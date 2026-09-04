package com.helium.gpu;

import com.helium.HeliumClient;
import org.lwjgl.glfw.GLFW;

public final class AdaptiveSyncManager {

    private static volatile boolean initialized = false;
    private static volatile boolean adaptiveSyncDetected = false;
    private static volatile int refreshRate = 60;

    private AdaptiveSyncManager() {}

    public static void init(long windowHandle) {
        if (initialized) return;
        initialized = true;

        try {
            long monitor = GLFW.glfwGetPrimaryMonitor();
            if (monitor != 0) {
                var vidMode = GLFW.glfwGetVideoMode(monitor);
                if (vidMode != null) {
                    refreshRate = vidMode.refreshRate();
                }
            }

            adaptiveSyncDetected = detectAdaptiveSync();

            HeliumClient.LOGGER.info("adaptive sync capability not asserted ({}hz refresh)", refreshRate);
        } catch (Throwable t) {
            HeliumClient.LOGGER.warn("adaptive sync detection failed", t);
        }
    }

    private static boolean detectAdaptiveSync() {
        /*
         * Refresh rate alone does not prove that a monitor is running in a VRR mode.
         * GLFW does not expose a portable "current VRR state" query, so do not turn a
         * high-Hz monitor into a false-positive adaptive-sync capability.
         *
         * The manager currently does not own the swap-interval lifecycle either, so
         * claiming VRR here would only alter FPS targeting without actually enabling it.
         */
        return false;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isAdaptiveSyncDetected() {
        return adaptiveSyncDetected;
    }

    public static int getRefreshRate() {
        return refreshRate;
    }

    public static int getTargetFps() {
        if (adaptiveSyncDetected) {
            return refreshRate - 3;
        }
        return refreshRate;
    }
}
