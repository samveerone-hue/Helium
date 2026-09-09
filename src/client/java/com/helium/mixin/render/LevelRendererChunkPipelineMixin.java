package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.AsyncChunkMeshing;
import com.helium.render.RenderBatch;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererChunkPipelineMixin {
    @Unique
    private static boolean helium$enabled() {
        HeliumConfig config = HeliumClient.getConfig();
        return config != null && config.modEnabled && config.renderPipelining;
    }

    @Inject(method = "scheduleChunkRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$interceptChunkRender(int x, int y, int z, boolean important, CallbackInfo ci) {
        if (!helium$enabled() || AsyncChunkMeshing.isBypassing()) return;
        if (!AsyncChunkMeshing.queue(x, y, z, important)) return;
        RenderBatch.trackSection();
        ci.cancel();
    }

    @Inject(method = "setLevel", at = @At("HEAD"), require = 0)
    private void helium$clearOnWorldChange(ClientLevel level, CallbackInfo ci) {
        AsyncChunkMeshing.clear();
        RenderBatch.clear();
    }
}
