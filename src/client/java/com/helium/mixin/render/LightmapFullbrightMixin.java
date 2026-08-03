package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.feature.FullbrightManager;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Lightmap.class)
public abstract class LightmapFullbrightMixin {

    @Unique
    private static boolean helium$fbFailed = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void helium$fullbrightGamma(LightmapRenderState state, CallbackInfo ci) {
        if (helium$fbFailed) return;
        try {
            if (FullbrightManager.isEnabled()) {
                state.brightness = FullbrightManager.getEffectiveGamma();
                state.needsUpdate = true;
            }
        } catch (Throwable t) {
            helium$fbFailed = true;
            HeliumClient.LOGGER.warn("fullbright mixin disabled ({})", t.getClass().getSimpleName());
        }
    }
}
