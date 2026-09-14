package com.helium.config;

/** Runtime configuration normalization shared by startup and config reload paths. */
public final class ConfigSanitizer {
    private ConfigSanitizer() {}

    public static void sanitize(HeliumConfig config) {
        if (config == null) return;
        config.entityCullDistance = ConfigClamps.entityCullDistance(config.entityCullDistance);
        config.blockEntityCullDistance = ConfigClamps.blockEntityCullDistance(config.blockEntityCullDistance);
        config.particleCullDistance = ConfigClamps.particleCullDistance(config.particleCullDistance);
        config.maxParticles = ConfigClamps.maxParticles(config.maxParticles);
        config.overlayTransparency = ConfigClamps.overlayTransparency(config.overlayTransparency);
        config.nativeMemoryPoolMb = ConfigClamps.nativeMemoryPoolMb(config.nativeMemoryPoolMb);
        config.chunkScheduleMaxPerTick = ConfigClamps.chunkScheduleMaxPerTick(config.chunkScheduleMaxPerTick);
        config.idleTimeoutSeconds = ConfigClamps.idleTimeoutSeconds(config.idleTimeoutSeconds);
        config.idleFpsLimit = ConfigClamps.idleFpsLimit(config.idleFpsLimit);
        config.fullbrightStrength = ConfigClamps.fullbrightStrength(config.fullbrightStrength);
        config.leafCullingDepth = ConfigClamps.leafCullingDepth(config.leafCullingDepth);
        config.leafCullingRandomRejection = ConfigClamps.leafCullingRandomRejection(config.leafCullingRandomRejection);
        config.particleLODDistance = ConfigClamps.particleLODDistance(config.particleLODDistance);
        config.particleLODReduction = ConfigClamps.particleLODReduction(config.particleLODReduction);
        config.itemFrameLODRange = ConfigClamps.itemFrameLODRange(config.itemFrameLODRange);
        config.inactiveFpsLimit = ConfigClamps.inactiveFpsLimit(config.inactiveFpsLimit);
        config.inactiveRenderDistance = ConfigClamps.inactiveRenderDistance(config.inactiveRenderDistance);
    }
}