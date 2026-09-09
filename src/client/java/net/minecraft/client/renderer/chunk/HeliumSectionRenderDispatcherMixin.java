package net.minecraft.client.renderer.chunk;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.AsyncChunkMeshing;
import com.helium.render.RenderBatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catalyst-inspired admission control for the native 26.1.2 chunk compile queue.
 */
@Mixin(SectionRenderDispatcher.class)
public abstract class HeliumSectionRenderDispatcherMixin {
    @Inject(method = "schedule", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$interceptCompileSchedule(SectionRenderDispatcher.RenderSection.CompileTask task,
                                                   CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.modEnabled || !config.renderPipelining) return;
        if (AsyncChunkMeshing.isBypassing()) return;

        if (!AsyncChunkMeshing.queue(task)) {
            // Queue full: preserve vanilla admission rather than dropping a rebuild.
            return;
        }

        RenderBatch.trackSection();
        ci.cancel();
    }
}
