package com.helium.compute;

import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL;
import org.lwjgl.opencl.CL10;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import static org.lwjgl.opencl.CL10.CL_DEVICE_TYPE_GPU;
import static org.lwjgl.opencl.CL10.CL_MEM_COPY_HOST_PTR;
import static org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY;
import static org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE;
import static org.lwjgl.opencl.CL10.CL_MEM_WRITE_ONLY;
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

public final class OpenClComputeBackend implements AutoCloseable {
    private static final int DEVICE_NAME = 0x102B;
    private static final int HOST_UNIFIED_MEMORY = 0x1035;
    private static final long CONTEXT_PLATFORM = 0x1084L;
    private static final int FLOW_INF = 1073741824;

    private static final String PROGRAM = """
        __kernel void helium_los(__global const float* rays,__global const uchar* solid,__global uchar* result,const int size,const int min_x,const int min_y,const int min_z){
            const int id=get_global_id(0); const float ox=rays[id*6],oy=rays[id*6+1],oz=rays[id*6+2],tx=rays[id*6+3],ty=rays[id*6+4],tz=rays[id*6+5];
            const float dx=tx-ox,dy=ty-oy,dz=tz-oz; const int steps=max(1,(int)ceil(sqrt(dx*dx+dy*dy+dz*dz)*2.0f)); int v=1;
            for(int s=1;s<steps;s++){float t=((float)s)/((float)steps);int x=(int)floor(ox+dx*t)-min_x,y=(int)floor(oy+dy*t)-min_y,z=(int)floor(oz+dz*t)-min_z;
                if(x<0||y<0||z<0||x>=size||y>=size||z>=size)continue; if(solid[(z*size+y)*size+x]!=0){v=0;break;}}
            result[id]=(uchar)v;
        }
        __kernel void helium_flow(__global const uchar* blocked,__global const int* current,__global int* next,const int size,const int target_index){
            const int id=get_global_id(0); if(blocked[id]!=0){next[id]=1073741824;return;} if(id==target_index){next[id]=0;return;}
            int plane=size*size,z=id/plane,rem=id-z*plane,y=rem/size,x=rem-y*size,b=current[id];
            if(x>0)b=min(b,current[id-1]+1); if(x+1<size)b=min(b,current[id+1]+1); if(y>0)b=min(b,current[id-size]+1); if(y+1<size)b=min(b,current[id+size]+1);
            if(z>0)b=min(b,current[id-plane]+1); if(z+1<size)b=min(b,current[id+plane]+1); next[id]=b;
        }
        """;

    private final long context, queue, program, losKernel, flowKernel;
    private final boolean unifiedMemory;
    private final String deviceName;

    private OpenClComputeBackend(long context,long queue,long program,long losKernel,long flowKernel,boolean unifiedMemory,String deviceName){
        this.context=context;this.queue=queue;this.program=program;this.losKernel=losKernel;this.flowKernel=flowKernel;this.unifiedMemory=unifiedMemory;this.deviceName=deviceName;
    }

    public static OpenClComputeBackend create(){
        try {
            CL.create();
            try(var s=stackPush()){
                IntBuffer pc=s.callocInt(1);
                if(clGetPlatformIDs(null,pc)!=CL10.CL_SUCCESS||pc.get(0)==0)return null;
                PointerBuffer platforms=s.mallocPointer(pc.get(0));
                if(clGetPlatformIDs(platforms,null)!=CL10.CL_SUCCESS)return null;
                long bestPlatform=0,bestDevice=0;int bestScore=Integer.MIN_VALUE;boolean bestUnified=false;String bestName="OpenCL GPU";
                for(int p=0;p<platforms.capacity();p++){
                    long platform=platforms.get(p);IntBuffer dc=s.callocInt(1);
                    if(clGetDeviceIDs(platform,CL_DEVICE_TYPE_GPU,null,dc)!=CL10.CL_SUCCESS||dc.get(0)==0)continue;
                    PointerBuffer devices=s.mallocPointer(dc.get(0));
                    if(clGetDeviceIDs(platform,CL_DEVICE_TYPE_GPU,devices,null)!=CL10.CL_SUCCESS)continue;
                    for(int d=0;d<devices.capacity();d++){
                        long device=devices.get(d);boolean unified=queryInt(device,HOST_UNIFIED_MEMORY)!=0;String name=queryString(device,DEVICE_NAME);String q=name.toLowerCase(java.util.Locale.ROOT);
                        int score=unified?300:100;if(q.contains("intel")||q.contains("uhd")||q.contains("iris"))score+=50;if(q.contains("radeon graphics")||q.contains("vega")||q.contains("apu"))score+=40;if(q.contains("nvidia")||q.contains("geforce")||q.contains("radeon rx"))score-=30;
                        if(score>bestScore){bestScore=score;bestPlatform=platform;bestDevice=device;bestUnified=unified;bestName=name;}
                    }
                }
                if(bestDevice==0)return null;
                PointerBuffer props=s.mallocPointer(3).put(CONTEXT_PLATFORM).put(bestPlatform).put(0L).flip();
                PointerBuffer devices=s.mallocPointer(1).put(bestDevice).flip();var e=s.callocInt(1);
                long c=clCreateContext(props,devices,null,0L,e);if(e.get(0)!=CL10.CL_SUCCESS||c==0)return null;
                long q=clCreateCommandQueue(c,bestDevice,0L,e);if(e.get(0)!=CL10.CL_SUCCESS||q==0){clReleaseContext(c);return null;}
                long prog=clCreateProgramWithSource(c,PROGRAM,e);if(e.get(0)!=CL10.CL_SUCCESS||prog==0||clBuildProgram(prog,bestDevice,"",null,0L)!=CL10.CL_SUCCESS){if(prog!=0)clReleaseProgram(prog);clReleaseCommandQueue(q);clReleaseContext(c);return null;}
                long los=clCreateKernel(prog,"helium_los",e),flow=clCreateKernel(prog,"helium_flow",e);if(e.get(0)!=CL10.CL_SUCCESS||los==0||flow==0){if(los!=0)clReleaseKernel(los);if(flow!=0)clReleaseKernel(flow);clReleaseProgram(prog);clReleaseCommandQueue(q);clReleaseContext(c);return null;}
                return new OpenClComputeBackend(c,q,prog,los,flow,bestUnified,bestName);
            }
        }catch(Throwable ignored){return null;}
    }

    private static int queryInt(long device,int parameter){try(var s=stackPush()){IntBuffer value=s.callocInt(1);return clGetDeviceInfo(device,parameter,value,null)==CL10.CL_SUCCESS?value.get(0):0;}}
    private static String queryString(long device,int parameter){try(var s=stackPush()){PointerBuffer size=s.mallocPointer(1);if(clGetDeviceInfo(device,parameter,(ByteBuffer)null,size)!=CL10.CL_SUCCESS||size.get(0)<=1)return "OpenCL GPU";ByteBuffer value=s.malloc((int)size.get(0));if(clGetDeviceInfo(device,parameter,value,null)!=CL10.CL_SUCCESS)return "OpenCL GPU";byte[] bytes=new byte[value.remaining()];value.get(bytes);int length=bytes.length;while(length>0&&bytes[length-1]==0)length--;return new String(bytes,0,length,StandardCharsets.UTF_8);}}

    public String deviceName(){return deviceName;}
    public boolean usesUnifiedMemory(){return unifiedMemory;}

    public boolean[] runLineOfSight(float[] rays,byte[] solid,int size,int minX,int minY,int minZ){
        int count=rays.length/6;if(count==0)return new boolean[0];long rb=0,sb=0,ob=0;
        try(var s=stackPush()){
            var e=s.callocInt(1);var rd=s.mallocFloat(rays.length).put(rays).flip();var sd=s.malloc(solid.length).put(solid).flip();var out=s.malloc(count);
            rb=clCreateBuffer(context,CL_MEM_READ_ONLY|CL_MEM_COPY_HOST_PTR,rd,e);sb=clCreateBuffer(context,CL_MEM_READ_ONLY|CL_MEM_COPY_HOST_PTR,sd,e);ob=clCreateBuffer(context,CL_MEM_WRITE_ONLY,count,e);if(rb==0||sb==0||ob==0)return null;
            clSetKernelArg1p(losKernel,0,rb);clSetKernelArg1p(losKernel,1,sb);clSetKernelArg1p(losKernel,2,ob);clSetKernelArg1i(losKernel,3,size);clSetKernelArg1i(losKernel,4,minX);clSetKernelArg1i(losKernel,5,minY);clSetKernelArg1i(losKernel,6,minZ);
            PointerBuffer global=s.mallocPointer(1).put(count).flip();if(clEnqueueNDRangeKernel(queue,losKernel,1,null,global,null,null,null)!=CL10.CL_SUCCESS||clFinish(queue)!=CL10.CL_SUCCESS||clEnqueueReadBuffer(queue,ob,true,0,out,null,null)!=CL10.CL_SUCCESS)return null;
            boolean[] values=new boolean[count];for(int i=0;i<count;i++)values[i]=out.get(i)!=0;return values;
        }finally{if(rb!=0)clReleaseMemObject(rb);if(sb!=0)clReleaseMemObject(sb);if(ob!=0)clReleaseMemObject(ob);}
    }

    public int[] runFlowField(byte[] blocked,int size,int tx,int ty,int tz){
        int voxels=size*size*size,target=(tz*size+ty)*size+tx;if(tx<0||ty<0||tz<0||tx>=size||ty>=size||tz>=size||blocked[target]!=0)return null;long bb=0,cb=0,nb=0;
        try(var s=stackPush()){
            var e=s.callocInt(1);var bd=s.malloc(blocked.length).put(blocked).flip();var cur=s.mallocInt(voxels);for(int i=0;i<voxels;i++)cur.put(i,i==target?0:FLOW_INF);
            bb=clCreateBuffer(context,CL_MEM_READ_ONLY|CL_MEM_COPY_HOST_PTR,bd,e);cb=clCreateBuffer(context,CL_MEM_READ_WRITE|CL_MEM_COPY_HOST_PTR,cur,e);nb=clCreateBuffer(context,CL_MEM_READ_WRITE,(long)voxels*Integer.BYTES,e);if(bb==0||cb==0||nb==0)return null;
            PointerBuffer global=s.mallocPointer(1).put(voxels).flip();for(int i=0;i<Math.min(size*2+4,160);i++){clSetKernelArg1p(flowKernel,0,bb);clSetKernelArg1p(flowKernel,1,cb);clSetKernelArg1p(flowKernel,2,nb);clSetKernelArg1i(flowKernel,3,size);clSetKernelArg1i(flowKernel,4,target);if(clEnqueueNDRangeKernel(queue,flowKernel,1,null,global,null,null,null)!=CL10.CL_SUCCESS)return null;long temp=cb;cb=nb;nb=temp;}
            if(clFinish(queue)!=CL10.CL_SUCCESS)return null;var out=s.mallocInt(voxels);if(clEnqueueReadBuffer(queue,cb,true,0,out,null,null)!=CL10.CL_SUCCESS)return null;int[] distances=new int[voxels];out.get(distances);return distances;
        }finally{if(bb!=0)clReleaseMemObject(bb);if(cb!=0)clReleaseMemObject(cb);if(nb!=0)clReleaseMemObject(nb);}
    }

    public void close(){if(losKernel!=0)clReleaseKernel(losKernel);if(flowKernel!=0)clReleaseKernel(flowKernel);if(program!=0)clReleaseProgram(program);if(queue!=0)clReleaseCommandQueue(queue);if(context!=0)clReleaseContext(context);}
}
