package com.helium.mixin.platform;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.platform.DwmApi;
import com.helium.platform.WindowsVersion;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class WindowMixin {

    @Shadow
    @Final
    private long handle;

    @Shadow
    private boolean fullscreen;

    @Unique
    private static Boolean helium$glContextUpgradeEnabled = null;

    @Unique
    private static boolean helium$isGlContextUpgradeEnabled() {
        if (helium$glContextUpgradeEnabled != null) return helium$glContextUpgradeEnabled;

        boolean threatenGLPresent = net.fabricmc.loader.api.FabricLoader.getInstance()
                .isModLoaded("threatengl");
        if (threatenGLPresent) {
            helium$glContextUpgradeEnabled = false;
            HeliumClient.LOGGER.info("threatengl detected - disabling gl context upgrade to avoid conflicts");
            return false;
        }

        try {
            java.nio.file.Path cfgPath = net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getConfigDir().resolve("helium.json");
            if (java.nio.file.Files.exists(cfgPath)) {
                String json = java.nio.file.Files.readString(cfgPath);
                if (json.contains("\"glContextUpgrade\": false") || json.contains("\"glContextUpgrade\":false")) {
                    helium$glContextUpgradeEnabled = false;
                    return false;
                }
            }
        } catch (Throwable ignored) {}
        helium$glContextUpgradeEnabled = true;
        return true;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", remap = false), require = 0)
    private void helium$overrideGLContext(int hint, int value) {
        if (helium$isGlContextUpgradeEnabled()) {
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

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void helium$initWindowStyle(CallbackInfo ci) {
        if (Util.getPlatform() != Util.OS.WINDOWS) return;

        WindowsVersion.init();
        DwmApi.applyWindowStyle(this.fullscreen, this.handle);
    }

    @Inject(method = "toggleFullscreen", at = @At("TAIL"), require = 0)
    private void helium$onToggleFullscreen(CallbackInfo ci) {
        if (Util.getPlatform() != Util.OS.WINDOWS) return;

        DwmApi.applyWindowStyle(this.fullscreen, this.handle);
    }

    @Inject(method = "logGlError", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$suppressGLErrorsModern(int error, long description, CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config != null && config.suppressOpenGLErrors) {
            ci.cancel();
        }
    }

    @Inject(method = "logOnGlError", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$suppressGLErrorsLogOn(CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config != null && config.suppressOpenGLErrors) {
            ci.cancel();
        }
    }
}
