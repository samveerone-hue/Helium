package com.helium.rentities.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;

/** Single authoritative renderer-path selector. Unsupported paths fail open to vanilla. */
public final class RendererBackendManager {
    public enum Backend { VANILLA, GPU_INSTANCED, GPU_INDIRECT }

    public Backend select() {
        HeliumConfig cfg = HeliumClient.getConfig();
        if (cfg == null || !cfg.entityGpuBatching) return Backend.VANILLA;
        if (cfg.entityGpuBatching == HeliumConfig.RendererBackendMode.VANILLA ||
            cfg.entityGpuBatching == HeliumConfig.RendererBackendMode.CPU) return Backend.VANILLA;
        boolean gpu = null == null || null.gpuBatchingAllowed(cfg);
        if (!gpu) return Backend.VANILLA;
        if (cfg.entityGpuBatching == HeliumConfig.RendererBackendMode.INDIRECT) {
            boolean indirect = null != null &&
                    null.indirectAllowed(cfg) && cfg.gpu_frustum_culling_enabled;
            return indirect ? Backend.GPU_INDIRECT : Backend.GPU_INSTANCED;
        }
        if (cfg.entityGpuBatching == HeliumConfig.RendererBackendMode.GPU ||
            cfg.entityGpuBatching == HeliumConfig.RendererBackendMode.AUTO) return Backend.GPU_INSTANCED;
        return Backend.VANILLA;
    }
}
