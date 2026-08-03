package com.helium.mixin.render;

import com.helium.render.DevModeOptimizer;
import com.helium.render.FrameLightCache;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class LightmapCacheMixin {

    @Inject(
            method = "getLightmapCoordinates(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void helium$checkLightCache(BlockAndTintGetter world, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!DevModeOptimizer.isLightCacheEnabled()) return;
        int cached = FrameLightCache.get(pos.asLong());
        if (cached != FrameLightCache.MISS) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(
            method = "getLightmapCoordinates(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"),
            require = 0
    )
    private static void helium$storeLightCache(BlockAndTintGetter world, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!DevModeOptimizer.isLightCacheEnabled()) return;
        int result = cir.getReturnValue();
        if (result != 0) {
            FrameLightCache.put(pos.asLong(), result);
        }
    }
}
