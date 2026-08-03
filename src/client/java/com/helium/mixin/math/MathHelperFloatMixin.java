package com.helium.mixin.math;

import com.helium.HeliumClient;
import com.helium.math.FastMath;
import com.helium.util.VersionMethodResolver;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.lang.invoke.MethodHandle;

@Mixin(Mth.class)
public abstract class MathHelperFloatMixin {

    @Unique
    private static float helium$computesin(double value) {
        if (FastMath.isInitialized() && HeliumClient.getConfig() != null && HeliumClient.getConfig().fastMath) {
            return FastMath.sin(value);
        }

        try {
            if (VersionMethodResolver.hasfloatsincos()) {
                MethodHandle h = VersionMethodResolver.sinfloathandle();
                if (h != null) return (float) h.invokeExact((float) value);
            } else if (VersionMethodResolver.hasdoublesincos()) {
                MethodHandle h = VersionMethodResolver.sindoublehandle();
                if (h != null) return (float) h.invokeExact(value);
            }
        } catch (Throwable ignored) {}

        return (float) Math.sin(value);
    }

    @Unique
    private static float helium$computecos(double value) {
        if (FastMath.isInitialized() && HeliumClient.getConfig() != null && HeliumClient.getConfig().fastMath) {
            return FastMath.cos(value);
        }

        try {
            if (VersionMethodResolver.hasfloatsincos()) {
                MethodHandle h = VersionMethodResolver.cosfloathandle();
                if (h != null) return (float) h.invokeExact((float) value);
            } else if (VersionMethodResolver.hasdoublesincos()) {
                MethodHandle h = VersionMethodResolver.cosdoublehandle();
                if (h != null) return (float) h.invokeExact(value);
            }
        } catch (Throwable ignored) {}

        return (float) Math.cos(value);
    }
}
