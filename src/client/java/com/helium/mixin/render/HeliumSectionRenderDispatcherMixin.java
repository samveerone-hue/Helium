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
 * Bounds admission to Minecraft's native section compile queue without taking
 * ownership of the version-specific SectionTask/CompileTask implementation.
 *
 * <p>26.2 changed the internal task type from CompileTask to SectionTask. The
 * hook therefore deliberately accepts the task as Object and lets the queue
 * adapter invoke the native schedule method reflectively.</p>
 */
@Mixin(SectionRenderDispatcher.class)
public abstract class HeliumSectionRenderDispatcherMixin {
    @Inject(method = "schedule", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$interceptCompileSchedule(Object task, CallbackInfo ci) {
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
