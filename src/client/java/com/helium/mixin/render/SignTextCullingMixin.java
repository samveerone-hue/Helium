package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.CullingHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignRenderer.class)
public abstract class SignTextCullingMixin {

    @Unique
    private static boolean helium$failed = false;

    @Unique
    private static final double ONE_SIGN_ROTATION = Math.PI / 8.0;

    @Inject(method = "submitSignText", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$cullsigntext(SignRenderState renderstate, PoseStack matrices,
                                      SubmitNodeCollector queue,
                                      SignText text, CallbackInfo ci) {
        if (helium$failed) return;
        try {
            boolean front = text == renderstate.frontText;
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled || !config.signTextCulling) return;

            BlockPos pos = renderstate.blockPos;
            if (pos == null) return;

            Minecraft client = Minecraft.getInstance();
            if (client.level == null || client.gameRenderer == null) return;

            Vec3 camerapos = client.gameRenderer.getMainCamera().position();
            if (camerapos == null) return;

            BlockState blockstate = client.level.getBlockState(pos);
            if (blockstate == null) return;

            if (blockstate.hasProperty(WallSignBlock.FACING)) {
                Direction facing = blockstate.getValue(WallSignBlock.FACING);
                Vec3 signpos = Vec3.atCenterOf(pos).subtract(
                        facing.getStepX() * 0.39, 0, facing.getStepZ() * 0.39
                );
                boolean hidden = helium$shouldhidewallsigntext(facing, signpos, camerapos);
                if (front == hidden) {
                    ci.cancel();
                    return;
                }
            } else if (blockstate.hasProperty(StandingSignBlock.ROTATION)) {
                int rotation = blockstate.getValue(StandingSignBlock.ROTATION);
                double angle = rotation * ONE_SIGN_ROTATION;
                Vec3 signpos = Vec3.atCenterOf(pos);
                if (front) {
                    if (helium$isbehindline(angle, signpos, camerapos)) {
                        ci.cancel();
                        return;
                    }
                } else {
                    if (helium$isbehindline(angle, camerapos, signpos)) {
                        ci.cancel();
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("sign text culling failed ({})", t.getClass().getSimpleName());
            }
        }
    }

    @Unique
    private static boolean helium$shouldhidewallsigntext(Direction facing, Vec3 signpos, Vec3 camerapos) {
        return switch (facing) {
            case NORTH -> camerapos.z > signpos.z;
            case SOUTH -> camerapos.z < signpos.z;
            case WEST -> camerapos.x > signpos.x;
            case EAST -> camerapos.x < signpos.x;
            default -> false;
        };
    }

    @Unique
    private static boolean helium$isbehindline(double angle, Vec3 a, Vec3 b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double lineangle = Math.atan2(-dz, dx);
        double diff = lineangle - angle;
        diff = ((diff + Math.PI) % (2 * Math.PI) + (2 * Math.PI)) % (2 * Math.PI) - Math.PI;
        return diff > 0;
    }
}
