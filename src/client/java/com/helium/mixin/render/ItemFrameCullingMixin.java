package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.CullingHelper;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameCullingMixin {

    @Unique
    private static boolean helium$failed = false;

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$cullitemframe(ItemFrameRenderState renderstate, PoseStack matrices,
                                       SubmitNodeCollector queue, CameraRenderState camerastate,
                                       CallbackInfo ci) {
        if (helium$failed) return;
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled) return;

            if (!config.itemFrameCulling && !config.itemFrameLOD) return;

            double distsq = renderstate.distanceToCameraSq;

            if (config.itemFrameLOD) {
                double lodrangesq = (double) config.itemFrameLODRange * config.itemFrameLODRange;
                if (distsq > lodrangesq) {
                    ci.cancel();
                    return;
                }
            }

            if (config.itemFrameCulling) {
                Direction facing = renderstate.direction;
                if (facing != null) {
                    BlockPos frameblockpos = new BlockPos(
                            (int) Math.floor(renderstate.x),
                            (int) Math.floor(renderstate.y),
                            (int) Math.floor(renderstate.z)
                    );
                    if (CullingHelper.shouldcullback(frameblockpos, facing)) {
                        Vec3 framepos = new Vec3(renderstate.x, renderstate.y, renderstate.z);
                        if (!CullingHelper.isfacingcamera(facing, framepos)) {
                            ci.cancel();
                        }
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
