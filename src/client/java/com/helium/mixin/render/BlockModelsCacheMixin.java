package com.helium.mixin.render;

import com.helium.config.ExperimentalConfig;
import com.helium.render.ModelCache;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.model.BlockStateModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(BlockModels.class)
public abstract class BlockModelsCacheMixin {
    private static boolean helium$enabled() {
        try {
            return ExperimentalConfig.load().modelCache;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void helium$cacheGet(BlockState state, CallbackInfoReturnable<BlockStateModel> cir) {
        if (!helium$enabled()) return;
        if (!ModelCache.isInitialized()) {
            ModelCache.init(ExperimentalConfig.load().modelCacheMaxMb);
        }
        BlockStateModel cached = ModelCache.get(state);
        if (cached != null) cir.setReturnValue(cached);
    }

    @Inject(method = "getModel", at = @At("RETURN"))
    private void helium$cachePut(BlockState state, CallbackInfoReturnable<BlockStateModel> cir) {
        if (!helium$enabled() || cir.getReturnValue() == null) return;
        if (!ModelCache.isInitialized()) {
            ModelCache.init(ExperimentalConfig.load().modelCacheMaxMb);
        }
        ModelCache.put(state, cir.getReturnValue());
    }

    @Inject(method = "setModels", at = @At("HEAD"))
    private void helium$invalidateOnReload(Map<BlockState, BlockStateModel> models, CallbackInfo ci) {
        ModelCache.invalidateAll();
    }
}
