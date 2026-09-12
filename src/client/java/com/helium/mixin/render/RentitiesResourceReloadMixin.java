package com.helium.mixin.render;

import com.helium.rentities.entities.EntityBatchRenderer;
import com.helium.rentities.entities.EntityGlTextureResolver;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Invalidates Rentities' GL texture-object/name caches whenever client resources reload. */
@Mixin(MinecraftClient.class)
public abstract class RentitiesResourceReloadMixin {
    @Inject(method = "reloadResources()Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"), require = 0)
    private void helium$invalidateRentitiesTextureCaches(CallbackInfoReturnable<?> cir) {
        EntityGlTextureResolver.invalidateCache();
        EntityBatchRenderer renderer = EntityBatchRenderer.INSTANCE;
        if (renderer != null) {
            renderer.entityGlTexIds.clear();
            renderer.entityTextureLocs.clear();
            renderer.entityTexFailed.clear();
        }
    }
}
