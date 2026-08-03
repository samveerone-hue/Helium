package com.helium.mixin.math;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.math.FastMath;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mth.class)
public abstract class MathHelperMixin {

    @Inject(method = "atan2(DD)D", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastAtan2(double y, double x, CallbackInfoReturnable<Double> cir) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config != null && FastMath.isInitialized() && config.fastMath) {
            cir.setReturnValue(FastMath.atan2(y, x));
        }
    }

    @Inject(method = "fastInverseSqrt(D)D", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastInvSqrt(double value, CallbackInfoReturnable<Double> cir) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config != null && FastMath.isInitialized() && config.fastMath) {
            cir.setReturnValue(FastMath.inverseSqrt(value));
        }
    }

    @Inject(method = "floorMod(II)I", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastFloorModInt(int dividend, int divisor, CallbackInfoReturnable<Integer> cir) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config != null && config.fastMath) {
            int m = dividend % divisor;
            if ((m ^ divisor) < 0) {
                m += divisor;
            }
            cir.setReturnValue(m);
        }
    }

    @Inject(method = "floorMod(FF)F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastFloorModFloat(float dividend, float divisor, CallbackInfoReturnable<Float> cir) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config != null && config.fastMath) {
            cir.setReturnValue(dividend - Mth.floor(dividend / divisor) * divisor);
        }
    }

    @Inject(method = "floorMod(DD)D", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastFloorModDouble(double dividend, double divisor, CallbackInfoReturnable<Double> cir) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config != null && config.fastMath) {
            cir.setReturnValue(dividend - Math.floor(dividend / divisor) * divisor);
        }
    }

    @Inject(method = "ceilLog2", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastCeilLog2(int value, CallbackInfoReturnable<Integer> cir) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config != null && config.fastMath) {
            if (value <= 1) {
                cir.setReturnValue(0);
                return;
            }
            cir.setReturnValue(32 - Integer.numberOfLeadingZeros(value - 1));
        }
    }

    @Inject(method = "floorLog2", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastFloorLog2(int value, CallbackInfoReturnable<Integer> cir) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config != null && config.fastMath) {
            cir.setReturnValue(31 - Integer.numberOfLeadingZeros(value));
        }
    }
}
