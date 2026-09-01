package com.helium.mixin.math;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.math.FastMath;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MathHelper.class)
public abstract class MathHelperMixin {

    private static boolean enabled() {
        HeliumConfig config = HeliumClient.getConfig();
        return config != null && config.fastMath && FastMath.isInitialized();
    }

    @Inject(method = "atan2(DD)D", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastAtan2(double y, double x, CallbackInfoReturnable<Double> cir) {
        if (enabled()) cir.setReturnValue(FastMath.atan2(y, x));
    }

    @Inject(method = "fastInverseSqrt(D)D", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastInvSqrt(double value, CallbackInfoReturnable<Double> cir) {
        if (enabled()) cir.setReturnValue(FastMath.inverseSqrt(value));
    }

    @Inject(method = "floorMod(II)I", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastFloorModInt(int dividend, int divisor, CallbackInfoReturnable<Integer> cir) {
        if (enabled() && divisor != 0) cir.setReturnValue(FastMath.positiveModulo(dividend, divisor));
    }

    @Inject(method = "floorMod(FF)F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastFloorModFloat(float dividend, float divisor, CallbackInfoReturnable<Float> cir) {
        if (enabled() && divisor != 0.0F) cir.setReturnValue(FastMath.positiveModulo(dividend, divisor));
    }

    @Inject(method = "floorMod(DD)D", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastFloorModDouble(double dividend, double divisor, CallbackInfoReturnable<Double> cir) {
        if (enabled() && divisor != 0.0D) cir.setReturnValue(FastMath.positiveModulo(dividend, divisor));
    }

    @Inject(method = "floor", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastFloorFloat(float value, CallbackInfoReturnable<Integer> cir) {
        if (enabled() && value >= Integer.MIN_VALUE && value < (double) Integer.MAX_VALUE + 1.0D) {
            cir.setReturnValue(FastMath.floor(value));
        }
    }

    @Inject(method = "floor", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastFloorDouble(double value, CallbackInfoReturnable<Integer> cir) {
        if (enabled() && value >= Integer.MIN_VALUE && value < (double) Integer.MAX_VALUE + 1.0D) {
            cir.setReturnValue(FastMath.floor(value));
        }
    }

    @Inject(method = "ceil", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastCeilFloat(float value, CallbackInfoReturnable<Integer> cir) {
        if (enabled() && value >= Integer.MIN_VALUE - 1.0D && value <= Integer.MAX_VALUE) {
            cir.setReturnValue(FastMath.ceil(value));
        }
    }

    @Inject(method = "ceil", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastCeilDouble(double value, CallbackInfoReturnable<Integer> cir) {
        if (enabled() && value >= Integer.MIN_VALUE - 1.0D && value <= Integer.MAX_VALUE) {
            cir.setReturnValue(FastMath.ceil(value));
        }
    }

    @Inject(method = "clamp(III)I", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastClampInt(int value, int min, int max, CallbackInfoReturnable<Integer> cir) {
        if (enabled()) cir.setReturnValue(FastMath.clamp(value, min, max));
    }

    @Inject(method = "clamp(FFF)F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastClampFloat(float value, float min, float max, CallbackInfoReturnable<Float> cir) {
        if (enabled()) cir.setReturnValue(FastMath.clamp(value, min, max));
    }

    @Inject(method = "clamp(DDD)D", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastClampDouble(double value, double min, double max, CallbackInfoReturnable<Double> cir) {
        if (enabled()) cir.setReturnValue(FastMath.clamp(value, min, max));
    }

    @Inject(method = "ceilLog2", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastCeilLog2(int value, CallbackInfoReturnable<Integer> cir) {
        if (enabled()) cir.setReturnValue(FastMath.ceilLog2(value));
    }

    @Inject(method = "floorLog2", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastFloorLog2(int value, CallbackInfoReturnable<Integer> cir) {
        if (enabled()) cir.setReturnValue(FastMath.floorLog2(value));
    }
}
