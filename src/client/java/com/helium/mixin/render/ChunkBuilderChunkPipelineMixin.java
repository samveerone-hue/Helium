package com.helium.mixin.render;

import com.helium.HeliumClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.Vec3d;
import com.helium.render.AsyncChunkMeshing;
import com.helium.render.RenderBatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the Catalyst-style chunk queues synchronized with vanilla camera/lifecycle state. */
@Mixin(ChunkBuilder.class)
public abstract class ChunkBuilderChunkPipelineMixin {

    @Inject(method = "setCameraPosition", at = @At("TAIL"), require = 0)
    private void helium$updateCamera(Vec3d position, CallbackInfo ci) {
        if (HeliumClient.getConfig() != null && HeliumClient.getConfig().renderPipelining) {
            AsyncChunkMeshing.updateCamera(position);
        }
    }

    @Inject(method = "stop", at = @At("HEAD"), require = 0)
    private void helium$clearQueues(CallbackInfo ci) {
        AsyncChunkMeshing.clear();
        RenderBatch.clear();
    }
}
