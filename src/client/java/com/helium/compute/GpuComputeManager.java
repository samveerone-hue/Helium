package com.helium.compute;

import com.helium.HeliumClient;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GpuComputeManager {
    @FunctionalInterface public interface SolidSampler { boolean isSolid(int x,int y,int z); }
    private record Key(int source,int target) {}
    private record Request(Key key,float[] ray,long tick,SolidSampler sampler) {}
    private record Result(boolean visible,long tick) {}
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Helium-GPU-Compute");t.setDaemon(true);t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    private static final ConcurrentMap<Key,Request> pending=new ConcurrentHashMap<>();
    private static final ConcurrentMap<Key,Result> results=new ConcurrentHashMap<>();
    private static volatile GpuComputeConfig config;
    private static volatile OpenClComputeBackend backend;
    private static volatile long lastFlush=Long.MIN_VALUE;

    private GpuComputeManager(){}
    private static synchronized boolean enabled(){
        GpuComputeConfig c=config==null?(config=GpuComputeConfig.load()):config;
        boolean wanted=c.enabled&&(c.lineOfSight||c.pathfinding);
        if(!wanted){closeBackend();return false;}
        if(backend==null) backend=OpenClComputeBackend.create();
        return backend!=null;
    }
    private static void closeBackend(){OpenClComputeBackend b=backend;backend=null;if(b!=null){try{b.close();}catch(Throwable ignored){}}pending.clear();results.clear();}
    public static boolean lineOfSightEnabled(){return enabled()&&config.lineOfSight;}
    public static boolean pathfindingEnabled(){return enabled()&&config.pathfinding;}
    public static Boolean cached(int source,int target,long tick){Result r=results.get(new Key(source,target));if(r==null)return null;return tick-r.tick<=Math.max(1,config.refreshTicks)?r.visible:null;}
    public static void requestLineOfSight(int source,int target,float ox,float oy,float oz,float tx,float ty,float tz,long tick,SolidSampler sampler){
        if(!lineOfSightEnabled()||source==target)return;
        if(cached(source,target,tick)!=null)return;
        Key key=new Key(source,target);pending.putIfAbsent(key,new Request(key,new float[]{ox,oy,oz,tx,ty,tz},tick,sampler));
        flush(tick-1);
    }
    private static void flush(long tick){
        if(tick==Long.MIN_VALUE||lastFlush==tick)return;lastFlush=tick;
        ArrayList<Request> batch=new ArrayList<>();int max=Math.max(1,config.maxBatch);
        for(Request r:pending.values())if(r.tick==tick&&batch.size()<max&&pending.remove(r.key,r))batch.add(r);
        if(batch.isEmpty()||backend==null)return;
        Request anchor=batch.get(0);int size=Math.max(16,Math.min(48,config.gridSize));int half=size/2;int minX=(int)Math.floor(anchor.ray[0])-half,minY=(int)Math.floor(anchor.ray[1])-half,minZ=(int)Math.floor(anchor.ray[2])-half;
        byte[] solid=new byte[size*size*size];int i=0;
        try{for(int z=0;z<size;z++)for(int y=0;y<size;y++)for(int x=0;x<size;x++,i++)solid[i]=(byte)(anchor.sampler.isSolid(minX+x,minY+y,minZ+z)?1:0);}catch(Throwable t){HeliumClient.LOGGER.debug("gpu compute world snapshot failed",t);return;}
        float[] rays=new float[batch.size()*6];for(i=0;i<batch.size();i++)System.arraycopy(batch.get(i).ray,0,rays,i*6,6);
        OpenClComputeBackend b=backend;EXECUTOR.execute(()->{try{boolean[] values=b.runLineOfSight(rays,solid,size,minX,minY,minZ);if(values==null)return;for(int n=0;n<values.length;n++)results.put(batch.get(n).key,new Result(values[n],tick));}catch(Throwable t){HeliumClient.LOGGER.debug("gpu line-of-sight batch failed",t);}});
    }
    public static int[] runFlowField(byte[] blocked,int size,int targetX,int targetY,int targetZ){if(!pathfindingEnabled()||backend==null)return null;try{return backend.runFlowField(blocked,size,targetX,targetY,targetZ);}catch(Throwable t){HeliumClient.LOGGER.debug("gpu flow-field failed",t);return null;}}
}
