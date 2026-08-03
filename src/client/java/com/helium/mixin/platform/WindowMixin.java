package com.helium.mixin.platform;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.platform.DwmApi;
import com.helium.platform.WindowsVersion;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class WindowMixin {

    @Shadow
    @Final
    private long handle;

    @Shadow
    private boolean fullscreen;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void helium$initWindowStyle(CallbackInfo ci) {
        if (Util.getPlatform() != Util.OS.WINDOWS) return;

        WindowsVersion.init();
        DwmApi.applyWindowStyle(this.fullscreen, this.handle);
    }

    @Inject(method = "toggleFullScreen", at = @At("TAIL"))
    private void helium$onToggleFullscreen(CallbackInfo ci) {
        if (Util.getPlatform() != Util.OS.WINDOWS) return;

        DwmApi.applyWindowStyle(this.fullscreen, this.handle);
    }

    @Inject(method = "defaultErrorCallback", at = @At("HEAD"), cancellable = true)
    private void helium$suppressGLErrorsModern(int error, long description, CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config != null && config.suppressOpenGLErrors) {
            ci.cancel();
        }
    }
}
