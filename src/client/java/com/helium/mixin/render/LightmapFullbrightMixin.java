package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.feature.FullbrightManager;
import net.minecraft.client.renderer.Lightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Lightmap.class)
public abstract class LightmapFullbrightMixin {

    @Unique
    private static boolean helium$fbFailed = false;

    //? if >=1.21.1 {
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1),
            require = 0
    )
    private float helium$fullbrightGamma(Double instance) {
        if (helium$fbFailed) return instance.floatValue();
        try {
            if (FullbrightManager.isEnabled()) {
                return FullbrightManager.getEffectiveGamma();
            }
            return instance.floatValue();
        } catch (Throwable t) {
            helium$fbFailed = true;
            HeliumClient.LOGGER.warn("fullbright mixin disabled ({})", t.getClass().getSimpleName());
            return instance.floatValue();
        }
    }
    //?} else {
    /*
    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0),
            require = 0
    )
    private float helium$fullbrightGamma(Double instance) {
        if (helium$fbFailed) return instance.floatValue();
        try {
            if (FullbrightManager.isEnabled()) {
                return FullbrightManager.getEffectiveGamma();
            }
            return instance.floatValue();
        } catch (Throwable t) {
            helium$fbFailed = true;
            HeliumClient.LOGGER.warn("fullbright mixin disabled ({})", t.getClass().getSimpleName());
            return instance.floatValue();
        }
    }
    */
    //?}
}
