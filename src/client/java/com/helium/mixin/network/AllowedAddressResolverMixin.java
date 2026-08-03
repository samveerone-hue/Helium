package com.helium.mixin.network;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.network.FastIpPingOptimizer;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.util.Optional;

@Mixin(ServerNameResolver.class)
public abstract class AllowedAddressResolverMixin {

    @Inject(method = "resolveAddress", at = @At("RETURN"), require = 0)
    private void helium$patchReverseDns(ServerAddress address, CallbackInfoReturnable<Optional<ResolvedServerAddress>> cir) {
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled || !config.fastIpPing) return;
            if (!FastIpPingOptimizer.isInitialized()) return;

            Optional<ResolvedServerAddress> result = cir.getReturnValue();
            if (result == null || result.isEmpty()) return;

            InetSocketAddress socketAddr = result.get().asInetSocketAddress();
            FastIpPingOptimizer.patchAddress(socketAddr);
        } catch (Throwable ignored) {}
    }
}
