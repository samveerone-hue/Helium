package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.AsyncChunkMeshing;
import com.helium.render.RenderBatch;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11-only chunk scheduling hook based on Catalyst's RenderBatch/AsyncMeshing design.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererChunkPipelineMixin {

    @Unique
    private static boolean helium$enabled() {
        HeliumConfig config = HeliumClient.getConfig();
        return config != null && config.renderPipelining;
    }

    @Inject(method = "scheduleChunkRender(IIIZ)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$interceptChunkRender(int x, int y, int z, boolean important, CallbackInfo ci) {
        if (!helium$enabled()) return;
        if (AsyncChunkMeshing.isBypassing() || RenderBatch.isBypassing()) return;

        AsyncChunkMeshing.queue(x, y, z, important);
        RenderBatch.trackSection();
        ci.cancel();
    }

    @Inject(method = "reload()V", at = @At("HEAD"), require = 0)
    private void helium$clearOnReload(CallbackInfo ci) {
        AsyncChunkMeshing.clear();
        RenderBatch.clear();
    }

    @Inject(method = "setWorld", at = @At("HEAD"), require = 0)
    private void helium$clearOnWorldChange(net.minecraft.client.world.ClientWorld world, CallbackInfo ci) {
        AsyncChunkMeshing.clear();
        RenderBatch.clear();
    }
}
