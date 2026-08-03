package com.helium.mixin.memory;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.client.Screenshot;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(Screenshot.class)
public abstract class ScreenshotLeakMixin {

    @Unique
    private static ByteBuffer helium$trackedBuffer = null;

    @Inject(method = "grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V", at = @At("HEAD"), require = 0)
    private static void helium$trackBufferAllocSaveSimple(CallbackInfo ci) {
        helium$resetTracker();
    }

    @Inject(method = "grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V", at = @At("RETURN"), require = 0)
    private static void helium$freeTrackedBufferSaveSimple(CallbackInfo ci) {
        helium$freeTracker();
    }

    @Inject(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V", at = @At("HEAD"), require = 0)
    private static void helium$trackBufferAllocSaveFull(CallbackInfo ci) {
        helium$resetTracker();
    }

    @Inject(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V", at = @At("RETURN"), require = 0)
    private static void helium$freeTrackedBufferSaveFull(CallbackInfo ci) {
        helium$freeTracker();
    }

    @Inject(method = "takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V", at = @At("HEAD"), require = 0)
    private static void helium$trackBufferAllocTakeScreenshot(CallbackInfo ci) {
        helium$resetTracker();
    }

    @Inject(method = "takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V", at = @At("RETURN"), require = 0)
    private static void helium$freeTrackedBufferTakeScreenshot(CallbackInfo ci) {
        helium$freeTracker();
    }

    @Inject(method = "takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V", at = @At("HEAD"), require = 0)
    private static void helium$trackBufferAllocTakeScreenshotI(CallbackInfo ci) {
        helium$resetTracker();
    }

    @Inject(method = "takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V", at = @At("RETURN"), require = 0)
    private static void helium$freeTrackedBufferTakeScreenshotI(CallbackInfo ci) {
        helium$freeTracker();
    }

    @Unique
    private static void helium$resetTracker() {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.screenshotLeakFix) return;

        helium$trackedBuffer = null;
    }

    @Unique
    private static void helium$freeTracker() {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.screenshotLeakFix) return;

        if (helium$trackedBuffer != null) {
            try {
                MemoryUtil.memFree(helium$trackedBuffer);
            } catch (Throwable ignored) {}
            helium$trackedBuffer = null;
        }
    }

}
