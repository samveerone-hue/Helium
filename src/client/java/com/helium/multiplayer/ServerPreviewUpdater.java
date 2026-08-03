package com.helium.multiplayer;

import net.minecraft.network.chat.Component;

public interface ServerPreviewUpdater {
    void helium$updateServerData(String name, String[] motd, String players, long ping);
    void helium$updateFavicon(byte[] favicon);
    void helium$setMotdText(Component motd);
}
