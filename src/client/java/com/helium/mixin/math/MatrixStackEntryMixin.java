package com.helium.mixin.math;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.math.GBFMatrix3f;
import com.helium.math.GBFMatrix4f;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PoseStack.Pose.class)
public abstract class MatrixStackEntryMixin {

    @Mutable
    @Shadow
    Matrix3f normalMatrix;

    @Mutable
    @Shadow
    Matrix4f positionMatrix;

    @Unique
    private static boolean helium$matrixReplaceFailed = false;

    @Inject(method = "<init>()V", at = @At("TAIL"), require = 0)
    private void helium$replaceWithFastMatrices(CallbackInfo ci) {
        if (helium$matrixReplaceFailed) return;

        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config != null && config.fastMath) {
                this.positionMatrix = new GBFMatrix4f(this.positionMatrix);
                this.normalMatrix = new GBFMatrix3f(this.normalMatrix);
            }
        } catch (Throwable t) {
            if (!helium$matrixReplaceFailed) {
                helium$matrixReplaceFailed = true;
                HeliumClient.LOGGER.warn("[helium] fast matrix replacement in PoseStack.Pose failed", t);
            }
        }
    }
}
