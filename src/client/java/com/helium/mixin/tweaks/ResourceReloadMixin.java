package com.helium.mixin.tweaks;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.tweaks.AsyncPackReloader;
import com.helium.render.TextRenderOptimizer;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftClient.class)
public class ResourceReloadMixin {

    @Inject(method = "reloadResources()Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$asyncreload(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        TextRenderOptimizer.invalidate();
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.modEnabled || !config.asyncPackReload) return;

        AsyncPackReloader.reloadasync();
        cir.setReturnValue(CompletableFuture.completedFuture(null));
    }
}
