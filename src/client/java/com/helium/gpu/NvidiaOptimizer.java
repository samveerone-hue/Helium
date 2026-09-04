package com.helium.gpu;

import com.helium.HeliumClient;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.KHRParallelShaderCompile;
import org.lwjgl.opengl.ARBParallelShaderCompile;

public final class NvidiaOptimizer {

    private static volatile boolean initialized = false;
    private static volatile boolean parallelShaderCompile = false;
    private static volatile String shaderCompileExtension = "none";

    private NvidiaOptimizer() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        if (!GpuDetector.isNvidia()) {
            HeliumClient.LOGGER.info("nvidia optimizer skipped - not an nvidia gpu");
            return;
        }

        try {
            var caps = GL.getCapabilities();
            if (caps.GL_KHR_parallel_shader_compile) {
                KHRParallelShaderCompile.glMaxShaderCompilerThreadsKHR(0xFFFFFFFF);
                parallelShaderCompile = true;
                shaderCompileExtension = "KHR";
                HeliumClient.LOGGER.info("nvidia: KHR parallel shader compile enabled");
            } else if (caps.GL_ARB_parallel_shader_compile) {
                ARBParallelShaderCompile.glMaxShaderCompilerThreadsARB(0xFFFFFFFF);
                parallelShaderCompile = true;
                shaderCompileExtension = "ARB";
                HeliumClient.LOGGER.info("nvidia: ARB parallel shader compile enabled");
            }
        } catch (Throwable t) {
            HeliumClient.LOGGER.warn("nvidia: parallel shader compile unavailable", t);
        }

        HeliumClient.LOGGER.info("nvidia optimizer initialized");
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean hasParallelShaderCompile() {
        return parallelShaderCompile;
    }

    public static String getShaderCompileExtension() {
        return shaderCompileExtension;
    }
}
