package com.helium.rentities.entities;

import net.minecraft.client.render.entity.state.ArmadilloEntityRenderState;
import net.minecraft.client.render.entity.state.ArmorStandEntityRenderState;
import net.minecraft.client.render.entity.state.BatEntityRenderState;
import net.minecraft.client.render.entity.state.BeeEntityRenderState;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.CreeperEntityRenderState;
import net.minecraft.client.render.entity.state.GoatEntityRenderState;
import net.minecraft.client.render.entity.state.SheepEntityRenderState;
import net.minecraft.client.render.entity.state.SnifferEntityRenderState;
import net.minecraft.client.render.entity.state.WardenEntityRenderState;
import net.minecraft.client.render.entity.state.WitherEntityRenderState;
import net.minecraft.entity.EntityType;

/**
 * Single correctness gate for the Rentities replacement renderer.
 *
 * Rentities replaces the base body submission for living renderers, while the
 * vanilla LivingEntityRenderer continues running its feature pipeline. This
 * means armor, held items, saddles, capes and custom feature layers remain
 * responsible for their own geometry and textures instead of being silently
 * dropped.
 */
public final class RentitiesRenderStatePolicy {
    private RentitiesRenderStatePolicy() {}

    public static boolean canBatch(Object state, EntityType<?> type) {
        if (state == null || type == null) return false;
        if (!EntityBatchRegistry.isGpuBatchable(type)) return false;

        // Player rendering has a separate vanilla/Helium path.
        if (type == EntityType.PLAYER) return false;

        // Armor Stand geometry is supported only for the standard full-size
        // configuration. Equipment is intentionally allowed: vanilla's feature
        // renderers continue after Rentities suppresses only the base model.
        if (state instanceof ArmorStandEntityRenderState stand) {
            return !stand.small && !stand.marker && stand.showArms && stand.showBasePlate;
        }

        // These render states carry geometry-affecting or feature-layer state that
        // is not represented by the current fixed baked body mesh. Keep them on
        // vanilla until their dedicated GPU paths are implemented.
        if (state instanceof SheepEntityRenderState
                || state instanceof GoatEntityRenderState
                || state instanceof CreeperEntityRenderState
                || state instanceof SnifferEntityRenderState
                || state instanceof ArmadilloEntityRenderState
                || state instanceof BatEntityRenderState
                || state instanceof BeeEntityRenderState
                || state instanceof WardenEntityRenderState
                || state instanceof WitherEntityRenderState) {
            return false;
        }

        // Armed entities are allowed to batch with their held items. Their
        // HeldItemFeatureRenderer keeps running in vanilla after the Rentities base
        // model command is consumed by RentitiesBodyModelSuppression.
        return true;
    }
}
