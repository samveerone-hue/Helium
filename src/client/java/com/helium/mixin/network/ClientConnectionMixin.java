package com.helium.mixin.network;

import com.helium.HeliumClient;
import com.helium.config.ExperimentalConfig;
import com.helium.network.BufferOptimizer;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {

    @Unique
    private long helium$tickCounter = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void helium$optimizeBuffers(CallbackInfo ci) {
        ExperimentalConfig experimental = ExperimentalConfig.load();
        if (experimental.networkOptimizations) {
            BufferOptimizer.tick(helium$tickCounter++);
        }
    }

    /**
     * Defer the final Netty flush by one connection tick when packet batching is enabled.
     * Packet encoding and ordering remain vanilla; only the flush boundary is coalesced.
     */
    @ModifyVariable(
            method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;Z)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private boolean helium$batchFlushes(boolean flush) {
        if (!flush) return false;
        ExperimentalConfig experimental = ExperimentalConfig.load();
        return !experimental.packetBatching;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void helium$flushBatchedPackets(CallbackInfo ci) {
        ExperimentalConfig experimental = ExperimentalConfig.load();
        if (!experimental.packetBatching) return;
        try {
            ((ClientConnection) (Object) this).flush();
        } catch (Throwable t) {
            HeliumClient.LOGGER.debug("packet batch flush failed: {}", t.toString());
        }
    }
}
