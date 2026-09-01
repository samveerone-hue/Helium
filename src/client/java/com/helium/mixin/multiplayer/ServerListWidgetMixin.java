package com.helium.mixin.multiplayer;

import com.helium.HeliumClient;
import com.helium.compat.ExternalModCompat;
import com.helium.config.HeliumConfig;
import com.helium.network.FastServerPingHelper;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

@Mixin(MultiplayerServerListWidget.class)
public abstract class ServerListWidgetMixin {

    @Shadow
    @Mutable
    @Final
    private static ThreadPoolExecutor SERVER_PINGER_THREAD_POOL;

    @Shadow
    @Final
    private List<MultiplayerServerListWidget.ServerEntry> servers;

    @Unique
    private static boolean helium$poolInitialized = false;

    @Unique
    private static int helium$lastServerCount = -1;

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void helium$initPingerPool(CallbackInfo ci) {
        if (!ExternalModCompat.shouldUseHeliumServerPings()) return;

        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.modEnabled || !config.fastServerPing) return;

        int serverCount = servers != null ? servers.size() : 0;
        helium$ensurePool(serverCount);
    }

    @Inject(method = "updateEntries", at = @At("HEAD"), require = 0)
    private void helium$onUpdateEntries(CallbackInfo ci) {
        if (!ExternalModCompat.shouldUseHeliumServerPings()) return;

        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.modEnabled || !config.fastServerPing) return;

        int serverCount = servers != null ? servers.size() : 0;
        helium$ensurePool(serverCount);
    }

    @Unique
    private void helium$ensurePool(int serverCount) {
        if (helium$poolInitialized && helium$lastServerCount == serverCount
                && SERVER_PINGER_THREAD_POOL != null
                && !SERVER_PINGER_THREAD_POOL.isShutdown()) {
            return;
        }

        int threads = FastServerPingHelper.threadCount(serverCount);
        if (helium$poolInitialized
                && SERVER_PINGER_THREAD_POOL != null
                && !SERVER_PINGER_THREAD_POOL.isShutdown()
                && SERVER_PINGER_THREAD_POOL.getCorePoolSize() == threads) {
            helium$lastServerCount = serverCount;
            return;
        }

        if (SERVER_PINGER_THREAD_POOL != null) {
            SERVER_PINGER_THREAD_POOL.shutdownNow();
        }

        SERVER_PINGER_THREAD_POOL = FastServerPingHelper.createExecutor(serverCount);
        helium$poolInitialized = true;
        helium$lastServerCount = serverCount;
    }
}
