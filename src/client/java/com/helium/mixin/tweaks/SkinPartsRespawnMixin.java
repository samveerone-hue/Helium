package com.helium.mixin.tweaks;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class SkinPartsRespawnMixin {

    @Unique
    private boolean helium$dead = false;

    @Inject(method = "onPlayerRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V", at = @At("RETURN"), require = 0)
    private void helium$refreshskinonrespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        if (!helium$dead) return;
        helium$dead = false;

        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.forceSkinParts) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.options.broadcastOptions();
    }

    @Inject(method = "onDeathMessage(Lnet/minecraft/network/protocol/game/ClientboundPlayerCombatKillPacket;)V", at = @At("RETURN"), require = 0)
    private void helium$trackdeath(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            helium$dead = true;
        }
    }
}
