package com.helium.mixin.render;

import com.helium.render.DevModeOptimizer;
import com.helium.render.FrameLightCache;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndLightGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class LightmapCacheMixin {

    @Inject(
            method = "getLightCoords(Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/core/BlockPos;)I",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void helium$checkLightCache(BlockAndLightGetter world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!DevModeOptimizer.isLightCacheEnabled()) return;
        int cached = FrameLightCache.get(pos.asLong());
        if (cached != FrameLightCache.MISS) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(
            method = "getLightCoords(Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"),
            require = 0
    )
    private static void helium$storeLightCache(BlockAndLightGetter world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!DevModeOptimizer.isLightCacheEnabled()) return;
        int result = cir.getReturnValue();
        if (result != 0) {
            FrameLightCache.put(pos.asLong(), result);
        }
    }
}
