package com.helium.mixin.render;

import com.helium.rentities.entities.EntityBatchRegistry;
import com.helium.rentities.entities.EntityBatchRenderer;
import com.helium.rentities.entities.EntityInstance;
import net.minecraft.client.render.entity.state.ArmorStandEntityRenderState;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.util.math.EulerAngle;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * Replaces the legacy/reflection interpolation values in EntityBatchRenderer's
 * packed instance with the authoritative 1.21.11 render-state values.
 *
 * Minecraft has already prepared these values for the current render tick, so
 * interpolating previous/current entity fields again can visibly double-lerp
 * rotation and movement. The mixin also carries render-state light, exact Armor
 * Stand Euler poses, and the exact baked head pivot into the GPU instance payload.
 */
@Mixin(targets = "com.helium.rentities.entities.EntityBatchRenderer")
public abstract class EntityBatchRendererStateMixin {

    private static volatile Field meshBakerField;
    private static volatile Field bonePivotDataField;

    @Inject(method = "writeEntityInstance", at = @At("RETURN"), require = 1)
    private void helium$applyRenderState(long ptr, Object state, double x, double y, double z,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || state == null) return;

        EntityTypeHolder typeHolder = resolveType(state);
        if (state instanceof EntityRenderState renderState) {
            MemoryUtil.memPutInt(ptr + EntityInstance.OFFSET_PACKED_LIGHT, renderState.light);

            if (state instanceof LivingEntityRenderState living) {
                // These are authoritative values for this rendered frame. Do not
                // interpolate previous/current fields a second time.
                MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_ROTATION_Y,
                        (float) Math.toRadians(180.0f - living.bodyYaw));
                MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_HEAD_YAW,
                        (float) Math.toRadians(living.relativeHeadYaw));
                MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_HEAD_PITCH,
                        (float) Math.toRadians(living.pitch));
                MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_LIMB_SWING,
                        living.limbSwingAnimationProgress);
                MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_LIMB_SWING_AMT,
                        living.limbSwingAmplitude);
                MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_DEATH_TIME,
                        living.deathTime);
                MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_SNEAK_PROGRESS,
                        renderState.sneaking ? 1.0f : 0.0f);
                MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_HURT_TIME,
                        living.hurt ? 10.0f : 0.0f);

                int flags = MemoryUtil.memGetInt(ptr + EntityInstance.OFFSET_FLAGS);
                if (renderState.invisible) flags |= EntityInstance.FLAG_IS_INVISIBLE;
                else flags &= ~EntityInstance.FLAG_IS_INVISIBLE;
                if (living.touchingWater) flags |= EntityInstance.FLAG_IS_IN_WATER;
                else flags &= ~EntityInstance.FLAG_IS_IN_WATER;
                MemoryUtil.memPutInt(ptr + EntityInstance.OFFSET_FLAGS, flags);
            } else {
                int flags = MemoryUtil.memGetInt(ptr + EntityInstance.OFFSET_FLAGS);
                if (renderState.invisible) flags |= EntityInstance.FLAG_IS_INVISIBLE;
                else flags &= ~EntityInstance.FLAG_IS_INVISIBLE;
                MemoryUtil.memPutInt(ptr + EntityInstance.OFFSET_FLAGS, flags);
            }
        }

        if (state instanceof ArmedEntityRenderState armed) {
            MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_ATTACK_PROGRESS,
                    armed.handSwingProgress);
        }

        if (state instanceof ArmorStandEntityRenderState stand) {
            MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_ROTATION_Y,
                    (float) Math.toRadians(180.0f - stand.yaw));
            writePose(ptr, EntityInstance.OFFSET_ARMOR_STAND_HEAD_POSE, stand.headRotation);
            writePose(ptr, EntityInstance.OFFSET_ARMOR_STAND_BODY_POSE, stand.bodyRotation);
            writePose(ptr, EntityInstance.OFFSET_ARMOR_STAND_LEFT_ARM_POSE, stand.leftArmRotation);
            writePose(ptr, EntityInstance.OFFSET_ARMOR_STAND_RIGHT_ARM_POSE, stand.rightArmRotation);
            writePose(ptr, EntityInstance.OFFSET_ARMOR_STAND_LEFT_LEG_POSE, stand.leftLegRotation);
            writePose(ptr, EntityInstance.OFFSET_ARMOR_STAND_RIGHT_LEG_POSE, stand.rightLegRotation);
        }

        if (typeHolder.type != null) {
            writeBakedHeadPivot(ptr, typeHolder.type);
        }
    }

    private static EntityTypeHolder resolveType(Object state) {
        try {
            return new EntityTypeHolder(EntityBatchRenderer.getEntityType(state));
        } catch (Throwable ignored) {
            return new EntityTypeHolder(null);
        }
    }

    private static void writeBakedHeadPivot(long ptr, net.minecraft.entity.EntityType<?> type) {
        try {
            EntityBatchRenderer renderer = EntityBatchRenderer.INSTANCE;
            if (renderer == null) return;

            Field mf = meshBakerField;
            if (mf == null) {
                mf = EntityBatchRenderer.class.getDeclaredField("meshBaker");
                mf.setAccessible(true);
                meshBakerField = mf;
            }

            Object baker = mf.get(renderer);
            if (baker == null) return;

            Field bf = bonePivotDataField;
            if (bf == null) {
                bf = baker.getClass().getDeclaredField("bonePivotData");
                bf.setAccessible(true);
                bonePivotDataField = bf;
            }

            float[] pivots = (float[]) bf.get(baker);
            int typeIndex = EntityBatchRegistry.getEntityTypeIndex(type);
            int base = (typeIndex * 10) * 4;
            if (base < 0 || base + 2 >= pivots.length) return;

            MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_HEAD_PIVOT_X, pivots[base]);
            MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_HEAD_PIVOT_Y, pivots[base + 1]);
            MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_HEAD_PIVOT_Z, pivots[base + 2]);
        } catch (Throwable ignored) {
            // A missing pivot is not fatal; the shader retains its existing fallback.
        }
    }

    private static void writePose(long ptr, int offset, EulerAngle pose) {
        if (pose == null) {
            MemoryUtil.memPutFloat(ptr + offset, 0.0f);
            MemoryUtil.memPutFloat(ptr + offset + 4, 0.0f);
            MemoryUtil.memPutFloat(ptr + offset + 8, 0.0f);
        } else {
            MemoryUtil.memPutFloat(ptr + offset, (float) Math.toRadians(pose.pitch()));
            MemoryUtil.memPutFloat(ptr + offset + 4, (float) Math.toRadians(pose.yaw()));
            MemoryUtil.memPutFloat(ptr + offset + 8, (float) Math.toRadians(pose.roll()));
        }
        MemoryUtil.memPutFloat(ptr + offset + 12, 0.0f);
    }

    private record EntityTypeHolder(net.minecraft.entity.EntityType<?> type) {}
}
