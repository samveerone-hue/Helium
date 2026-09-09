package com.helium.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Bounded compile-task gate for Minecraft 26.1.2.
 *
 * <p>Tasks stay as vanilla {@link SectionRenderDispatcher.RenderSection.CompileTask}
 * instances, so Mojang's existing distance/high-priority ordering remains authoritative.
 * Helium only limits how much work is admitted to the dispatcher each frame.</p>
 */
public final class AsyncChunkMeshing {
    private static final int MAX_PENDING = 16_384;
    private static final PriorityBlockingQueue<SectionRenderDispatcher.RenderSection.CompileTask> PENDING =
            new PriorityBlockingQueue<>();
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    private AsyncChunkMeshing() {}

    public static boolean queue(SectionRenderDispatcher.RenderSection.CompileTask task) {
        if (task == null) return false;
        if (PENDING.size() >= MAX_PENDING) return false;
        return PENDING.offer(task);
    }

    public static int drainQueue(SectionRenderDispatcher dispatcher, int maxPerFrame) {
        if (dispatcher == null || maxPerFrame <= 0) return 0;
        int count = 0;
        BYPASS.set(true);
        try {
            while (count < Math.min(maxPerFrame, 64)) {
                SectionRenderDispatcher.RenderSection.CompileTask task = PENDING.poll();
                if (task == null) break;
                dispatcher.schedule(task);
                count++;
            }
        } finally {
            BYPASS.set(false);
        }
        return count;
    }

    public static boolean isBypassing() {
        return BYPASS.get();
    }

    public static int size() {
        return PENDING.size();
    }

    public static int getDrainBudget(int configured) {
        int max = Math.max(1, Math.min(configured, 64));
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.adaptiveChunkScheduling || !RenderPipeline.isInitialized()) return max;

        int targetFps = 60;
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.options.framerateLimit().get() > 0) {
            targetFps = client.options.framerateLimit().get();
        }

        double targetMs = 1000.0 / Math.max(1, targetFps);
        double frameMs = RenderPipeline.getSmoothedFrameTimeMs();
        if (frameMs <= targetMs) return max;

        return Math.max(1, (int) Math.floor(max * Math.max(0.25, targetMs / frameMs)));
    }

    public static void clear() {
        PENDING.clear();
        BYPASS.set(false);
    }
}
