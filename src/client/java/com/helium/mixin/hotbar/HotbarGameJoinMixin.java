package com.helium.mixin.hotbar;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.hotbar.HotbarOptimizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class HotbarGameJoinMixin {

    @Inject(method = "onGameJoin", at = @At("TAIL"), require = 0)
    private void helium$warnhotbardisabled(ClientboundLoginPacket packet, CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.hotbarOptimizer) return;
        if (!HotbarOptimizer.isserverdisabled()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        client.player.sendSystemMessage(
                Component.literal("[Helium] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Hotbar Optimizer disabled on this server.").withStyle(ChatFormatting.RED)));
    }
}
