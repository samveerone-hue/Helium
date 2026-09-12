package com.helium.mixin.render;

import com.helium.rentities.entities.EntityAnimationCategory;
import com.helium.rentities.entities.EntityBatchRegistry;
import com.helium.rentities.entities.EntityBatchRenderer;
import com.helium.rentities.entities.EntityInstance;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.EntityType;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/** Bypasses obsolete 1.21.11 render-state reflection for modern EntityRenderState objects. */
@Mixin(targets = "com.helium.rentities.entities.EntityBatchRenderer")
public abstract class EntityBatchRendererModernStateMixin {
    @Inject(method = "writeEntityInstance", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$writeModernState(long ptr, Object state, double x, double y, double z,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!(state instanceof EntityRenderState renderState)) return;

        EntityType<?> type = helium$resolveEntityType(state);
        if (type == null) return;

        try {
            MemoryUtil.memSet(ptr, 0, EntityInstance.STRIDE);
            MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_POSITION_X, (float) x);
            MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_POSITION_Y, (float) y);
            MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_POSITION_Z, (float) z);
            MemoryUtil.memPutInt(ptr + EntityInstance.OFFSET_PACKED_LIGHT, renderState.light);

            int flags = 0;
            if (renderState.invisible) flags |= EntityInstance.FLAG_IS_INVISIBLE;
            if (type == EntityType.PLAYER) flags |= EntityInstance.FLAG_IS_PLAYER;
            if (EntityBatchRegistry.hasZombieArms(type)) flags |= EntityInstance.FLAG_ZOMBIE_ARMS;
            MemoryUtil.memPutInt(ptr + EntityInstance.OFFSET_FLAGS, flags);

            if (state instanceof LivingEntityRenderState living) {
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
                MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_HURT_TIME,
                        living.hurt ? 10.0f : 0.0f);
                MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_SNEAK_PROGRESS,
                        living.sneaking ? 1.0f : 0.0f);
                if (living.touchingWater) {
                    flags |= EntityInstance.FLAG_IS_IN_WATER;
                    MemoryUtil.memPutInt(ptr + EntityInstance.OFFSET_FLAGS, flags);
                }
            }

            if (state instanceof ArmedEntityRenderState armed) {
                MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_ATTACK_PROGRESS,
                        armed.handSwingProgress);
            }

            EntityAnimationCategory category = EntityBatchRegistry.getCategory(type);
            MemoryUtil.memPutInt(ptr + EntityInstance.OFFSET_ENTITY_TYPE,
                    EntityBatchRegistry.getEntityTypeIndex(type));
            MemoryUtil.memPutInt(ptr + EntityInstance.OFFSET_ANIM_CATEGORY, category.glslId);
            MemoryUtil.memPutInt(ptr + EntityInstance.OFFSET_TEXTURE_LAYER, 0);
            MemoryUtil.memPutInt(ptr + EntityInstance.OFFSET_HELD_MAIN, EntityInstance.NO_ITEM);
            MemoryUtil.memPutInt(ptr + EntityInstance.OFFSET_HELD_OFFHAND, EntityInstance.NO_ITEM);
            MemoryUtil.memPutInt(ptr + EntityInstance.OFFSET_ARMOR_HEAD, EntityInstance.NO_ARMOR);
            MemoryUtil.memPutInt(ptr + EntityInstance.OFFSET_ARMOR_CHEST, EntityInstance.NO_ARMOR);
            MemoryUtil.memPutInt(ptr + EntityInstance.OFFSET_ARMOR_LEGS, EntityInstance.NO_ARMOR);
            MemoryUtil.memPutInt(ptr + EntityInstance.OFFSET_ARMOR_FEET, EntityInstance.NO_ARMOR);
            MemoryUtil.memPutInt(ptr + EntityInstance.OFFSET_MOUNT_ID, EntityInstance.NO_MOUNT);
            MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_SEAT_OFFSET_X, 0.0f);
            MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_SEAT_OFFSET_Y, 0.0f);
            MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_SEAT_OFFSET_Z, 0.0f);
            MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_TEX_SCALE_X, 1.0f);
            MemoryUtil.memPutFloat(ptr + EntityInstance.OFFSET_TEX_SCALE_Y, 1.0f);

            // EntityBatchRendererStateMixin runs at RETURN and adds exact model poses,
            // special renderer scale and the final authoritative state values.
            cir.setReturnValue(true);
        } catch (Throwable t) {
            MemoryUtil.memSet(ptr, 0, EntityInstance.STRIDE);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getEntityType", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$resolveModernEntityType(Object state,
                                                        CallbackInfoReturnable<EntityType<?>> cir) {
        if (!(state instanceof EntityRenderState)) return;
        EntityType<?> type = helium$resolveEntityType(state);
        if (type != null) cir.setReturnValue(type);
    }

    @Inject(method = "getEntityId", at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$modernStateHasNoLegacyId(Object state,
                                                         CallbackInfoReturnable<Integer> cir) {
        if (state instanceof EntityRenderState) {
            // Modern render states are not the live Entity object and do not expose the
            // legacy integer id used by the old fallback extractor.
            cir.setReturnValue(-1);
        }
    }

    private static EntityType<?> helium$resolveEntityType(Object state) {
        for (Class<?> cls = state.getClass(); cls != null; cls = cls.getSuperclass()) {
            for (String name : new String[]{"field_58171", "entityType", "H"}) {
                try {
                    Field field = cls.getDeclaredField(name);
                    if (!EntityType.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    Object value = field.get(state);
                    if (value instanceof EntityType<?> type) return type;
                } catch (NoSuchFieldException ignored) {
                } catch (Throwable ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}