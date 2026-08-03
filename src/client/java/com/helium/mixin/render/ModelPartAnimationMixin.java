package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.FastAnimationOptimizer;
import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class ModelPartAnimationMixin {

    @Shadow public float xRot;
    @Shadow public float yRot;
    @Shadow public float zRot;
    @Shadow public float xScale;
    @Shadow public float yScale;
    @Shadow public float zScale;
    @Shadow public float x;
    @Shadow public float y;
    @Shadow public float z;

    @Unique
    private final Quaternionf helium$reusedQuat = new Quaternionf();

    @Inject(method = "translateAndRotate", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$fastApplyTransform(PoseStack matrices, CallbackInfo ci) {
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled || !config.fastAnimations) return;
            if (!FastAnimationOptimizer.isInitialized()) return;

            matrices.translate(x / 16.0f, y / 16.0f, z / 16.0f);

            if (xRot != 0f || yRot != 0f || zRot != 0f) {
                matrices.mulPose(helium$reusedQuat.rotationZYX(zRot, yRot, xRot));
            }

            if (xScale != 1f || yScale != 1f || zScale != 1f) {
                matrices.scale(xScale, yScale, zScale);
            }

            ci.cancel();
        } catch (Throwable ignored) {}
    }
}
