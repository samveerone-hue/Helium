package com.helium.particle;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;

/**
 * Render-thread particle LOD state shared by the ParticleManager and Particle mixins.
 * Kept outside mixin classes so Mixin never has to expose a non-private helper method
 * on a Minecraft target class.
 */
public final class ParticleLODState {
    private static volatile double cameraX;
    private static volatile double cameraY;
    private static volatile double cameraZ;
    private static volatile boolean cameraReady;

    private ParticleLODState() {}

    public static void prepare(HeliumConfig config) {
        if (config == null || !config.particleLOD) {
            cameraReady = false;
            return;
        }

        try {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            var camera = client.gameRenderer == null ? null : client.gameRenderer.getCamera();
            if (camera == null) {
                cameraReady = false;
                return;
            }

            var pos = com.helium.util.VersionCompat.getCameraPosition(camera);
            cameraX = pos.x;
            cameraY = pos.y;
            cameraZ = pos.z;
            cameraReady = true;
        } catch (Throwable t) {
            cameraReady = false;
            HeliumClient.LOGGER.debug("particle LOD camera state unavailable", t);
        }
    }

    public static boolean isEnabled() {
        HeliumConfig config = HeliumClient.getConfig();
        return config != null && config.modEnabled && config.particleLOD && cameraReady;
    }

    public static double cameraX() { return cameraX; }
    public static double cameraY() { return cameraY; }
    public static double cameraZ() { return cameraZ; }
}
