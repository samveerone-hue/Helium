package com.helium.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

import java.lang.reflect.Method;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Bounded compile-task gate for Minecraft 26.x.
 *
 * <p>Tasks remain the native section-task objects so Mojang's existing
 * priority model stays authoritative. Helium only limits how much work is
 * admitted to the dispatcher per frame. The dispatcher task type changed in
 * 26.2, so the adapter intentionally avoids naming that private nested type.</p>
 */
public final class AsyncChunkMeshing {
    private static final int MAX_PENDING = 16_384;
    private static final PriorityBlockingQueue<Object> PENDING = new PriorityBlockingQueue<>();
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    private AsyncChunkMeshing() {}

    public static boolean queue(Object task) {
        if (task == null) return false;
        if (PENDING.size() >= MAX_PENDING) return false;
        return PENDING.offer(task);
    }

    private static volatile Method SCHEDULE;

    public static int drainQueue(SectionRenderDispatcher dispatcher, int maxPerFrame) {
        if (dispatcher == null || maxPerFrame <= 0) return 0;
        int count = 0;
        BYPASS.set(true);
        try {
            while (count < Math.min(maxPerFrame, 64)) {
                Object task = PENDING.poll();
                if (task == null) break;
                try {
                    Method method = findScheduleMethod(task);
                    if (method == null) {
                        PENDING.offer(task);
                        break;
                    }
                    method.invoke(dispatcher, task);
                    count++;
                } catch (ReflectiveOperationException | RuntimeException e) {
                    PENDING.offer(task);
                    break;
                }
            }
        } finally {
            BYPASS.set(false);
        }
        return count;
    }

    private static Method findScheduleMethod(Object task) {
        Method cached = SCHEDULE;
        if (cached != null && cached.getParameterCount() == 1
                && cached.getParameterTypes()[0].isAssignableFrom(task.getClass())) {
            return cached;
        }

        for (Method method : SectionRenderDispatcher.class.getDeclaredMethods()) {
            if (!method.getName().equals("schedule") || method.getParameterCount() != 1) continue;
            Class<?> parameterType = method.getParameterTypes()[0];
            if (!parameterType.isAssignableFrom(task.getClass())) continue;
            method.setAccessible(true);
            SCHEDULE = method;
            return method;
        }
        return null;
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
        SCHEDULE = null;
    }
}
