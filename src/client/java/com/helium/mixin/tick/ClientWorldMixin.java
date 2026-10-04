package com.helium.mixin.tick;

import com.helium.HeliumClient;
import com.helium.compute.GpuComputeManager;
import com.helium.config.HeliumConfig;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Unique private long helium$tickCounter = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void helium$tickMaintenance(CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.modEnabled) return;

        if (helium$tickCounter++ == 0) GpuComputeManager.clearWorldState();
    }
}
