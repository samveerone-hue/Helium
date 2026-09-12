package com.helium.mixin.render;

import com.helium.rentities.entities.EntityBatchRenderer;
import net.minecraft.client.render.entity.state.ArmorStandEntityRenderState;
import net.minecraft.util.math.EulerAngle;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Completes the Armor Stand portion of the Rentities instance payload.
 *
 * The GPU shader already has six per-instance Euler-angle slots and an Armor Stand
 * render path. The state writer previously set the Armor Stand flag but never filled
 * those slots, which left all six poses at zero. This mixin keeps the generic writer
 * version-agnostic and only supplies the Armor Stand-specific state that the vanilla
 * render state exposes directly in Yarn 1.21.11.
 */
@Mixin(EntityBatchRenderer.class)
public abstract class RentitiesArmorStandStateMixin {

    @Inject(method = "writeEntityInstance", at = @At("TAIL"))
    private void helium$writeArmorStandState(
            long ptr,
            Object state,
            double rx,
            double ry,
            double rz,
            CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (!(state instanceof ArmorStandEntityRenderState armorStand)) return;

        // ArmorStandEntityRenderState.yaw is the authoritative body rotation for
        // this renderer. Do not rely on inherited LivingEntityRenderState.bodyYaw.
        float rotationY = (float) Math.toRadians(180.0f - armorStand.yaw);
        MemoryUtil.memPutFloat(
                ptr + EntityBatchRendererOffsets.ROTATION_Y,
                rotationY);

        putEuler(ptr + EntityBatchRendererOffsets.ARMOR_STAND_HEAD_POSE, armorStand.headRotation);
        putEuler(ptr + EntityBatchRendererOffsets.ARMOR_STAND_BODY_POSE, armorStand.bodyRotation);
        putEuler(ptr + EntityBatchRendererOffsets.ARMOR_STAND_LEFT_ARM_POSE, armorStand.leftArmRotation);
        putEuler(ptr + EntityBatchRendererOffsets.ARMOR_STAND_RIGHT_ARM_POSE, armorStand.rightArmRotation);
        putEuler(ptr + EntityBatchRendererOffsets.ARMOR_STAND_LEFT_LEG_POSE, armorStand.leftLegRotation);
        putEuler(ptr + EntityBatchRendererOffsets.ARMOR_STAND_RIGHT_LEG_POSE, armorStand.rightLegRotation);

        // Armor Stand head pivot in the baked shader coordinate system:
        // vanilla biped head pivot = 24 pixels above the feet.
        MemoryUtil.memPutFloat(ptr + EntityBatchRendererOffsets.HEAD_PIVOT_X, 0.0f);
        MemoryUtil.memPutFloat(ptr + EntityBatchRendererOffsets.HEAD_PIVOT_Y, 24.0f);
        MemoryUtil.memPutFloat(ptr + EntityBatchRendererOffsets.HEAD_PIVOT_Z, 0.0f);
    }

    private static void putEuler(long ptr, EulerAngle angle) {
        if (angle == null) {
            MemoryUtil.memPutFloat(ptr, 0.0f);
            MemoryUtil.memPutFloat(ptr + 4L, 0.0f);
            MemoryUtil.memPutFloat(ptr + 8L, 0.0f);
            MemoryUtil.memPutFloat(ptr + 12L, 0.0f);
            return;
        }

        MemoryUtil.memPutFloat(ptr, (float) Math.toRadians(angle.getPitch()));
        MemoryUtil.memPutFloat(ptr + 4L, (float) Math.toRadians(angle.getYaw()));
        MemoryUtil.memPutFloat(ptr + 8L, (float) Math.toRadians(angle.getRoll()));
        MemoryUtil.memPutFloat(ptr + 12L, 0.0f);
    }

    /** Offsets mirrored from EntityInstance without depending on its private API surface. */
    private static final class EntityBatchRendererOffsets {
        static final long ROTATION_Y = 12L;
        static final long HEAD_PIVOT_X = 144L;
        static final long HEAD_PIVOT_Y = 148L;
        static final long HEAD_PIVOT_Z = 152L;

        static final long ARMOR_STAND_HEAD_POSE = 160L;
        static final long ARMOR_STAND_BODY_POSE = 176L;
        static final long ARMOR_STAND_LEFT_ARM_POSE = 192L;
        static final long ARMOR_STAND_RIGHT_ARM_POSE = 208L;
        static final long ARMOR_STAND_LEFT_LEG_POSE = 224L;
        static final long ARMOR_STAND_RIGHT_LEG_POSE = 240L;

        private EntityBatchRendererOffsets() {}
    }
}
