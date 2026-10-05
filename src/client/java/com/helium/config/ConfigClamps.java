package com.helium.config;

/** Pure configuration bounds used by the runtime config classes and runtime-free tests. */
public final class ConfigClamps {
    private ConfigClamps() {}

    public static int entityCullDistance(int value) { return clamp(value, 16, 128); }
    public static int blockEntityCullDistance(int value) { return clamp(value, 16, 96); }
    public static int particleCullDistance(int value) { return clamp(value, 8, 64); }
    public static int maxParticles(int value) { return clamp(value, 100, 5000); }
    public static int overlayTransparency(int value) { return clamp(value, 0, 100); }
    public static int nativeMemoryPoolMb(int value) { return clamp(value, 16, 256); }
    public static int chunkScheduleMaxPerTick(int value) { return clamp(value, 1, 64); }
    public static int idleTimeoutSeconds(int value) { return clamp(value, 10, 300); }
    public static int idleFpsLimit(int value) { return clamp(value, 1, 30); }
    public static int fullbrightStrength(int value) { return clamp(value, 0, 10); }
    public static int leafCullingDepth(int value) { return clamp(value, 1, 4); }
    public static float leafCullingRandomRejection(float value) {
        return clamp(value, 0.0f, 1.0f);
    }
    public static double particleLODDistance(double value) {
        return clamp(value, 4.0, 64.0);
    }
    public static double particleLODReduction(double value) {
        return clamp(value, 0.0, 1.0);
    }
    public static int itemFrameLODRange(int value) { return clamp(value, 32, 256); }
    public static int rentitiesAsyncVisibilityRefreshFrames(int value) { return clamp(value, 1, 30); }
    public static int rentitiesAsyncVisibilityMaxAgeFrames(int value) { return clamp(value, 1, 60); }
    public static double rentitiesAsyncVisibilityMaxDistance(double value) {
        return clamp(value, 0.0, 256.0);
    }
    public static int inactiveFpsLimit(int value) { return clamp(value, 1, 60); }
    public static int inactiveRenderDistance(int value) { return clamp(value, 2, 32); }

    public static int gpuGridSize(int value) { return clamp(value, 16, 48); }
    public static int gpuRefreshTicks(int value) { return clamp(value, 1, 10); }
    public static int gpuMaxBatch(int value) { return clamp(value, 1, 8); }

    public static int experimentalPacketBatchTicks(int value) { return clamp(value, 1, 2); }
    public static int experimentalModelCacheMaxMb(int value) { return clamp(value, 16, 512); }
    public static int experimentalAsyncLightMaxPerTick(int value) { return clamp(value, 8, 256); }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
