package com.helium.mixin.multiplayer;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MultiplayerScreenMixin {
    @Shadow protected ServerSelectionList serverSelectionList;

    @Redirect(method = "refreshServerList", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void helium$preserveScrollOnRefresh(net.minecraft.client.gui.Gui gui, Screen newScreen) {
        HeliumConfig config = HeliumClient.getConfig();
        Minecraft client = Minecraft.getInstance();
        if (config == null || !config.modEnabled || !config.preserveScrollOnRefresh
                || !(newScreen instanceof JoinMultiplayerScreen) || this.serverSelectionList == null) {
            gui.setScreen(newScreen);
            return;
        }

        double scrollY = this.serverSelectionList.scrollAmount();
        gui.setScreen(newScreen);
        ServerSelectionList newWidget = ((MultiplayerScreenAccessor) newScreen).helium$getServerListWidget();
        if (newWidget != null) newWidget.setScrollAmount(scrollY);
    }
}
