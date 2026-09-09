package com.helium.mixin.render;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface WorldRendererInvoker {
    @Invoker("scheduleChunkRender")
    void helium$invokeScheduleChunkRender(int x, int y, int z, boolean important);
}
