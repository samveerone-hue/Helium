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
 * Catalyst-style bounded admission for Minecraft 26.1.2's native compile queue.
 *
 * <p>The mixin lives in the target package so the package-private CompileTask type
 * remains an implementation detail of Minecraft while Helium stores it opaquely.</p>
 */
@Mixin(SectionRenderDispatcher.class)
public abstract class HeliumSectionRenderDispatcherMixin {
    @Inject(method = "schedule", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$interceptCompileSchedule(
            SectionRenderDispatcher.RenderSection.CompileTask task,
            CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.modEnabled || !config.renderPipelining) return;
        if (AsyncChunkMeshing.isBypassing()) return;

        if (!AsyncChunkMeshing.queue(task)) {
            // Queue saturation must never drop a render rebuild.
            return;
        }

        RenderBatch.trackSection();
        ci.cancel();
    }
}
