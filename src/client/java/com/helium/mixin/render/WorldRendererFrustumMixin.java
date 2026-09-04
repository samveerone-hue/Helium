package com.helium.mixin.render;

import com.helium.render.CullingHelper;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererFrustumMixin {
    @Inject(method = "applyFrustum", at = @At("HEAD"), require = 0)
    private void helium$captureFrustumAtApply(Frustum frustum, CallbackInfo ci) {
        CullingHelper.setfrustum(frustum);
    }
}