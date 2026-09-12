package com.helium.mixin.tick;

import com.helium.HeliumClient;
import com.helium.compat.CrossLoaderCompat;
import com.helium.config.ExperimentalConfig;
import com.helium.config.HeliumConfig;
import com.helium.lighting.AsyncLightEngine;
import com.helium.math.SimdMath;
import com.helium.render.AsyncChunkMeshing;
import com.helium.render.ModelCache;
import com.helium.startup.FastStartup;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class ClientTickMixin {
    private boolean helium$experimentalStarted;

    @Inject(method = "tick", at = @At("TAIL"))
    private void helium$onTickEnd(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        HeliumConfig config = HeliumClient.getConfig();
        ExperimentalConfig experimental = ExperimentalConfig.load();

        if (!helium$experimentalStarted) {
            helium$experimentalStarted = true;
            try {
                if (experimental.fastStartup) FastStartup.prepare();
                if (experimental.simdMath) SimdMath.init();
                if (experimental.modelCache && !ModelCache.isInitialized()) ModelCache.init(experimental.modelCacheMaxMb);
                if (experimental.asyncLightUpdates && !AsyncLightEngine.isInitialized()) AsyncLightEngine.init(experimental.asyncLightMaxPerTick);
            } catch (Throwable t) {
                HeliumClient.LOGGER.debug("experimental initialization partially failed: {}", t.toString());
            }
        }

        if (experimental.fastStartup) FastStartup.pollCompleted();
        if (experimental.asyncLightUpdates) AsyncLightEngine.drainPrepared();

        if (config != null && config.modEnabled && config.renderPipelining && client.worldRenderer != null) {
            AsyncChunkMeshing.drainQueue(client.worldRenderer, config.chunkScheduleMaxPerTick);
        }

        if (!CrossLoaderCompat.isfabrictickavailable()) {
            CrossLoaderCompat.tick();
        }
    }
}
