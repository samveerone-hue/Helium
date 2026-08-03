package com.helium.mixin.lang;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.lang.LanguageHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public abstract class LanguageReloadMixin {

    @Shadow
    @Final
    private LanguageManager languageManager;

    @Shadow
    public abstract ResourceManager getResourceManager();

    @Shadow
    @Nullable
    public net.minecraft.client.gui.screens.Screen currentScreen;

    @Inject(method = "reloadResources()Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$skipFullReloadForLanguage(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        helium$trySkipReload(cir);
    }

    @Inject(method = "setOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$cancelSplashOnLanguageChange(Overlay overlay, CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.instantLanguageChange) return;
        if (overlay instanceof LoadingOverlay && currentScreen instanceof LanguageSelectScreen) {
            ci.cancel();
        }
    }

    @Unique
    private void helium$trySkipReload(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.instantLanguageChange) return;

        if (LanguageHelper.shouldSkipFullReload()) {
            this.languageManager.onResourceManagerReload(this.getResourceManager());
            cir.setReturnValue(CompletableFuture.completedFuture(null));
        }
    }
}
