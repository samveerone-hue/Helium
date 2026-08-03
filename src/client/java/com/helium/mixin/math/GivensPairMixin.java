package com.helium.mixin.math;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.math.GBFMath;
import com.mojang.math.GivensParameters;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GivensParameters.class)
public abstract class GivensPairMixin {

    @Unique
    private static final GivensParameters helium$IDENTITY = new GivensParameters(0.0F, 1.0F);

    @Inject(method = "fromUnnormalized", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastNormalize(float a, float b, CallbackInfoReturnable<GivensParameters> cir) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config != null && config.fastMath) {
            float f = GBFMath.lengthsquared(a, b);
            if (f < GBFMath.NORMALIZE_EPSILON) {
                cir.setReturnValue(helium$IDENTITY);
                return;
            }
            float inv = GBFMath.invsqrt(f);
            cir.setReturnValue(new GivensParameters(a * inv, b * inv));
        }
    }

    @Inject(method = "fromPositiveAngle", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastFromAngle(float radians, CallbackInfoReturnable<GivensParameters> cir) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config != null && config.fastMath) {
            float f = 0.5F * radians;
            float sin = Mth.sin(f);
            float cos = GBFMath.cosfromsin(sin, f);
            cir.setReturnValue(new GivensParameters(sin, cos));
        }
    }
}
