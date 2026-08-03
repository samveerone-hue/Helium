package com.helium.mixin.dsa;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.gpu.GBGL;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public abstract class FramebufferDSAMixin {

    @Shadow public int width;
    @Shadow public int height;

    @Unique
    private static boolean helium$dsaFboFailed = false;

    @Inject(method = "createBuffers", at = @At("HEAD"), require = 0)
    private void helium$initDSACaps(int width, int height, CallbackInfo ci) {
        if (helium$dsaFboFailed) return;

        try {
            GBGL.initcaps();
        } catch (Throwable t) {
            if (!helium$dsaFboFailed) {
                helium$dsaFboFailed = true;
                HeliumClient.LOGGER.warn("[helium] DSA FBO caps init failed in RenderTarget.createBuffers", t);
            }
        }
    }

}
