package com.helium.mixin.compat;

import com.helium.render.ShaderUniformCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.Iris", remap = false)
public class IrisShaderCacheMixin {

    @Inject(method = "reload", at = @At("HEAD"), require = 0, remap = false)
    private static void helium$invalidateonshadereload(CallbackInfo ci) {
        ShaderUniformCache.invalidate();
    }
}
