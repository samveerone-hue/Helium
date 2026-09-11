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
    private static final int DEVICE_NAME=0x102B, HOST_UNIFIED_MEMORY=0x1035, FLOW_INF=1073741824;
    private static final long CONTEXT_PLATFORM=0x1084L;
    private static final String PROGRAM="""
        __kernel void helium_los(__global const float* rays,__global const uchar* solid,__global uchar* result,const int size,const int min_x,const int min_y,const int min_z){const int id=get_global_id(0);const float ox=rays[id*6],oy=rays[id*6+1],oz=rays[id*6+2],tx=rays[id*6+3],ty=rays[id*6+4],tz=rays[id*6+5];const float dx=tx-ox,dy=ty-oy,dz=tz-oz;const int steps=max(1,(int)ceil(sqrt(dx*dx+dy*dy+dz*dz)*2.0f));int v=1;for(int s=1;s<steps;s++){float t=((float)s)/((float)steps);int x=(int)floor(ox+dx*t)-min_x,y=(int)floor(oy+dy*t)-min_y,z=(int)floor(oz+dz*t)-min_z;if(x<0||y<0||z<0||x>=size||y>=size||z>=size)continue;if(solid[(z*size+y)*size+x]!=0){v=0;break;}}result[id]=(uchar)v;}
        __kernel void helium_flow(__global const uchar* blocked,__global const int* current,__global int* next,const int size,const int target_index){const int id=get_global_id(0);if(blocked[id]!=0){next[id]=1073741824;return;}if(id==target_index){next[id]=0;return;}int plane=size*size,z=id/plane,rem=id-z*plane,y=rem/size,x=rem-y*size,b=current[id];if(x>0)b=min(b,current[id-1]+1);if(x+1<size)b=min(b,current[id+1]+1);if(y>0)b=min(b,current[id-size]+1);if(y+1<size)b=min(b,current[id+size]+1);if(z>0)b=min(b,current[id-plane]+1);if(z+1<size)b=min(b,current[id+plane]+1);next[id]=b;}
        """;
    private final long context,queue,program,losKernel,flowKernel;private final boolean unifiedMemory;private final String deviceName;
    private OpenClComputeBackend(long c,long q,long p,long l,long f,boolean u,String n){context=c;queue=q;program=p;losKernel=l;flowKernel=f;unifiedMemory=u;deviceName=n;}
    public static OpenClComputeBackend create(){try{CL.create();try(var s=stackPush()){IntBuffer pc=s.callocInt(1);if(clGetPlatformIDs((PointerBuffer)null,pc)!=CL10.CL_SUCCESS||pc.get(0)==0)return null;PointerBuffer ps=s.mallocPointer(pc.get(0));if(clGetPlatformIDs(ps,(IntBuffer)null)!=CL10.CL_SUCCESS)return null;long bp=0,bd=0;int bs=Integer.MIN_VALUE;boolean bu=false;String bn="OpenCL GPU";for(int p=0;p<ps.capacity();p++){long platform=ps.get(p);IntBuffer dc=s.callocInt(1);if(clGetDeviceIDs(platform,CL_DEVICE_TYPE_GPU,(PointerBuffer)null,dc)!=CL10.CL_SUCCESS||dc.get(0)==0)continue;PointerBuffer ds=s.mallocPointer(dc.get(0));if(clGetDeviceIDs(platform,CL_DEVICE_TYPE_GPU,ds,(IntBuffer)null)!=CL10.CL_SUCCESS)continue;for(int d=0;d<ds.capacity();d++){long device=ds.get(d);boolean u=queryInt(device,HOST_UNIFIED_MEMORY)!=0;String n=queryString(device,DEVICE_NAME);String x=n.toLowerCase(java.util.Locale.ROOT);int score=u?300:100;if(x.contains("intel")||x.contains("uhd")||x.contains("iris"))score+=50;if(x.contains("radeon graphics")||x.contains("vega")||x.contains("apu"))score+=40;if(x.contains("nvidia")||x.contains("geforce")||x.contains("radeon rx"))score-=30;if(score>bs){bs=score;bp=platform;bd=device;bu=u;bn=n;}}}if(bd==0)return null;PointerBuffer props=s.mallocPointer(3).put(CONTEXT_PLATFORM).put(bp).put(0L).flip();PointerBuffer devs=s.mallocPointer(1).put(bd).flip();var e=s.callocInt(1);long c=clCreateContext(props,devs,null,0L,e);if(e.get(0)!=CL10.CL_SUCCESS||c==0)return null;long q=clCreateCommandQueue(c,bd,0L,e);if(e.get(0)!=CL10.CL_SUCCESS||q==0){clReleaseContext(c);return null;}long prog=clCreateProgramWithSource(c,PROGRAM,e);if(e.get(0)!=CL10.CL_SUCCESS||prog==0||clBuildProgram(prog,bd,"",null,0L)!=CL10.CL_SUCCESS){if(prog!=0)clReleaseProgram(prog);clReleaseCommandQueue(q);clReleaseContext(c);return null;}long l=clCreateKernel(prog,"helium_los",e),f=clCreateKernel(prog,"helium_flow",e);if(e.get(0)!=CL10.CL_SUCCESS||l==0||f==0){if(l!=0)clReleaseKernel(l);if(f!=0)clReleaseKernel(f);clReleaseProgram(prog);clReleaseCommandQueue(q);clReleaseContext(c);return null;}return new OpenClComputeBackend(c,q,prog,l,f,bu,bn);}}catch(Throwable ignored){return null;}}
    private static int queryInt(long d,int p){try(var s=stackPush()){IntBuffer b=s.callocInt(1);return clGetDeviceInfo(d,p,b,(PointerBuffer)null)==CL10.CL_SUCCESS?b.get(0):0;}}
    private static String queryString(long d,int p){try(var s=stackPush()){PointerBuffer n=s.mallocPointer(1);if(clGetDeviceInfo(d,p,(ByteBuffer)null,n)!=CL10.CL_SUCCESS||n.get(0)<=1)return"OpenCL GPU";ByteBuffer b=s.malloc((int)n.get(0));if(clGetDeviceInfo(d,p,b,(PointerBuffer)null)!=CL10.CL_SUCCESS)return"OpenCL GPU";byte[] a=new byte[b.remaining()];b.get(a);int l=a.length;while(l>0&&a[l-1]==0)l--;return new String(a,0,l,StandardCharsets.UTF_8);}}
    public String deviceName(){return deviceName;} public boolean usesUnifiedMemory(){return unifiedMemory;}
    public boolean[] runLineOfSight(float[] r,byte[] solid,int size,int minX,int minY,int minZ){int count=r.length/6;if(count==0)return new boolean[0];long rb=0,sb=0,ob=0;try(var s=stackPush()){var e=s.callocInt(1);var rd=s.mallocFloat(r.length).put(r).flip();var sd=s.malloc(solid.length).put(solid).flip();var out=s.malloc(count);rb=clCreateBuffer(context,CL_MEM_READ_ONLY|CL_MEM_COPY_HOST_PTR,rd,e);sb=clCreateBuffer(context,CL_MEM_READ_ONLY|CL_MEM_COPY_HOST_PTR,sd,e);ob=clCreateBuffer(context,CL_MEM_WRITE_ONLY,count,e);if(rb==0||sb==0||ob==0)return null;clSetKernelArg1p(losKernel,0,rb);clSetKernelArg1p(losKernel,1,sb);clSetKernelArg1p(losKernel,2,ob);clSetKernelArg1i(losKernel,3,size);clSetKernelArg1i(losKernel,4,minX);clSetKernelArg1i(losKernel,5,minY);clSetKernelArg1i(losKernel,6,minZ);PointerBuffer g=s.mallocPointer(1).put(count).flip();if(clEnqueueNDRangeKernel(queue,losKernel,1,null,g,null,null,null)!=CL10.CL_SUCCESS||clFinish(queue)!=CL10.CL_SUCCESS||clEnqueueReadBuffer(queue,ob,true,0,out,null,null)!=CL10.CL_SUCCESS)return null;boolean[] a=new boolean[count];for(int i=0;i<count;i++)a[i]=out.get(i)!=0;return a;}finally{if(rb!=0)clReleaseMemObject(rb);if(sb!=0)clReleaseMemObject(sb);if(ob!=0)clReleaseMemObject(ob);}}
    public int[] runFlowField(byte[] blocked,int size,int tx,int ty,int tz){int voxels=size*size*size,target=(tz*size+ty)*size+tx;if(tx<0||ty<0||tz<0||tx>=size||ty>=size||tz>=size||blocked[target]!=0)return null;long bb=0,cb=0,nb=0;try(var s=stackPush()){var e=s.callocInt(1);var bd=s.malloc(blocked.length).put(blocked).flip();var cur=s.mallocInt(voxels);for(int i=0;i<voxels;i++)cur.put(i,i==target?0:FLOW_INF);bb=clCreateBuffer(context,CL_MEM_READ_ONLY|CL_MEM_COPY_HOST_PTR,bd,e);cb=clCreateBuffer(context,CL_MEM_READ_WRITE|CL_MEM_COPY_HOST_PTR,cur,e);nb=clCreateBuffer(context,CL_MEM_READ_WRITE,(long)voxels*Integer.BYTES,e);if(bb==0||cb==0||nb==0)return null;PointerBuffer g=s.mallocPointer(1).put(voxels).flip();for(int i=0;i<Math.min(size*2+4,160);i++){clSetKernelArg1p(flowKernel,0,bb);clSetKernelArg1p(flowKernel,1,cb);clSetKernelArg1p(flowKernel,2,nb);clSetKernelArg1i(flowKernel,3,size);clSetKernelArg1i(flowKernel,4,target);if(clEnqueueNDRangeKernel(queue,flowKernel,1,null,g,null,null,null)!=CL10.CL_SUCCESS)return null;long t=cb;cb=nb;nb=t;}if(clFinish(queue)!=CL10.CL_SUCCESS)return null;var out=s.mallocInt(voxels);if(clEnqueueReadBuffer(queue,cb,true,0,out,null,null)!=CL10.CL_SUCCESS)return null;int[] d=new int[voxels];out.get(d);return d;}finally{if(bb!=0)clReleaseMemObject(bb);if(cb!=0)clReleaseMemObject(cb);if(nb!=0)clReleaseMemObject(nb);}}
    public void close(){if(losKernel!=0)clReleaseKernel(losKernel);if(flowKernel!=0)clReleaseKernel(flowKernel);if(program!=0)clReleaseProgram(program);if(queue!=0)clReleaseCommandQueue(queue);if(context!=0)clReleaseContext(context);}
}
