package com.helium.mixin.render;

import com.helium.render.ShaderUniformCache;
import com.helium.render.TextRenderOptimizer;
import net.minecraft.client.resource.ResourceReloadLogger;
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
