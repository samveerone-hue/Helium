package com.helium.mixin.render;

import com.helium.rentities.RendererCapabilityState;
import com.helium.rentities.entities.EntityBatchRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Converts fatal GPU batch-flush failures into a persistent vanilla fallback. */
@Mixin(EntityBatchRenderer.class)
public abstract class EntityBatchRendererHealthMixin {
    @Redirect(
            method = "flushBatch",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/helium/rentities/entities/EntityBatchRenderer;doFlush()V"
            )
    )
    private static void helium$safeFlush(EntityBatchRenderer renderer) {
        try {
            ((EntityBatchRendererInvoker) renderer).helium$invokeDoFlush();
        } catch (Throwable t) {
            RendererCapabilityState caps = RendererCapabilityState.current();
            if (caps != null) caps.markFailed(RendererCapabilityState.Feature.GPU_BATCHING, t);
        }
    }
}
