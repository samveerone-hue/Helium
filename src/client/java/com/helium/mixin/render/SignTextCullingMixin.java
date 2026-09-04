package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.SignBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.AbstractSignBlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.SignBlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignBlockEntityRenderer.class)
public abstract class SignTextCullingMixin {

    @Unique
    private static boolean helium$failed = false;

    @Unique
    private static final double ONE_SIGN_ROTATION = Math.PI / 8.0;

    @Inject(method = "renderText", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$cullsigntext(SignBlockEntityRenderState renderstate, MatrixStack matrices,
                                      OrderedRenderCommandQueue queue,
                                      boolean front, CallbackInfo ci) {
        if (helium$failed) return;
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled || !config.signTextCulling) return;

            BlockPos pos = renderstate.pos;
            if (pos == null) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.gameRenderer == null) return;

            Vec3d camerapos = client.gameRenderer.getCamera().getCameraPos();
            if (camerapos == null) return;

            BlockState blockstate = client.world.getBlockState(pos);
            if (blockstate == null) return;

            if (blockstate.contains(WallSignBlock.FACING)) {
                Direction facing = blockstate.get(WallSignBlock.FACING);
                double signX = pos.getX() + 0.5 - facing.getOffsetX() * 0.39;
                double signZ = pos.getZ() + 0.5 - facing.getOffsetZ() * 0.39;
                boolean hidden = helium$shouldhidewallsigntext(facing, signX, signZ, camerapos.x, camerapos.z);
                if (front == hidden) {
                    ci.cancel();
                    return;
                }
            } else if (blockstate.contains(SignBlock.ROTATION)) {
                int rotation = blockstate.get(SignBlock.ROTATION);
                double angle = rotation * ONE_SIGN_ROTATION;
                double signX = pos.getX() + 0.5;
                double signZ = pos.getZ() + 0.5;
                if (front) {
                    if (helium$isbehindline(angle, signX, signZ, camerapos.x, camerapos.z)) {
                        ci.cancel();
                        return;
                    }
                } else {
                    if (helium$isbehindline(angle, camerapos.x, camerapos.z, signX, signZ)) {
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
    private static boolean helium$shouldhidewallsigntext(
            Direction facing, double signX, double signZ, double cameraX, double cameraZ) {
        return switch (facing) {
            case NORTH -> cameraZ > signZ;
            case SOUTH -> cameraZ < signZ;
            case WEST -> cameraX > signX;
            case EAST -> cameraX < signX;
            default -> false;
        };
    }

    @Unique
    private static boolean helium$isbehindline(
            double angle, double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double lineangle = Math.atan2(-dz, dx);
        double diff = lineangle - angle;
        diff = ((diff + Math.PI) % (2 * Math.PI) + (2 * Math.PI)) % (2 * Math.PI) - Math.PI;
        return diff > 0;
    }
}
