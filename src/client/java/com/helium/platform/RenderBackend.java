package com.helium.platform;

import com.helium.HeliumClient;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Detects Minecraft 26.2's active Blaze3D renderer without touching backend-specific
 * implementation classes. This is intentionally backend-neutral: the same Helium jar
 * runs with OpenGL or Vulkan, while OpenGL-only fast paths opt out when Vulkan is active.
 */
public final class RenderBackend {
    public enum Type {
        UNKNOWN,
        OPENGL,
        VULKAN,
        OTHER
    }

    private static volatile Type type = Type.UNKNOWN;
    private static volatile boolean logged;

    private RenderBackend() {}

    public static Type getType() {
        detect();
        return type;
    }

    public static boolean isKnown() {
        return type != Type.UNKNOWN;
    }

    public static boolean isOpenGL() {
        return getType() == Type.OPENGL;
    }

    public static boolean isVulkan() {
        return getType() == Type.VULKAN;
    }

    public static void detect() {
        if (type != Type.UNKNOWN) return;
        synchronized (RenderBackend.class) {
            if (type != Type.UNKNOWN) return;

            try {
                var device = RenderSystem.tryGetDevice();
                if (device == null) return;

                String backend = device.getDeviceInfo().backendName();
                Type detected;
                if ("Vulkan".equalsIgnoreCase(backend)) {
                    detected = Type.VULKAN;
                } else if ("OpenGL".equalsIgnoreCase(backend)) {
                    detected = Type.OPENGL;
                } else {
                    detected = Type.OTHER;
                }

                type = detected;

                if (!logged) {
                    logged = true;
                    HeliumClient.LOGGER.info("graphics backend detected: {} ({})", backend, detected);
                    if (detected == Type.VULKAN) {
                        HeliumClient.LOGGER.info("Vulkan renderer active; OpenGL-only Helium fast paths will be bypassed");
                    }
                }
            } catch (Throwable t) {
                // Backend detection must never prevent Minecraft from starting.
                HeliumClient.LOGGER.debug("graphics backend detection deferred: {}", t.toString());
            }
        }
    }
}
