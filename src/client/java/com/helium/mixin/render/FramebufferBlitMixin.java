package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.util.VersionMethodResolver;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(Framebuffer.class)
public abstract class FramebufferBlitMixin {

    @Shadow public int textureWidth;
    @Shadow public int textureHeight;

    @Unique
    private static boolean helium$failed = false;

    @Inject(method = "blitToScreen()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$fastBlitModern(CallbackInfo ci) {
        if (helium$failed) return;

        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.fastFramebufferBlit) return;

            if (!VersionMethodResolver.haslegacyfbo()) return;

            Field fboField = VersionMethodResolver.fbofield();
            if (fboField == null) return;

            int fboId = fboField.getInt(this);
            if (fboId <= 0) return;

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fboId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);

            GL30.glBlitFramebuffer(
                    0, 0, this.textureWidth, this.textureHeight,
                    0, 0, this.textureWidth, this.textureHeight,
                    GL30.GL_COLOR_BUFFER_BIT,
                    GL30.GL_NEAREST
            );

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            ci.cancel();
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("fast blit failed ({})", t.getClass().getSimpleName());
            }
        }
    }
}
