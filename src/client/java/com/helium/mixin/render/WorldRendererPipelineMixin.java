package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.DevModeOptimizer;
import com.helium.render.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class WorldRendererPipelineMixin {

    @Unique
    private static boolean helium$failed = false;

    @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void helium$frameStart(CallbackInfo ci) {
        if (helium$failed) return;
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled) return;

            if (config.devMode && DevModeOptimizer.isActive()) {
                DevModeOptimizer.onFrameStart();
            }

            if (!config.renderPipelining || !RenderPipeline.isInitialized()) return;

            Minecraft client = Minecraft.getInstance();
            int maxFps = client.options.framerateLimit().get();
            if (maxFps > 0 && maxFps < 260) {
                RenderPipeline.setTargetFps(maxFps);
            }

            RenderPipeline.onFrameStart();
        } catch (Throwable t) {
            helium$failed = true;
            HeliumClient.LOGGER.warn("render pipeline hook disabled ({})", t.getClass().getSimpleName());
        }
    }

    @Inject(method = "renderLevel", at = @At("RETURN"), require = 0)
    private void helium$frameEnd(CallbackInfo ci) {
        if (helium$failed) return;
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled || !config.renderPipelining) return;
            if (!RenderPipeline.isInitialized()) return;

            RenderPipeline.onFrameEnd();
        } catch (Throwable ignored) {}
    }
}
