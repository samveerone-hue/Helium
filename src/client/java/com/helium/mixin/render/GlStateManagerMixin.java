package com.helium.mixin.render;

import com.helium.config.ExperimentalConfig;
import com.helium.render.GLStateCache;
import com.helium.render.ShaderUniformCache;
import com.helium.HeliumClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlStateManager", remap = false)
public abstract class GlStateManagerMixin {

    private static boolean helium$cacheEnabled() {
        try {
            ExperimentalConfig cfg = ExperimentalConfig.load();
            if (!cfg.glStateCache) return false;
            // ImmediatelyFast and other renderer-owned state caches can invalidate assumptions
            // outside GlStateManager; keep this feature off in that combination.
            if (HeliumClient.hasImmediatelyFast()) return false;
            if (!GLStateCache.isInitialized()) GLStateCache.init();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Inject(method = "_activeTexture", at = @At("HEAD"), remap = false)
    private static void helium$trackActiveTexture(int texture, CallbackInfo ci) {
        if (helium$cacheEnabled()) GLStateCache.setActiveTexture(texture);
    }

    @Inject(method = "_bindTexture", at = @At("HEAD"), cancellable = true, remap = false)
    private static void helium$cacheBindTexture(int texture, CallbackInfo ci) {
        if (helium$cacheEnabled() && !GLStateCache.shouldBindTexture(texture)) ci.cancel();
    }

    @Inject(method = "_enableBlend", at = @At("HEAD"), cancellable = true, remap = false)
    private static void helium$cacheEnableBlend(CallbackInfo ci) {
        if (helium$cacheEnabled() && !GLStateCache.shouldEnableBlend(true)) ci.cancel();
    }

    @Inject(method = "_disableBlend", at = @At("HEAD"), cancellable = true, remap = false)
    private static void helium$cacheDisableBlend(CallbackInfo ci) {
        if (helium$cacheEnabled() && !GLStateCache.shouldEnableBlend(false)) ci.cancel();
    }

    @Inject(method = "_enableDepthTest", at = @At("HEAD"), cancellable = true, remap = false)
    private static void helium$cacheEnableDepth(CallbackInfo ci) {
        if (helium$cacheEnabled() && !GLStateCache.shouldEnableDepthTest(true)) ci.cancel();
    }

    @Inject(method = "_disableDepthTest", at = @At("HEAD"), cancellable = true, remap = false)
    private static void helium$cacheDisableDepth(CallbackInfo ci) {
        if (helium$cacheEnabled() && !GLStateCache.shouldEnableDepthTest(false)) ci.cancel();
    }

    @Inject(method = "_enableCull", at = @At("HEAD"), cancellable = true, remap = false)
    private static void helium$cacheEnableCull(CallbackInfo ci) {
        if (helium$cacheEnabled() && !GLStateCache.shouldEnableCullFace(true)) ci.cancel();
    }

    @Inject(method = "_disableCull", at = @At("HEAD"), cancellable = true, remap = false)
    private static void helium$cacheDisableCull(CallbackInfo ci) {
        if (helium$cacheEnabled() && !GLStateCache.shouldEnableCullFace(false)) ci.cancel();
    }

    @Inject(method = "_blendFuncSeparate", at = @At("HEAD"), cancellable = true, remap = false)
    private static void helium$cacheBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha, CallbackInfo ci) {
        if (helium$cacheEnabled() && !GLStateCache.shouldSetBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha)) ci.cancel();
    }

    @Inject(method = "_depthFunc", at = @At("HEAD"), cancellable = true, remap = false)
    private static void helium$cacheDepthFunc(int func, CallbackInfo ci) {
        if (helium$cacheEnabled() && !GLStateCache.shouldSetDepthFunc(func)) ci.cancel();
    }

    @Inject(method = "_glGetUniformLocation", at = @At("HEAD"), cancellable = true, remap = false)
    private static void helium$cacheuniformlocation(int program, CharSequence name, CallbackInfoReturnable<Integer> cir) {
        if (!ShaderUniformCache.isenabled()) return;
        cir.setReturnValue(ShaderUniformCache.getuniform(program, name));
    }
}
