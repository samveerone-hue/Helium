package com.helium.render;

import com.helium.HeliumClient;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class RenderPipeline {

    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicLong frameCount = new AtomicLong(0);
    private static final AtomicLong lastFrameTime = new AtomicLong(0);
    private static final AtomicLong frameBudgetNs = new AtomicLong(16_666_667L);

    private static volatile long[] frameTimes = new long[60];
    private static volatile int frameIndex = 0;
    private static volatile int frameSampleCount = 0;
    private static volatile long frameTimeSumNs = 0;
    private static volatile double smoothedFrameTime = 16.67;
    private static volatile boolean adaptivePacing = true;

    private RenderPipeline() {}

    public static void init() {
        if (initialized.getAndSet(true)) return;
        lastFrameTime.set(System.nanoTime());
        HeliumClient.LOGGER.info("render pipeline initialized (frame budget tracking mode)");
    }

    public static boolean isInitialized() {
        return initialized.get();
    }

    public static void onFrameStart() {
        if (!initialized.get()) return;

        long now = System.nanoTime();
        long last = lastFrameTime.getAndSet(now);
        long delta = now - last;

        if (delta > 0 && delta < 1_000_000_000L) {
            long old = frameTimes[frameIndex];
            if (old > 0) {
                frameTimeSumNs -= old;
            } else if (frameSampleCount < frameTimes.length) {
                frameSampleCount++;
            }

            frameTimes[frameIndex] = delta;
            frameTimeSumNs += delta;
            frameIndex = (frameIndex + 1) % frameTimes.length;

            if (frameSampleCount > 0) {
                smoothedFrameTime = (double) frameTimeSumNs / frameSampleCount / 1_000_000.0;
            }
        }

        frameCount.incrementAndGet();
    }

    /**
     * Records the end of a frame without blocking the render thread. Minecraft/driver frame
     * limiting owns present pacing; Helium only exposes the measured budget to schedulers.
     */
    public static void onFrameEnd() {
        if (!initialized.get() || !adaptivePacing) return;
        long elapsed = System.nanoTime() - lastFrameTime.get();
        if (elapsed < 0) return;
        // Intentionally no sleep: sleeping here can add input latency and fight Minecraft's
        // own frame limiter. The frame budget remains available through getFrameBudgetMs().
    }

    public static void setTargetFps(int fps) {
        if (fps <= 0 || fps >= 260) {
            frameBudgetNs.set(0L);
            return;
        }
        if (fps <= 1000) {
            frameBudgetNs.set(1_000_000_000L / fps);
        }
    }

    public static void setAdaptivePacing(boolean enabled) {
        adaptivePacing = enabled;
    }

    public static double getFrameBudgetMs() {
        return frameBudgetNs.get() / 1_000_000.0;
    }

    public static double getSmoothedFrameTimeMs() {
        return smoothedFrameTime;
    }

    public static double getSmoothedFps() {
        return smoothedFrameTime > 0 ? 1000.0 / smoothedFrameTime : 0;
    }

    public static long getFrameCount() {
        return frameCount.get();
    }

    public static void shutdown() {
        initialized.set(false);
        frameCount.set(0);
        lastFrameTime.set(0);
        frameTimes = new long[60];
        frameIndex = 0;
        frameSampleCount = 0;
        frameTimeSumNs = 0;
        smoothedFrameTime = 16.67;
        HeliumClient.LOGGER.info("render pipeline shutdown");
    }
}
