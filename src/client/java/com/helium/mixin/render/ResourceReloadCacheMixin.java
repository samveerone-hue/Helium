package com.helium.mixin.render;

import com.helium.render.ShaderUniformCache;
import com.helium.render.TextRenderOptimizer;
import net.minecraft.client.resource.ResourceReloadLogger;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResourceReloadLogger.class)
public class ResourceReloadCacheMixin {

    @Inject(method = "finish", at = @At("HEAD"), require = 0)
    private void helium$invalidateshadercacheonreload(CallbackInfo ci) {
        ShaderUniformCache.invalidate();
        TextRenderOptimizer.invalidate();
    }
}


@Mixin(MinecraftClient.class)
class ResourceReloadTextCacheStartMixin {
    @Inject(
            method = "reloadResources()Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            require = 0
    )
    private void helium$invalidateTextCacheBeforeReload(
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<java.util.concurrent.CompletableFuture<Void>> cir) {
        TextRenderOptimizer.invalidate();
    }
}
