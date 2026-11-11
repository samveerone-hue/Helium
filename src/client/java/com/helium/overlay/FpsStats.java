package com.helium.overlay;

public final class FpsStats {

    private static final long RESET_INTERVAL_MS = 500;
    private static final long DISPLAY_UPDATE_INTERVAL_MS = 500;

    private final Object lock = new Object();
    private int currentFps = 0;
    private int displayFps = 0;
    private int minFps = Integer.MAX_VALUE;
    private int maxFps = 0;
    private long fpsSum = 0;
    private int fpsCount = 0;
    private long lastResetTime = System.currentTimeMillis();
    private long lastDisplayUpdateTime = System.currentTimeMillis();
    private long lastFrameNanos;
    private double frameIntervalMs;

    public void updateFps(int fps) {
        synchronized (lock) {
            this.currentFps = fps;
            long frameNow = System.nanoTime();
            if (lastFrameNanos != 0L) frameIntervalMs = (frameNow - lastFrameNanos) / 1_000_000.0;
            lastFrameNanos = frameNow;

            long now = System.currentTimeMillis();
            if (now - lastDisplayUpdateTime >= DISPLAY_UPDATE_INTERVAL_MS) {
                this.displayFps = fps;
                lastDisplayUpdateTime = now;
            }

            if (fps > 0) {
                if (fps < minFps) minFps = fps;
                if (fps > maxFps) maxFps = fps;
                fpsSum += fps;
                fpsCount++;
            }

            if (now - lastResetTime >= RESET_INTERVAL_MS) {
                minFps = fps;
                maxFps = fps;
                fpsSum = fps;
                fpsCount = 1;
                lastResetTime = now;
            }
        }
    }

    public int getCurrentFps() {
        synchronized (lock) { return displayFps; }
    }

    public int getMinFps() {
        synchronized (lock) { return minFps == Integer.MAX_VALUE ? 0 : minFps; }
    }

    public int getMaxFps() {
        synchronized (lock) { return maxFps; }
    }

    public int getAvgFps() {
        synchronized (lock) { return fpsCount > 0 ? (int) (fpsSum / fpsCount) : 0; }
    }

    public double getFrameIntervalMs() {
        synchronized (lock) { return frameIntervalMs; }
    }

    public void reset() {
        synchronized (lock) {
            currentFps = 0;
            displayFps = 0;
            minFps = Integer.MAX_VALUE;
            maxFps = 0;
            fpsSum = 0;
            fpsCount = 0;
                        lastFrameNanos = 0L;
                        frameIntervalMs = 0.0;
            lastResetTime = System.currentTimeMillis();
        }
    }
}
