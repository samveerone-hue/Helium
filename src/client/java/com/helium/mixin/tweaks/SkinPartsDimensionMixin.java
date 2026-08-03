package com.helium.mixin.tweaks;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class SkinPartsDimensionMixin {

    @Inject(method = "setWorld(Lnet/minecraft/client/multiplayer/ClientLevel;)V", at = @At("RETURN"), require = 0)
    private void helium$refreshskinondimchange(ClientLevel world, CallbackInfo ci) {
        if (world == null) return;

        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.forceSkinParts) return;

        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null) return;

        mc.options.broadcastOptions();
    }
}
