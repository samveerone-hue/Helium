package com.helium.mixin.render;

import com.helium.rentities.entities.RentitiesEquipmentBatcher;
import com.helium.rentities.entities.RentitiesEquipmentContext;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Converts simple equipment layer submissions into Rentities GPU geometry. */
@Mixin(EquipmentRenderer.class)
public abstract class EquipmentRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private <S> void helium$batchSimpleArmor(
            EquipmentModel.LayerType layerType,
            RegistryKey<EquipmentAsset> assetKey,
            Model<? super S> model,
            S state,
            ItemStack stack,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            @Nullable Identifier textureId,
            int outlineColor,
            int initialOrder,
            CallbackInfo ci) {
        if (!RentitiesEquipmentContext.isCurrent(state)) return;
        if (stack == null || stack.isEmpty()) return;

        // Trim, dye, glint and outline passes need vanilla's full material pipeline.
        if (textureId == null) return;
        if (stack.get(DataComponentTypes.TRIM) != null) return;
        if (stack.get(DataComponentTypes.DYED_COLOR) != null) return;
        if (stack.hasGlint()) return;
        if (outlineColor != 0) return;

        if (RentitiesEquipmentBatcher.capture(model, matrices, light, textureId)) {
            ci.cancel();
        }
    }
}
