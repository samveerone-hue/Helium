package com.helium.mixin.render;

import com.helium.rentities.entities.EntityBatchRenderer;
import com.helium.rentities.entities.RentitiesEquipmentBatcher;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks the equipment batch into the same frame lifecycle as the body batch. */
@Mixin(targets = "com.helium.rentities.entities.EntityBatchRenderer")
public abstract class EntityBatchRendererEquipmentMixin {
    @Inject(method = "beginWorldRender(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", at = @At("HEAD"), require = 0)
    private static void helium$resetEquipment(Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        RentitiesEquipmentBatcher.resetFrame();
    }

    @Inject(method = "flushBatch()V", at = @At("TAIL"), require = 0)
    private static void helium$flushEquipment(CallbackInfo ci) {
        RentitiesEquipmentBatcher.flush(EntityBatchRenderer.storedViewProjection);
    }
}
