package com.helium.mixin.render;

import com.helium.render.ShaderUniformCache;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenCacheMixin {

    @Inject(method = "<init>(Z)V", at = @At("TAIL"), require = 0)
    private void helium$invalidateshadercache(boolean doBackgroundFade, CallbackInfo ci) {
        ShaderUniformCache.invalidate();
    }
}
