package com.helium.compute;

import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL;
import org.lwjgl.opencl.CL10;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opencl.CL10.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public final class OpenClComputeBackend implements AutoCloseable {
    private static final int DEVICE_NAME = 0x102B;
    private static final int HOST_UNIFIED_MEMORY = 0x1035;
    private static final long CONTEXT_PLATFORM = 0x1084L;
    private static final int FLOW_INF = 1073741824;

    private static final String PROGRAM = """
        __kernel void helium_los(__global const float* rays, __global const uchar* solid,
                                 __global uchar* result, const int size,
                                 const int min_x, const int min_y, const int min_z) {
            const int id = get_global_id(0);
            const float ox=rays[id*6], oy=rays[id*6+1], oz=rays[id*6+2];
            const float tx=rays[id*6+3], ty=rays[id*6+4], tz=rays[id*6+5];
            const float dx=tx-ox, dy=ty-oy, dz=tz-oz;
            const float distance=sqrt(dx*dx+dy*dy+dz*dz);
            const int steps=max(1,(int)ceil(distance*2.0f));
            int visible=1;
            for (int s=1;s<steps;s++) {
                const float t=((float)s)/((float)steps);
                const int x=(int)floor(ox+dx*t)-min_x;
                const int y=(int)floor(oy+dy*t)-min_y;
                const int z=(int)floor(oz+dz*t)-min_z;
                if (x<0||y<0||z<0||x>=size||y>=size||z>=size) continue;
                if (solid[(z*size+y)*size+x]!=0) { visible=0; break; }
            }
            result[id]=(uchar)visible;
        }
        __kernel void helium_flow(__global const uchar* blocked, __global const int* current,
                                  __global int* next, const int size, const int target_index) {
            const int id=get_global_id(0);
            if (blocked[id]!=0) { next[id]=1073741824; return; }
            if (id==target_index) { next[id]=0; return; }
            const int plane=size*size, z=id/plane, rem=id-z*plane, y=rem/size, x=rem-y*size;
            int best=current[id];
            if (x>0) best=min(best,current[id-1]+1); if (x+1<size) best=min(best,current[id+1]+1);
            if (y>0) best=min(best,current[id-size]+1); if (y+1<size) best=min(best,current[id+size]+1);
            if (z>0) best=min(best,current[id-plane]+1); if (z+1<size) best=min(best,current[id+plane]+1);
            next[id]=best;
        }
        """;

    private final long context, queue, program, losKernel, flowKernel;
    private final boolean unifiedMemory;
    private final String deviceName;

    private OpenClComputeBackend(long context,long queue,long program,long losKernel,long flowKernel,boolean unifiedMemory,String deviceName) {
        this.context=context; this.queue=queue; this.program=program; this.losKernel=losKernel; this.flowKernel=flowKernel;
        this.unifiedMemory=unifiedMemory; this.deviceName=deviceName;
    }

    public static OpenClComputeBackend create() {
        try {
            CL.create();
            try (var stack=stackPush()) {
                IntBuffer pc=stack.callocInt(1);
                if (clGetPlatformIDs(null,pc)!=CL10.CL_SUCCESS || pc.get(0)==0) return null;
                PointerBuffer platforms=stack.mallocPointer(pc.get(0));
                if (clGetPlatformIDs(platforms,null)!=CL10.CL_SUCCESS) return null;
                long bestPlatform=0,bestDevice=0; int bestScore=Integer.MIN_VALUE; boolean unified=false; String name="OpenCL GPU";
                for (int p=0;p<platforms.capacity();p++) {
                    long platform=platforms.get(p); IntBuffer dc=stack.callocInt(1);
                    if (clGetDeviceIDs(platform,CL_DEVICE_TYPE_GPU,null,dc)!=CL10.CL_SUCCESS||dc.get(0)==0) continue;
                    PointerBuffer devices=stack.mallocPointer(dc.get(0));
                    if (clGetDeviceIDs(platform,CL_DEVICE_TYPE_GPU,devices,null)!=CL10.CL_SUCCESS) continue;
                    for(int d=0;d<devices.capacity();d++) {
                        long device=devices.get(d); boolean u=queryInt(device,HOST_UNIFIED_MEMORY)!=0; String n=queryString(device,DEVICE_NAME);
                        int score=(u?300:100); String q=n.toLowerCase(java.util.Locale.ROOT);
                        if(q.contains("intel")||q.contains("uhd")||q.contains("iris")) score+=50;
                        if(q.contains("radeon graphics")||q.contains("vega")||q.contains("apu")) score+=40;
                        if(q.contains("nvidia")||q.contains("geforce")||q.contains("radeon rx")) score-=30;
                        if(score>bestScore){bestScore=score;bestPlatform=platform;bestDevice=device;unified=u;name=n;}
                    }
                }
                if(bestDevice==0) return null;
                PointerBuffer props=stack.mallocPointer(3).put(CONTEXT_PLATFORM).put(bestPlatform).put(0L).flip();
                PointerBuffer devs=stack.mallocPointer(1).put(bestDevice).flip();
                var err=stack.callocInt(1);
                long ctx=clCreateContext(props,devs,null,0L,err); if(err.get(0)!=CL10.CL_SUCCESS||ctx==0)return null;
                long queue=clCreateCommandQueue(ctx,bestDevice,0L,err); if(err.get(0)!=CL10.CL_SUCCESS||queue==0){clReleaseContext(ctx);return null;}
                long prog=clCreateProgramWithSource(ctx,PROGRAM,err);
                if(err.get(0)!=CL10.CL_SUCCESS||prog==0||clBuildProgram(prog,bestDevice,"",null,0L)!=CL10.CL_SUCCESS){if(prog!=0)clReleaseProgram(prog);clReleaseCommandQueue(queue);clReleaseContext(ctx);return null;}
                long los=clCreateKernel(prog,"helium_los",err), flow=clCreateKernel(prog,"helium_flow",err);
                if(err.get(0)!=CL10.CL_SUCCESS||los==0||flow==0){if(los!=0)clReleaseKernel(los);if(flow!=0)clReleaseKernel(flow);clReleaseProgram(prog);clReleaseCommandQueue(queue);clReleaseContext(ctx);return null;}
                return new OpenClComputeBackend(ctx,queue,prog,los,flow,unified,name);
            }
        } catch(Throwable ignored){return null;}
    }

    private static int queryInt(long device,int parameter){try(var s=stackPush()){IntBuffer b=s.callocInt(1);return clGetDeviceInfo(device,parameter,b,null)==CL10.CL_SUCCESS?b.get(0):0;}}
    private static String queryString(long device,int parameter){try(var s=stackPush()){IntBuffer n=s.callocInt(1);if(clGetDeviceInfo(device,parameter,(ByteBuffer)null,n)!=CL10.CL_SUCCESS||n.get(0)<=1)return "OpenCL GPU";ByteBuffer b=s.malloc(n.get(0));if(clGetDeviceInfo(device,parameter,b,null)!=CL10.CL_SUCCESS)return "OpenCL GPU";byte[] a=new byte[b.remaining()];b.get(a);int l=a.length;while(l>0&&a[l-1]==0)l--;return new String(a,0,l,StandardCharsets.UTF_8);}}
    public String deviceName(){return deviceName;}
    public boolean usesUnifiedMemory(){return unifiedMemory;}

    public boolean[] runLineOfSight(float[] rays,byte[] solid,int size,int minX,int minY,int minZ){
        int count=rays.length/6;if(count==0)return new boolean[0];long rb=0,sb=0,out=0;
        try(var s=stackPush()){
            var e=s.callocInt(1);var r=s.mallocFloat(rays.length).put(rays).flip();var solids=s.malloc(solid.length).put(solid).flip();var result=s.malloc(count);
            rb=clCreateBuffer(context,CL_MEM_READ_ONLY|CL_MEM_COPY_HOST_PTR,r,e);sb=clCreateBuffer(context,CL_MEM_READ_ONLY|CL_MEM_COPY_HOST_PTR,solids,e);out=clCreateBuffer(context,CL_MEM_WRITE_ONLY,count,e);
            if(rb==0||sb==0||out==0)return null;
            clSetKernelArg1p(losKernel,0,rb);clSetKernelArg1p(losKernel,1,sb);clSetKernelArg1p(losKernel,2,out);clSetKernelArg1i(losKernel,3,size);clSetKernelArg1i(losKernel,4,minX);clSetKernelArg1i(losKernel,5,minY);clSetKernelArg1i(losKernel,6,minZ);
            PointerBuffer global=s.mallocPointer(1).put(count).flip();if(clEnqueueNDRangeKernel(queue,losKernel,1,null,global,null,null,null)!=CL10.CL_SUCCESS)return null;if(clFinish(queue)!=CL10.CL_SUCCESS)return null;if(clEnqueueReadBuffer(queue,out,true,0,result,null,null)!=CL10.CL_SUCCESS)return null;
            boolean[] values=new boolean[count];for(int i=0;i<count;i++)values[i]=result.get(i)!=0;return values;
        }finally{if(rb!=0)clReleaseMemObject(rb);if(sb!=0)clReleaseMemObject(sb);if(out!=0)clReleaseMemObject(out);}
    }

    public int[] runFlowField(byte[] blocked,int size,int tx,int ty,int tz){
        int voxels=size*size*size,target=(tz*size+ty)*size+tx;if(tx<0||ty<0||tz<0||tx>=size||ty>=size||tz>=size||blocked[target]!=0)return null;long bb=0,cb=0,nb=0;
        try(var s=stackPush()){
            var e=s.callocInt(1);var bd=s.malloc(blocked.length).put(blocked).flip();var cur=s.mallocInt(voxels);var nxt=s.mallocInt(voxels);for(int i=0;i<voxels;i++)cur.put(i,i==target?0:FLOW_INF);
            bb=clCreateBuffer(context,CL_MEM_READ_ONLY|CL_MEM_COPY_HOST_PTR,bd,e);cb=clCreateBuffer(context,CL_MEM_READ_WRITE|CL_MEM_COPY_HOST_PTR,cur,e);nb=clCreateBuffer(context,CL_MEM_READ_WRITE,(long)voxels*Integer.BYTES,e);if(bb==0||cb==0||nb==0)return null;
            PointerBuffer global=s.mallocPointer(1).put(voxels).flip();int iterations=Math.min(size*2+4,160);for(int i=0;i<iterations;i++){clSetKernelArg1p(flowKernel,0,bb);clSetKernelArg1p(flowKernel,1,cb);clSetKernelArg1p(flowKernel,2,nb);clSetKernelArg1i(flowKernel,3,size);clSetKernelArg1i(flowKernel,4,target);if(clEnqueueNDRangeKernel(queue,flowKernel,1,null,global,null,null,null)!=CL10.CL_SUCCESS)return null;long t=cb;cb=nb;nb=t;}if(clFinish(queue)!=CL10.CL_SUCCESS)return null;if(clEnqueueReadBuffer(queue,cb,true,0,cur,null,null)!=CL10.CL_SUCCESS)return null;int[] distances=new int[voxels];cur.rewind();cur.get(distances);return distances;
        }finally{if(bb!=0)clReleaseMemObject(bb);if(cb!=0)clReleaseMemObject(cb);if(nb!=0)clReleaseMemObject(nb);}
    }

    public void close(){if(losKernel!=0)clReleaseKernel(losKernel);if(flowKernel!=0)clReleaseKernel(flowKernel);if(program!=0)clReleaseProgram(program);if(queue!=0)clReleaseCommandQueue(queue);if(context!=0)clReleaseContext(context);}
}
