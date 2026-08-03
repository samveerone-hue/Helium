package com.helium.mixin.math;

import com.helium.HeliumClient;
import com.helium.math.FastMath;
import com.helium.util.VersionMethodResolver;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.MethodHandle;

@Mixin(Mth.class)
public abstract class MathHelperDoubleMixin {

    @Inject(method = "sin(D)F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastsinmoderndouble(double value, CallbackInfoReturnable<Float> cir) {
        if (FastMath.isInitialized() && HeliumClient.getConfig() != null && HeliumClient.getConfig().fastMath) {
            cir.setReturnValue(FastMath.sin(value));
        }
    }

    @Inject(method = "cos(D)F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$fastcosmoderndouble(double value, CallbackInfoReturnable<Float> cir) {
        if (FastMath.isInitialized() && HeliumClient.getConfig() != null && HeliumClient.getConfig().fastMath) {
            cir.setReturnValue(FastMath.cos(value));
        }
    }

    @Unique
    private static float helium$callvanillasin(double value) {
        try {
            MethodHandle h = VersionMethodResolver.sindoublehandle();
            if (h != null) return (float) h.invokeExact(value);
        } catch (Throwable ignored) {}
        return (float) Math.sin(value);
    }

    @Unique
    private static float helium$callvanillacos(double value) {
        try {
            MethodHandle h = VersionMethodResolver.cosdoublehandle();
            if (h != null) return (float) h.invokeExact(value);
        } catch (Throwable ignored) {}
        return (float) Math.cos(value);
    }
}
