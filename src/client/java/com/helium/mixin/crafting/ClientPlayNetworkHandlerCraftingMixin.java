package com.helium.mixin.crafting;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.crafting.OneClickCraftingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerCraftingMixin {

    @Inject(at = @At("TAIL"), method = "onScreenHandlerSlotUpdate(Lnet/minecraft/network/protocol/game/ClientboundContainerSetSlotPacket;)V", require = 0)
    private void helium$onScreenHandlerSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.oneClickCrafting) return;
        if (!OneClickCraftingManager.isinitialized()) return;

        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            OneClickCraftingManager.reset();
            return;
        }
        if (screen instanceof CraftingScreen || screen instanceof InventoryScreen) {
            if (packet.getSlot() == 0 && packet.getItem() != null) {
                OneClickCraftingManager.onresultslotupdated(packet.getItem());
            }
        } else if (screen instanceof StonecutterScreen) {
            if (packet.getSlot() == 1 && packet.getItem() != null) {
                OneClickCraftingManager.onstonecutterresultupdated(packet.getItem());
            }
        }
    }
}
