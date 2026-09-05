package com.helium.rentities.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.rentities.RendererCapabilityState;

/** Single authoritative renderer-path selector. Unsupported paths fail open to vanilla. */
public final class RendererBackendManager {
    public enum Backend { VANILLA, GPU_INSTANCED, GPU_INDIRECT }

    public Backend select() {
        HeliumConfig cfg = HeliumClient.getConfig();
        if (cfg == null || !cfg.entityGpuBatching) return Backend.VANILLA;

        RendererCapabilityState caps = RendererCapabilityState.current();
        if (caps == null || !caps.gpuBatchingAllowed(cfg)) return Backend.VANILLA;

        if (cfg.entityGpuFrustumCulling && caps.indirectAllowed(cfg)) {
            return Backend.GPU_INDIRECT;
        }
        return Backend.GPU_INSTANCED;
    }
}
