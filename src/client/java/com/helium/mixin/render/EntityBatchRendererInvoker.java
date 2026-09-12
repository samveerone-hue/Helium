package com.helium.mixin.render;

import com.helium.rentities.entities.EntityBatchRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Mixin bridge for invoking the private batch flush from the safety wrapper. */
@Mixin(EntityBatchRenderer.class)
public interface EntityBatchRendererInvoker {
    @Invoker("doFlush")
    void helium$invokeDoFlush();
}
