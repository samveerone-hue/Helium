package com.helium.mixin.platform;

import com.helium.HeliumClient;
import com.mojang.blaze3d.opengl.GlBackend;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GlBackend.class)
public abstract class GlBackendHintsMixin {

    @Redirect(method = "setWindowHints", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", remap = false))
    private void helium$overrideGLContext(int hint, int value) {
        if (GlContextUpgrade.isEnabled()) {
            boolean isMacOS = Util.getPlatform() == Util.OS.OSX;
            if (hint == GLFW.GLFW_CONTEXT_VERSION_MAJOR) {
                value = 4;
                HeliumClient.LOGGER.info("gl context upgrade: major version set to {}", value);
            } else if (hint == GLFW.GLFW_CONTEXT_VERSION_MINOR) {
                value = isMacOS ? 1 : 6;
                HeliumClient.LOGGER.info("gl context upgrade: minor version set to {}{}", value, isMacOS ? " (macOS limit)" : "");
            }
        }
        GLFW.glfwWindowHint(hint, value);
    }
}
