package com.helium.mixin.render;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Invokes vanilla's 1.21.11 chunk-render scheduler without reflection. */
@Mixin(WorldRenderer.class)
public interface WorldRendererInvoker {
    @Invoker("scheduleChunkRender")
    void helium$invokeScheduleChunkRender(int x, int y, int z, boolean important);
}
