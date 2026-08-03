package com.helium.mixin.reflex;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.reflex.ReflexManager;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererReflexMixin {

    @Unique
    private static boolean helium$reflexInitialized = false;

    @Unique
    private static boolean helium$reflexFailed = false;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void helium$reflexOnFrameStart(CallbackInfo ci) {
        if (helium$reflexFailed) return;

        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.enableReflex) return;

            if (!helium$reflexInitialized) {
                helium$reflexInitialized = true;
                ReflexManager.init();
            }

            if (ReflexManager.isEnabled()) {
                ReflexManager.onFrameStart(config.reflexOffsetNs);
            }
        } catch (Throwable t) {
            if (!helium$reflexFailed) {
                helium$reflexFailed = true;
                HeliumClient.LOGGER.warn("reflex frame start failed: {}", t.getMessage());
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void helium$reflexOnFrameEnd(CallbackInfo ci) {
        if (helium$reflexFailed) return;

        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.enableReflex) return;

            if (ReflexManager.isEnabled()) {
                ReflexManager.onFrameEnd();
            }
        } catch (Throwable t) {
            if (!helium$reflexFailed) {
                helium$reflexFailed = true;
                HeliumClient.LOGGER.warn("reflex frame end failed: {}", t.getMessage());
            }
        }
    }
}
