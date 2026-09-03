package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.CullingHelper;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.client.render.entity.state.ItemFrameEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFrameEntityRenderer.class)
public abstract class ItemFrameCullingMixin {

    @Unique
    private static boolean helium$failed = false;

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemFrameEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$cullitemframe(ItemFrameEntityRenderState renderstate, MatrixStack matrices,
                                       OrderedRenderCommandQueue queue, CameraRenderState camerastate,
                                       CallbackInfo ci) {
        if (helium$failed) return;
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled) return;

            if (!config.itemFrameCulling && !config.itemFrameLOD) return;

            double distsq = renderstate.squaredDistanceToCamera;

            if (config.itemFrameLOD) {
                double lodrangesq = (double) config.itemFrameLODRange * config.itemFrameLODRange;
                if (distsq > lodrangesq) {
                    ci.cancel();
                    return;
                }
            }

            if (config.itemFrameCulling) {
                Direction facing = renderstate.facing;
                if (facing != null) {
                    BlockPos frameblockpos = new BlockPos(
                            (int) Math.floor(renderstate.x),
                            (int) Math.floor(renderstate.y),
                            (int) Math.floor(renderstate.z)
                    );
                    if (CullingHelper.shouldcullback(frameblockpos, facing)
                            && !CullingHelper.isfacingcamera(facing, renderstate.x, renderstate.y, renderstate.z)) {
                        ci.cancel();
                    }
                }
            }
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("item frame culling failed ({})", t.getClass().getSimpleName());
            }
        }
    }
}
