package com.helium.mixin.tick;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.lighting.AsyncLightEngine;
import com.helium.memory.MemoryCompactor;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {

    @Unique
    private long helium$tickCounter = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void helium$tickMaintenance(CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.modEnabled) {
            return;
        }

        long time = helium$tickCounter++;

        // ClientTickCache.tick() is intentionally absent: its current implementation
        // performs no work, so keeping a per-world-tick call only adds overhead.

        if (config.memoryOptimizations) {
            MemoryCompactor.tick(time);
        }

        if (config.asyncLightUpdates && AsyncLightEngine.isInitialized()) {
            AsyncLightEngine.applyCompleted();
        }
    }
}
