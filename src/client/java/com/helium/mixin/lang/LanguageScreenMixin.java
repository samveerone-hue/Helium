package com.helium.mixin.lang;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.lang.LanguageHelper;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LanguageSelectScreen.class)
public abstract class LanguageScreenMixin {

    @Inject(
            method = "onDone",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;reloadResources()Ljava/util/concurrent/CompletableFuture;"
            ),
            require = 0
    )
    private void helium$beforelanguagereload(CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config != null && config.instantLanguageChange) {
            LanguageHelper.setSkipFullReload(true);
        }
    }
}
