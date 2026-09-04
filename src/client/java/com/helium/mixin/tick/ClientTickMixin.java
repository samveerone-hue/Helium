package com.helium.mixin.tick;

import com.helium.HeliumClient;
import com.helium.compat.CrossLoaderCompat;
import com.helium.config.HeliumConfig;
import com.helium.render.AsyncChunkMeshing;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class ClientTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void helium$onTickEnd(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        HeliumConfig config = HeliumClient.getConfig();

        if (config != null && config.modEnabled && config.renderPipelining && client.worldRenderer != null) {
            AsyncChunkMeshing.drainQueue(client.worldRenderer, config.chunkScheduleMaxPerTick);
        }

        if (!CrossLoaderCompat.isfabrictickavailable()) {
            CrossLoaderCompat.tick();
        }
    }
}
