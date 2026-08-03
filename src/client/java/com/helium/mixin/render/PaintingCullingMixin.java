package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.CullingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.client.renderer.entity.state.PaintingRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PaintingRenderer.class)
public abstract class PaintingCullingMixin {

    @Unique
    private static boolean helium$failed = false;

    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/PaintingRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$cullpainting(PaintingRenderState renderstate, PoseStack matrices,
                                      SubmitNodeCollector queue, CameraRenderState camerastate,
                                      CallbackInfo ci) {
        if (helium$failed) return;
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled || !config.paintingCulling) return;

            Direction facing = renderstate.direction;
            if (facing == null) return;

            Minecraft client = Minecraft.getInstance();
            if (client.level == null) return;

            Vec3 paintingpos = new Vec3(renderstate.x, renderstate.y, renderstate.z);
            BlockPos centerpos = BlockPos.containing(paintingpos);

            if (CullingHelper.shouldcullback(centerpos, facing)) {
                if (!CullingHelper.isfacingcamera(facing, paintingpos)) {
                    ci.cancel();
                }
            }
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("painting culling failed ({})", t.getClass().getSimpleName());
            }
        }
    }
}
