package com.helium.mixin.hotbar;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.hotbar.HotbarOptimizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public abstract class HotbarKeybindingMixin {

    @Inject(method = "setDown", at = @At("HEAD"), require = 0)
    private void helium$onhotbarkeypressed(boolean pressed, CallbackInfo ci) {
        if (!pressed) return;

        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.hotbarOptimizer) return;

        KeyMapping bind = (KeyMapping) (Object) this;
        String key = bind.getName();
        if (!key.startsWith("key.hotbar.")) return;

        Minecraft client = Minecraft.getInstance();
        if (client.isLocalServer()) return;
        if (client.gameMode == null) return;

        LocalPlayer player = client.player;
        if (player == null || player.hasInfiniteMaterials()) return;

        try {
            int slot = Integer.parseInt(key.substring("key.hotbar.".length())) - 1;
            if (slot < 0 || slot > 8) return;
            HotbarOptimizer.syncslot(client, slot);
        } catch (NumberFormatException ignored) {}
    }
}
