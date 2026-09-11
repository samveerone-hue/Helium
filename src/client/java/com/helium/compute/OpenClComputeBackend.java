package com.helium.compute;

import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL;
import org.lwjgl.opencl.CL10;

import java.nio.IntBuffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opencl.CL10.CL_DEVICE_TYPE_GPU;
import static org.lwjgl.opencl.CL10.clBuildProgram;
import static org.lwjgl.opencl.CL10.clCreateBuffer;
import static org.lwjgl.opencl.CL10.clCreateCommandQueue;
import static org.lwjgl.opencl.CL10.clCreateContext;
import static org.lwjgl.opencl.CL10.clCreateKernel;
import static org.lwjgl.opencl.CL10.clCreateProgramWithSource;
import static org.lwjgl.opencl.CL10.clEnqueueNDRangeKernel;
import static org.lwjgl.opencl.CL10.clEnqueueReadBuffer;
import static org.lwjgl.opencl.CL10.clFinish;
import static org.lwjgl.opencl.CL10.clGetDeviceIDs;
import static org.lwjgl.opencl.CL10.clGetDeviceInfo;
import static org.lwjgl.opencl.CL10.clGetPlatformIDs;
import static org.lwjgl.opencl.CL10.clReleaseCommandQueue;
import static org.lwjgl.opencl.CL10.clReleaseContext;
import static org.lwjgl.opencl.CL10.clReleaseKernel;
import static org.lwjgl.opencl.CL10.clReleaseMemObject;
import static org.lwjgl.opencl.CL10.clReleaseProgram;
import static org.lwjgl.opencl.CL10.clSetKernelArg1i;
import static org.lwjgl.opencl.CL10.clSetKernelArg1p;
import static org.lwjgl.system.MemoryStack.stackPush;

/** Optional OpenCL backend. Helium owns the kernels; no Laminar implementation is bundled. */
public final class OpenClComputeBackend implements AutoCloseable {
    private static final int CL_DEVICE_NAME = 0x102B;
    private static final int CL_DEVICE_HOST_UNIFIED_MEMORY = 0x1035;
    private static final long CL_CONTEXT_PLATFORM = 0x1084L;
    private static final int FLOW_INF = 1073741824;

    private static final String PROGRAM_SOURCE = """
            __kernel void helium_los(__global const float* rays,__global const uchar* solid,__global uchar* result,const int size,const int min_x,const int min_y,const int min_z){
                const int id=get_global_id(0); const float ox=rays[id*6],oy=rays[id*6+1],oz=rays[id*6+2],tx=rays[id*6+3],ty=rays[id*6+4],tz=rays[id*6+5];
                const float dx=tx-ox,dy=ty-oy,dz=tz-oz; const int steps=max(1,(int)ceil(sqrt(dx*dx+dy*dy+dz*dz)*2.0f)); int v=1;
                for(int s=1;s<steps;s++){float t=((float)s)/((float)steps);int x=(int)floor(ox+dx*t)-min_x,y=(int)floor(oy+dy*t)-min_y,z=(int)floor(oz+dz*t)-min_z;if(x<0||y<0||z<0||x>=size||y>=size||z>=size)continue;if(solid[(z*size+y)*size+x]!=0){v=0;break;}} result[id]=(uchar)v;
            }
            __kernel void helium_flow(__global const uchar* blocked,__global const int* current,__global int* next,const int size,const int target_index){
                const int id=get_global_id(0);if(blocked[id]!=0){next[id]=1073741824;return;}if(id==target_index){next[id]=0;return;}int plane=size*size,z=id/plane,rem=id-z*plane,y=rem/size,x=rem-y*size,b=current[id];
                if(x>0)b=min(b,current[id-1]+1);if(x+1<size)b=min(b,current[id+1]+1);if(y>0)b=min(b,current[id-size]+1);if(y+1<size)b=min(b,current[id+size]+1);if(z>0)b=min(b,current[id-plane]+1);if(z+1<size)b=min(b,current[id+plane]+1);next[id]=b;
            }
            """;

    private final long platform,device,context,queue,program,losKernel,flowKernel;
    private final boolean unifiedMemory;
    private final String deviceName;

    private OpenClComputeBackend(long platform,long device,long context,long queue,long program,long losKernel,long flowKernel,boolean unifiedMemory,String deviceName){
        this.platform=platform;this.device=device;this.context=context;this.queue=queue;this.program=program;this.losKernel=losKernel;this.flowKernel=flowKernel;this.unifiedMemory=unifiedMemory;this.deviceName=deviceName;
    }

    public static OpenClComputeBackend create(){
        try{CL.create();try(var s=stackPush()){
            IntBuffer platformCount=s.callocInt(1);if(clGetPlatformIDs((PointerBuffer)null,platformCount)!=CL10.CL_SUCCESS||platformCount.get(0)==0)return null;
            PointerBuffer platforms=s.mallocPointer(platformCount.get(0));if(clGetPlatformIDs(platforms,(IntBuffer)null)!=CL10.CL_SUCCESS)return null;
            long bestPlatform=0L,bestDevice=0L;int bestScore=Integer.MIN_VALUE;boolean bestUnified=false;String bestName="OpenCL GPU";
            for(int p=0;p<platforms.capacity();p++){
                long platform=platforms.get(p);IntBuffer deviceCount=s.callocInt(1);if(clGetDeviceIDs(platform,CL_DEVICE_TYPE_GPU,(PointerBuffer)null,deviceCount)!=CL10.CL_SUCCESS||deviceCount.get(0)==0)continue;
                PointerBuffer devices=s.mallocPointer(deviceCount.get(0));if(clGetDeviceIDs(platform,CL_DEVICE_TYPE_GPU,devices,(IntBuffer)null)!=CL10.CL_SUCCESS)continue;
                for(int d=0;d<devices.capacity();d++){long device=devices.get(d);boolean unified=queryInt(device,CL_DEVICE_HOST_UNIFIED_MEMORY)!=0;String name=queryString(device,CL_DEVICE_NAME);int score=score(name,unified);if(score>bestScore){bestScore=score;bestPlatform=platform;bestDevice=device;bestUnified=unified;bestName=name;}}
            }
            if(bestDevice==0L)return null;PointerBuffer properties=s.mallocPointer(3).put(CL_CONTEXT_PLATFORM).put(bestPlatform).put(0L).flip();PointerBuffer devices=s.mallocPointer(1).put(bestDevice).flip();var error=s.callocInt(1);
            long context=clCreateContext(properties,devices,null,0L,error);if(error.get(0)!=CL10.CL_SUCCESS||context==0L)return null;long queue=clCreateCommandQueue(context,bestDevice,0L,error);if(error.get(0)!=CL10.CL_SUCCESS||queue==0L){clReleaseContext(context);return null;}
            long program=clCreateProgramWithSource(context,PROGRAM_SOURCE,error);if(error.get(0)!=CL10.CL_SUCCESS||program==0L||clBuildProgram(program,bestDevice,"",null,0L)!=CL10.CL_SUCCESS){if(program!=0L)clReleaseProgram(program);clReleaseCommandQueue(queue);clReleaseContext(context);return null;}
            long losKernel=clCreateKernel(program,"helium_los",error),flowKernel=clCreateKernel(program,"helium_flow",error);if(error.get(0)!=CL10.CL_SUCCESS||losKernel==0L||flowKernel==0L){if(losKernel!=0L)clReleaseKernel(losKernel);if(flowKernel!=0L)clReleaseKernel(flowKernel);clReleaseProgram(program);clReleaseCommandQueue(queue);clReleaseContext(context);return null;}
            return new OpenClComputeBackend(bestPlatform,bestDevice,context,queue,program,losKernel,flowKernel,bestUnified,bestName);
        }}catch(Throwable ignored){return null;}
    }

    private static int score(String name,boolean unified){String normalized=name==null?"":name.toLowerCase(java.util.Locale.ROOT);int score=unified?300:100;if(normalized.contains("intel")||normalized.contains("uhd")||normalized.contains("iris"))score+=50;if(normalized.contains("radeon graphics")||normalized.contains("vega")||normalized.contains("apu"))score+=40;if(normalized.contains("nvidia")||normalized.contains("geforce")||normalized.contains("radeon rx"))score-=30;return score;}
    private static int queryInt(long device,int parameter){try(var s=stackPush()){IntBuffer value=s.callocInt(1);return clGetDeviceInfo(device,parameter,value,(PointerBuffer)null)==CL10.CL_SUCCESS?value.get(0):0;}}
    private static String queryString(long device,int parameter){try(var s=stackPush()){PointerBuffer size=s.mallocPointer(1);if(clGetDeviceInfo(device,parameter,(ByteBuffer)null,size)!=CL10.CL_SUCCESS||size.get(0)<=1)return"OpenCL GPU";ByteBuffer value=s.malloc((int)size.get(0));if(clGetDeviceInfo(device,parameter,value,(PointerBuffer)null)!=CL10.CL_SUCCESS)return"OpenCL GPU";byte[] bytes=new byte[value.remaining()];value.get(bytes);int length=bytes.length;while(length>0&&bytes[length-1]==0)length--;return new String(bytes,0,length,StandardCharsets.UTF_8);}}
    public String deviceName(){return deviceName;} public boolean usesUnifiedMemory(){return unifiedMemory;}

    public boolean[] runLineOfSight(float[] rays,byte[] solid,int size,int minX,int minY,int minZ){int count=rays.length/6;if(count==0)return new boolean[0];long rb=0,sb=0,out=0;try(var s=stackPush()){var e=s.callocInt(1);var rd=s.mallocFloat(rays.length).put(rays).flip();var sd=s.malloc(solid.length).put(solid).flip();var result=s.malloc(count);rb=clCreateBuffer(context,CL10.CL_MEM_READ_ONLY|CL10.CL_MEM_COPY_HOST_PTR,rd,e);sb=clCreateBuffer(context,CL10.CL_MEM_READ_ONLY|CL10.CL_MEM_COPY_HOST_PTR,sd,e);out=clCreateBuffer(context,CL10.CL_MEM_WRITE_ONLY,count,e);if(rb==0||sb==0||out==0)return null;clSetKernelArg1p(losKernel,0,rb);clSetKernelArg1p(losKernel,1,sb);clSetKernelArg1p(losKernel,2,out);clSetKernelArg1i(losKernel,3,size);clSetKernelArg1i(losKernel,4,minX);clSetKernelArg1i(losKernel,5,minY);clSetKernelArg1i(losKernel,6,minZ);PointerBuffer global=s.mallocPointer(1).put(count).flip();if(clEnqueueNDRangeKernel(queue,losKernel,1,null,global,null,null,null)!=CL10.CL_SUCCESS||clFinish(queue)!=CL10.CL_SUCCESS||clEnqueueReadBuffer(queue,out,true,0,result,null,null)!=CL10.CL_SUCCESS)return null;boolean[] values=new boolean[count];for(int i=0;i<count;i++)values[i]=result.get(i)!=0;return values;}finally{if(rb!=0)clReleaseMemObject(rb);if(sb!=0)clReleaseMemObject(sb);if(out!=0)clReleaseMemObject(out);}}

    public int[] runFlowField(byte[] blocked,int size,int tx,int ty,int tz){int voxels=size*size*size,target=(tz*size+ty)*size+tx;if(tx<0||ty<0||tz<0||tx>=size||ty>=size||tz>=size||blocked[target]!=0)return null;long bb=0,cb=0,nb=0;try(var s=stackPush()){var e=s.callocInt(1);var bd=s.malloc(blocked.length).put(blocked).flip();var cur=s.mallocInt(voxels);for(int i=0;i<voxels;i++)cur.put(i,i==target?0:FLOW_INF);bb=clCreateBuffer(context,CL10.CL_MEM_READ_ONLY|CL10.CL_MEM_COPY_HOST_PTR,bd,e);cb=clCreateBuffer(context,CL10.CL_MEM_READ_WRITE|CL10.CL_MEM_COPY_HOST_PTR,cur,e);nb=clCreateBuffer(context,CL10.CL_MEM_READ_WRITE,(long)voxels*Integer.BYTES,e);if(bb==0||cb==0||nb==0)return null;PointerBuffer global=s.mallocPointer(1).put(voxels).flip();for(int i=0;i<Math.min(size*2+4,160);i++){clSetKernelArg1p(flowKernel,0,bb);clSetKernelArg1p(flowKernel,1,cb);clSetKernelArg1p(flowKernel,2,nb);clSetKernelArg1i(flowKernel,3,size);clSetKernelArg1i(flowKernel,4,target);if(clEnqueueNDRangeKernel(queue,flowKernel,1,null,global,null,null,null)!=CL10.CL_SUCCESS)return null;long temp=cb;cb=nb;nb=temp;}if(clFinish(queue)!=CL10.CL_SUCCESS)return null;var out=s.mallocInt(voxels);if(clEnqueueReadBuffer(queue,cb,true,0,out,null,null)!=CL10.CL_SUCCESS)return null;int[] distances=new int[voxels];out.get(distances);return distances;}finally{if(bb!=0)clReleaseMemObject(bb);if(cb!=0)clReleaseMemObject(cb);if(nb!=0)clReleaseMemObject(nb);}}

    public void close(){if(losKernel!=0)clReleaseKernel(losKernel);if(flowKernel!=0)clReleaseKernel(flowKernel);if(program!=0)clReleaseProgram(program);if(queue!=0)clReleaseCommandQueue(queue);if(context!=0)clReleaseContext(context);}
}
