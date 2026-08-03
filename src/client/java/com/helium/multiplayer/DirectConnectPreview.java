package com.helium.multiplayer;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.network.chat.Component;

import java.net.UnknownHostException;

public final class DirectConnectPreview {

    private static String _lastAddress = "";
    private static long _lastPingTime = 0;
    private static final long DEBOUNCE_MS = 500;

    private DirectConnectPreview() {}

    public static void onAddressChanged(String address) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.modEnabled || !config.directConnectPreview) {
            return;
        }

        if (address == null || address.isBlank()) return;

        long now = System.currentTimeMillis();
        if (address.equals(_lastAddress) && now - _lastPingTime < DEBOUNCE_MS) {
            return;
        }

        _lastAddress = address;
        _lastPingTime = now;

        Thread.startVirtualThread(() -> pingServer(address));
    }

    private static void pingServer(String address) {
        ServerData info = new ServerData(address, address, ServerData.Type.OTHER);
        ServerStatusPinger pinger = new ServerStatusPinger();

        try {
            pinger.pingServer(info, () -> {}, () -> {
                dispatchResult(info);
            }, EventLoopGroupHolder.remote(true));
        } catch (UnknownHostException e) {
            info.motd = Component.literal("Unknown host");
            info.status = Component.literal("0/0");
            info.setState(ServerData.State.UNREACHABLE);
            dispatchResult(info);
        }
    }

    private static void dispatchResult(ServerData info) {
        if (info == null) return;

        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            Screen screen = client.screen;
            if (screen instanceof ServerPreviewUpdater updater) {
                if (info.motd != null) {
                    updater.helium$setMotdText(info.motd);
                } else {
                    updater.helium$setMotdText(Component.empty());
                }

                String[] motdLines = info.motd != null ? info.motd.getString().split("\n") : new String[]{""};
                updater.helium$updateServerData(
                        info.name != null ? info.name : "",
                        motdLines,
                        info.status != null ? info.status.getString() : "0/0",
                        info.ping
                );

                updater.helium$updateFavicon(info.getIconBytes());
            }
        });
    }
}
