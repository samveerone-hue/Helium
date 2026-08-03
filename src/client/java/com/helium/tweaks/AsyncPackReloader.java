package com.helium.tweaks;

import com.helium.HeliumClient;
import com.helium.dedup.DeduplicationManager;
import com.helium.render.ShaderUniformCache;
import com.helium.render.TextRenderOptimizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ResourceLoadStateTracker;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.SectionPos;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AsyncPackReloader {

    private static final Executor RELOAD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "helium-pack-reload");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicBoolean _loading = new AtomicBoolean(false);
    private static volatile boolean _needsrerender = false;

    private AsyncPackReloader() {}

    public static boolean isloading() {
        return _loading.get();
    }

    public static void reloadasync() {
        if (_loading.getAndSet(true)) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            _loading.set(false);
            return;
        }

        try {
            HeliumClient.LOGGER.info("async pack reload started");
            
            if (DeduplicationManager.isenabled()) {
                DeduplicationManager.clearcaches();
            }

            client.getResourcePackRepository().reload();
            List<PackResources> packs = client.resourcePackRepository.openAllSelected();

            ResourceLoadStateTracker.ReloadReason reason = getreloadreason();
            if (reason != null) {
                client.reloadStateTracker.startReload(reason, packs);
            }

            ReloadInstance reload = client.resourceManager.createReload(
                    RELOAD_EXECUTOR,
                    client,
                    Minecraft.RESOURCE_RELOAD_INITIAL_TASK,
                    packs
            );

            reload.done().thenRun(() -> {
                _needsrerender = true;

                try {
                    client.reloadStateTracker.finishReload();
                } catch (Throwable ignored) {}

                try {
                    client.downloadedPackSource.onReloadSuccess();
                } catch (Throwable ignored) {}

                ShaderUniformCache.invalidate();
                TextRenderOptimizer.invalidate();
                HeliumClient.LOGGER.info("async pack reload finished");
            });
        } catch (Throwable t) {
            HeliumClient.LOGGER.error("async pack reload failed", t);
            _loading.set(false);
        }
    }

    public static void tick() {
        if (!_needsrerender) return;
        _needsrerender = false;

        try {
            rerenderchunks();
        } catch (Throwable ignored) {}

        _loading.set(false);
    }

    private static void rerenderchunks() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) return;

        int renderdist = client.options.renderDistance().get() * 2;
        ChunkPos center = client.player.chunkPosition();
        int worldheight = client.level.getHeight();
        int ysections = SectionPos.blockToSectionCoord(worldheight);

        for (int dx = 0; dx < renderdist; dx++) {
            for (int dz = 0; dz < renderdist; dz++) {
                int cx = center.x() + dx - renderdist / 2;
                int cz = center.z() + dz - renderdist / 2;
                for (int cy = 0; cy < ysections; cy++) {
                    client.levelRenderer.setSectionDirty(cx, cy, cz);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static ResourceLoadStateTracker.ReloadReason getreloadreason() {
        try {
            return ResourceLoadStateTracker.ReloadReason.UNKNOWN;
        } catch (Throwable t1) {
            try {
                Class<?> clazz = Class.forName("net.minecraft.client.ResourceLoadStateTracker.ReloadReason");
                Object[] constants = clazz.getEnumConstants();
                if (constants != null && constants.length > 0) {
                    return (ResourceLoadStateTracker.ReloadReason) constants[0];
                }
            } catch (Throwable ignored) {}
            return null;
        }
    }
}
