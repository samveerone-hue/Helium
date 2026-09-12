package com.helium.mixin.render;

import net.minecraft.client.model.Model;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl.ModelCommand;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents the vanilla base entity model from being queued after Rentities has
 * already submitted that entity's body to the GPU batch. Feature-renderer model
 * submissions are untouched because the marker is consumed only by the exact
 * context model instance used by the living entity renderer.
 */
@Mixin(OrderedRenderCommandQueueImpl.class)
public abstract class OrderedRenderCommandQueueImplMixin {
    @Inject(
            method = "submitModel",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private <S extends EntityRenderState> void helium$suppressBatchedBody(
            Model<? super S> model,
            S state,
            MatrixStack matrices,
            RenderLayer renderLayer,
            int light,
            int overlay,
            int tintedColor,
            Sprite sprite,
            int outlineColor,
            ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay,
            CallbackInfo ci) {
        if (RentitiesBodyModelSuppression.consumeIfMatches(model, state)) {
            ci.cancel();
        }
    }
}
