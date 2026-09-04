package com.helium.mixin.render;

import com.helium.render.DevModeOptimizer;
import com.helium.render.FrameLightCache;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public abstract class LightmapCacheMixin {
    @Inject(method = "getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void helium$checkLightCache(BlockRenderView world, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!DevModeOptimizer.isLightCacheEnabled()) return;
        int cached = FrameLightCache.get(pos.asLong());
        if (cached != FrameLightCache.MISS) cir.setReturnValue(cached);
    }
    @Inject(method = "getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I",
            at = @At("RETURN"), require = 0)
    private static void helium$storeLightCache(BlockRenderView world, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!DevModeOptimizer.isLightCacheEnabled()) return;
        int result = cir.getReturnValue();
        if (result != 0) FrameLightCache.put(pos.asLong(), result);
    }
}