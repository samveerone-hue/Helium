package com.helium.mixin.render;

import com.helium.render.ShaderUniformCache;
import net.minecraft.client.ResourceLoadStateTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResourceLoadStateTracker.class)
public class ResourceReloadCacheMixin {

    @Inject(method = "finishReload", at = @At("HEAD"), require = 0)
    private void helium$invalidateshadercacheonreload(CallbackInfo ci) {
        ShaderUniformCache.invalidate();
    }
}
