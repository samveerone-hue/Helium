package com.helium.mixin.tweaks;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.tweaks.AsyncPackReloader;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public class ResourceReloadMixin {

    @Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$asyncreload(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.modEnabled || !config.asyncPackReload) return;

        AsyncPackReloader.reloadasync();
        cir.setReturnValue(CompletableFuture.completedFuture(null));
    }
}
