package com.helium.tweaks;

import com.helium.HeliumClient;
import com.helium.dedup.DeduplicationManager;
import com.helium.render.ShaderUniformCache;
import com.helium.render.TextRenderOptimizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ResourceLoadStateTracker;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ReloadInstance;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AsyncPackReloader {
    private static final Executor RELOAD_EXECUTOR=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"helium-pack-reload");t.setDaemon(true);return t;});
    private static final AtomicBoolean _loading=new AtomicBoolean(false);
    private static volatile boolean _needsrerender;
    private AsyncPackReloader(){}
    public static boolean isloading(){return _loading.get();}
    public static void reloadasync(){
        if(_loading.getAndSet(true))return; Minecraft client=Minecraft.getInstance(); if(client==null){_loading.set(false);return;}
        try{
            HeliumClient.LOGGER.info("async pack reload started"); if(DeduplicationManager.isenabled())DeduplicationManager.clearcaches();
            client.getResourcePackRepository().reload(); List<PackResources> packs=client.resourcePackRepository.openAllSelected();
            ResourceLoadStateTracker.ReloadReason reason=getreloadreason(); if(reason!=null)client.reloadStateTracker.startReload(reason,packs);
            ReloadInstance reload=client.resourceManager.createReload(RELOAD_EXECUTOR,client,Minecraft.RESOURCE_RELOAD_INITIAL_TASK,packs);
            reload.done().thenRun(()->{_needsrerender=true;try{client.reloadStateTracker.finishReload();}catch(Throwable ignored){}try{client.downloadedPackSource.onReloadSuccess();}catch(Throwable ignored){}ShaderUniformCache.invalidate();TextRenderOptimizer.invalidate();HeliumClient.LOGGER.info("async pack reload finished");});
        }catch(Throwable t){HeliumClient.LOGGER.error("async pack reload failed",t);_loading.set(false);}
    }
    public static void tick(){
        if(!_needsrerender)return; _needsrerender=false; Minecraft client=Minecraft.getInstance();
        if(client!=null&&client.levelRenderer!=null&&client.level!=null){try{client.levelRenderer.invalidateCompiledGeometry(client.level,client.options,client.gameRenderer.mainCamera(),client.getBlockColors());}catch(Throwable ignored){}}
        _loading.set(false);
    }
    private static ResourceLoadStateTracker.ReloadReason getreloadreason(){return ResourceLoadStateTracker.ReloadReason.MANUAL;}
}
