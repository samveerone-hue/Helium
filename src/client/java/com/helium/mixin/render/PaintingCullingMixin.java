package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.CullingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PaintingEntityRenderer;
import net.minecraft.client.render.entity.state.PaintingEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.painting.PaintingVariant;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PaintingEntityRenderer.class)
public abstract class PaintingCullingMixin {

    @Unique
    private static boolean helium$failed = false;

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/PaintingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$cullpainting(PaintingEntityRenderState renderstate, MatrixStack matrices,
                                      OrderedRenderCommandQueue queue, CameraRenderState camerastate,
                                      CallbackInfo ci) {
        if (helium$failed) return;
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled || !config.paintingCulling) return;

            Direction facing = renderstate.facing;
            if (facing == null) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) return;

            BlockPos centerpos = BlockPos.ofFloored(renderstate.x, renderstate.y, renderstate.z);

            if (CullingHelper.shouldcullback(centerpos, facing)
                    && !CullingHelper.isfacingcamera(facing, renderstate.x, renderstate.y, renderstate.z)) {
                ci.cancel();
            }
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("painting culling failed ({})", t.getClass().getSimpleName());
            }
        }
    }
}
