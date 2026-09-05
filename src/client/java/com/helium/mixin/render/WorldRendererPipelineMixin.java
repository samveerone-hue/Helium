package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.DevModeOptimizer;
import com.helium.render.RenderPipeline;
import com.helium.render.RenderBatch;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.ObjectAllocator;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererPipelineMixin {

    @Unique
    private static boolean helium$failed = false;

    @Inject(
            method = "render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            at = @At("HEAD"),
            require = 0)
    private void helium$captureRentitiesMatrices(
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f basicProjectionMatrix,
            Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.modEnabled || !config.entityGpuBatching || camera == null) return;
        var pos = camera.getPos();
        com.helium.rentities.entities.EntityBatchRenderer.captureWorldRenderMatrices(
                positionMatrix, projectionMatrix, pos.x, pos.y, pos.z);
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void helium$frameStart(CallbackInfo ci) {
        if (helium$failed) return;
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled) return;

            if (config.devMode && DevModeOptimizer.isActive()) {
                DevModeOptimizer.onFrameStart();
            }

            if (config.renderPipelining) {
                RenderBatch.beginFrame();
            }

            if (!config.renderPipelining || !RenderPipeline.isInitialized()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            int maxFps = client.options.getMaxFps().getValue();
            // Minecraft uses 260 as its unlimited sentinel. Never leave our internal
            // 60 FPS default active when the user requests an unlimited/high cap.
            RenderPipeline.setTargetFps(maxFps);

            RenderPipeline.onFrameStart();
        } catch (Throwable t) {
            helium$failed = true;
            HeliumClient.LOGGER.warn("render pipeline hook disabled ({})", t.getClass().getSimpleName());
        }
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
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
