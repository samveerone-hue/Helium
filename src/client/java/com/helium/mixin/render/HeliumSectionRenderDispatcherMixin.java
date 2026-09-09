package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.AsyncChunkMeshing;
import com.helium.render.RenderBatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bounds admission to Minecraft 26.1.2's native section compile queue without
 * replacing its CompileTask priority model.
 */
@Mixin(SectionRenderDispatcher.class)
public abstract class HeliumSectionRenderDispatcherMixin {
    @Inject(method = "schedule", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$interceptCompileSchedule(
            Object task,
            CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.modEnabled || !config.renderPipelining) return;
        if (AsyncChunkMeshing.isBypassing()) return;

        if (!AsyncChunkMeshing.queue(task)) {
            return; // queue full: preserve vanilla scheduling
        }

        RenderBatch.trackSection();
        ci.cancel();
    }
}
