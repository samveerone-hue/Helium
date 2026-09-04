package com.helium.tweaks;

import com.helium.HeliumClient;
import com.helium.dedup.DeduplicationManager;
import com.helium.render.ShaderUniformCache;
import com.helium.render.TextRenderOptimizer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.ResourceReloadLogger;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AsyncPackReloader {

    private static volatile ExecutorService RELOAD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
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

    public static synchronized void shutdown() {
        _loading.set(false);
        _needsrerender = false;
        ExecutorService service = RELOAD_EXECUTOR;
        if (service != null) {
            service.shutdownNow();
        }
    }

    private static synchronized ExecutorService reloadExecutor() {
        if (RELOAD_EXECUTOR == null || RELOAD_EXECUTOR.isShutdown()) {
            RELOAD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "helium-pack-reload");
                t.setDaemon(true);
                return t;
            });
        }
        return RELOAD_EXECUTOR;
    }

    public static void reloadasync() {
        if (_loading.getAndSet(true)) return;

        ExecutorService reloadExecutor = reloadExecutor();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            _loading.set(false);
            return;
        }

        try {
            HeliumClient.LOGGER.info("async pack reload started");
            
            if (DeduplicationManager.isenabled()) {
                DeduplicationManager.clearcaches();
            }

            client.getResourcePackManager().scanPacks();
            List<ResourcePack> packs = client.resourcePackManager.createResourcePacks();

            ResourceReloadLogger.ReloadReason reason = getreloadreason();
            if (reason != null) {
                client.resourceReloadLogger.reload(reason, packs);
            }

            ResourceReload reload = client.resourceManager.reload(
                    reloadExecutor,
                    client,
                    MinecraftClient.COMPLETED_UNIT_FUTURE,
                    packs
            );

            reload.whenComplete().thenRun(() -> client.execute(() -> {
                _needsrerender = true;

                try {
                    client.resourceReloadLogger.finish();
                } catch (Throwable ignored) {}

                try {
                    client.serverResourcePackLoader.onReloadSuccess();
                } catch (Throwable ignored) {}

                ShaderUniformCache.invalidate();
                TextRenderOptimizer.invalidate();
                _loading.set(false);
                HeliumClient.LOGGER.info("async pack reload finished");
            }));
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) return;

        int renderdist = client.options.getViewDistance().getValue() * 2;
        ChunkPos center = client.player.getChunkPos();
        int worldheight = client.world.getHeight();
        int ysections = ChunkSectionPos.getSectionCoord(worldheight);

        for (int dx = 0; dx < renderdist; dx++) {
            for (int dz = 0; dz < renderdist; dz++) {
                int cx = center.x + dx - renderdist / 2;
                int cz = center.z + dz - renderdist / 2;
                for (int cy = 0; cy < ysections; cy++) {
                    client.worldRenderer.scheduleChunkRender(cx, cy, cz);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static ResourceReloadLogger.ReloadReason getreloadreason() {
        try {
            return ResourceReloadLogger.ReloadReason.UNKNOWN;
        } catch (Throwable t1) {
            try {
                Class<?> clazz = Class.forName("net.minecraft.client.resource.ResourceReloadLogger$ReloadReason");
                Object[] constants = clazz.getEnumConstants();
                if (constants != null && constants.length > 0) {
                    return (ResourceReloadLogger.ReloadReason) constants[0];
                }
            } catch (Throwable ignored) {}
            return null;
        }
    }
}
