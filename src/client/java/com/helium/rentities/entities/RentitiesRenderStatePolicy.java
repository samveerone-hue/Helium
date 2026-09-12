package com.helium.rentities.entities;

import net.minecraft.client.render.entity.state.ArmadilloEntityRenderState;
import net.minecraft.client.render.entity.state.ArmorStandEntityRenderState;
import net.minecraft.client.render.entity.state.BatEntityRenderState;
import net.minecraft.client.render.entity.state.BeeEntityRenderState;
import net.minecraft.client.render.entity.state.CreeperEntityRenderState;
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
        if (!EntityBatchRegistry.isGpuBatchable(type)) return false;
        if (type == EntityType.PLAYER) return false;

        // The current GPU ABI represents adult/base-scale geometry only.
        // Let vanilla handle baby/scaled/upside-down variants rather than drawing
        // a correctly animated model at the wrong transform.
        if (state instanceof LivingEntityRenderState living) {
            if (living.baby || Math.abs(living.baseScale - 1.0f) > 0.001f || living.flipUpsideDown) return false;
        }

        if (state instanceof ArmorStandEntityRenderState stand) {
            return !stand.small && !stand.marker && stand.showArms && stand.showBasePlate;
        }

        // Sheep variants change baked geometry/material state (sheared/rainbow),
        // Creeper uses special swell scaling, and Warden/Wither exceed the current
        // generic ten-bone model ABI. Preserve exact vanilla rendering for them.
        if (state instanceof SheepEntityRenderState
                || state instanceof CreeperEntityRenderState
                || state instanceof WardenEntityRenderState
                || state instanceof WitherEntityRenderState) {
            return false;
        }

        // Bat/Bee/Armadillo are supported by the exact-pose path: their model
        // rotations are captured from Minecraft's own setAngles implementation.
        return true;
    }
}
