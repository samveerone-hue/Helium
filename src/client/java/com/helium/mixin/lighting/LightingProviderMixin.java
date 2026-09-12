package com.helium.mixin.lighting;

import com.helium.config.ExperimentalConfig;
import com.helium.lighting.AsyncLightEngine;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.light.LightingProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightingProvider.class)
public abstract class LightingProviderMixin {
    private static boolean helium$enabled() {
        try {
            return ExperimentalConfig.load().asyncLightUpdates;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Inject(method = "checkBlock", at = @At("HEAD"))
    private void helium$prepareBlockUpdate(BlockPos pos, CallbackInfo ci) {
        if (!helium$enabled()) return;
        try {
            ExperimentalConfig cfg = ExperimentalConfig.load();
            if (!AsyncLightEngine.isInitialized()) AsyncLightEngine.init(cfg.asyncLightMaxPerTick);
            AsyncLightEngine.queueBlock(pos.asLong());
        } catch (Throwable ignored) {
            // Never interfere with vanilla lighting if preparation fails.
        }
    }

    @Inject(method = "doLightUpdates", at = @At("HEAD"))
    private void helium$prepareBeforeVanillaLightPass(CallbackInfoReturnable<Integer> cir) {
        if (!helium$enabled()) return;
        try {
            if (!AsyncLightEngine.isInitialized()) {
                ExperimentalConfig cfg = ExperimentalConfig.load();
                AsyncLightEngine.init(cfg.asyncLightMaxPerTick);
            }
        } catch (Throwable ignored) {
        }
    }
}
