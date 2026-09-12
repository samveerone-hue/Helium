package com.helium.rentities.entities;

import net.minecraft.client.render.entity.state.ArmorStandEntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.SheepEntityRenderState;
import net.minecraft.client.render.entity.state.WardenEntityRenderState;
import net.minecraft.client.render.entity.state.WitherEntityRenderState;
import net.minecraft.entity.EntityType;

/** Single correctness gate for the Rentities replacement renderer. */
public final class RentitiesRenderStatePolicy {
    private RentitiesRenderStatePolicy() {}

    public static boolean canBatch(Object state, EntityType<?> type) {
        if (state == null || type == null) return false;
        if (type == EntityType.PLAYER) return false;

        // These variants have renderer/material state that the common GPU ABI does not
        // currently represent safely.
        if (state instanceof SheepEntityRenderState
                || state instanceof WardenEntityRenderState
                || state instanceof WitherEntityRenderState) {
            return false;
        }

        // A previously unknown modded entity may still be representable if its renderer
        // exposes a standard LivingEntity model with a compatible rig. Discover it before
        // applying the normal registry gate so the mesh baker and texture bootstrap can
        // use the same registry-backed GPU path on subsequent frames.
        if (!EntityBatchRegistry.isGpuBatchable(type)) {
            if (RentitiesCustomEntitySupport.resolveAndRegister(state, type)
                    == EntityAnimationCategory.CPU_ANIMATED) {
                return false;
            }
        }

        if (!EntityBatchRegistry.isGpuBatchable(type)) return false;

        // The common GPU ABI represents adult/base-scale geometry only.
        // Let vanilla handle baby/scaled/upside-down variants rather than drawing
        // a correctly animated model at the wrong transform.
        if (state instanceof LivingEntityRenderState living) {
            if (living.baby || Math.abs(living.baseScale - 1.0f) > 0.001f || living.flipUpsideDown) return false;
        }

        if (state instanceof ArmorStandEntityRenderState stand) {
            return !stand.small && !stand.marker && stand.showArms && stand.showBasePlate;
        }

        return true;
    }
}
