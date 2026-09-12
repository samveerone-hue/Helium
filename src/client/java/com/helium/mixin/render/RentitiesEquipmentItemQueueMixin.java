package com.helium.mixin.render;

import com.helium.rentities.entities.RentitiesEquipmentBatcher;
import com.helium.rentities.entities.RentitiesEquipmentContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Replaces ordinary held-item BakedQuad command submissions with Rentities GPU geometry. */
@Mixin(OrderedRenderCommandQueueImpl.class)
public abstract class RentitiesEquipmentItemQueueMixin {
    @Inject(
            method = "submitItem(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void helium$batchHeldItem(
            MatrixStack matrices,
            ItemDisplayContext displayContext,
            int light,
            int overlay,
            int outlineColors,
            int[] tintLayers,
            List<BakedQuad> quads,
            RenderLayer renderLayer,
            @Nullable ItemRenderState.Glint glintType,
            CallbackInfo ci) {
        if (!RentitiesEquipmentContext.isActive()) return;
        if (glintType != null || outlineColors != 0) return;
        if (quads == null || quads.isEmpty()) return;

        if (RentitiesEquipmentBatcher.captureBakedQuads(matrices, quads, tintLayers, light)) {
            ci.cancel();
        }
    }
}
