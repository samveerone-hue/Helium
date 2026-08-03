package com.helium.mixin.multiplayer;

import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(JoinMultiplayerScreen.class)
public interface MultiplayerScreenAccessor {

    @Accessor("serverListWidget")
    ServerSelectionList helium$getServerListWidget();
}
