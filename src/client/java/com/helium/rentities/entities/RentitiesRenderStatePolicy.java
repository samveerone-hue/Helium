package com.helium.rentities.entities;

import net.minecraft.client.render.entity.state.ArmadilloEntityRenderState;
import net.minecraft.client.render.entity.state.ArmorStandEntityRenderState;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
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
import net.minecraft.item.ItemStack;

/**
 * Single correctness gate for the Rentities replacement renderer.
 *
 * The replacement mesh currently represents the base model only. It does not
 * reproduce arbitrary feature layers, equipped item models, or every modern
 * render-state-specific animation. Falling back here is intentional: one frame
 * of vanilla rendering is preferable to silently dropping a layer or displaying
 * a stale pose.
 */
public final class RentitiesRenderStatePolicy {
    private RentitiesRenderStatePolicy() {}

    public static boolean canBatch(Object state, EntityType<?> type) {
        if (state == null || type == null) return false;
        if (!EntityBatchRegistry.isGpuBatchable(type)) return false;

        // Player rendering has a separate vanilla/Helium path.
        if (type == EntityType.PLAYER) return false;

        // Armor Stand geometry is supported only for the standard full-size
        // configuration. Variant visibility/scale changes alter the model and
        // therefore must use the vanilla renderer.
        if (state instanceof ArmorStandEntityRenderState stand) {
            if (stand.small || stand.marker || !stand.showArms || !stand.showBasePlate) return false;
            return hasNoBipedEquipment(stand);
        }

        // The cached mesh currently contains the renderer's base model, not
        // feature-layer/equipment geometry. Never batch an equipped biped.
        if (state instanceof BipedEntityRenderState biped && !hasNoBipedEquipment(biped)) {
            return false;
        }

        // These render states carry geometry-affecting or feature-layer state that
        // is not represented by the current fixed instance schema / baked mesh.
        // Keeping them on vanilla preserves exact visuals while the GPU path stays
        // available for ordinary state-compatible entities.
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

        // Armed entities may be non-biped render states. Their held-item model is
        // a separate render layer, which the current mesh path does not submit.
        if (state instanceof ArmedEntityRenderState armed) {
            if (!armed.leftHandItem.isEmpty() || !armed.rightHandItem.isEmpty()) return false;
        }

        return true;
    }

    private static boolean hasNoBipedEquipment(BipedEntityRenderState state) {
        return isEmpty(state.leftHandItem)
                && isEmpty(state.rightHandItem)
                && isEmpty(state.equippedHeadStack)
                && isEmpty(state.equippedChestStack)
                && isEmpty(state.equippedLegsStack)
                && isEmpty(state.equippedFeetStack);
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }
}
