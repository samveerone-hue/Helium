package com.helium.mixin.particle;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.compat.ExternalModCompat;
import com.helium.particle.ParticleBatcher;
import com.helium.particle.ParticleLimiter;
import com.helium.threading.ParticleWorkerPool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleManagerMixin {
    @Unique private int helium$particleAddCount;
    @Unique private static boolean helium$particleCullFailed;

    @Inject(method = "tick", at = @At("HEAD"))
    private void helium$initParticlePool(CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || ExternalModCompat.hasAsyncParticles()) return;
        if (config.threadOptimizations && !ParticleWorkerPool.isInitialized()) {
            ParticleWorkerPool.init(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
        }
        if (config.particleLimiting && !ParticleLimiter.isInitialized()) ParticleLimiter.init(config.maxParticles);
        if (config.particleBatching && !ParticleBatcher.isInitialized()) ParticleBatcher.init();
        if (ParticleBatcher.isInitialized()) ParticleBatcher.tick();
        if (config.particleLimiting && ParticleLimiter.isInitialized()) ParticleLimiter.setParticleCount(helium$particleAddCount);
        helium$particleAddCount = Math.max(0, helium$particleAddCount - 20);
    }

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void helium$cullDistantParticles(Particle particle, CallbackInfo ci) {
        try { helium$cullDistantParticlesInternal(particle, ci); }
        catch (Throwable t) {
            if (!helium$particleCullFailed) {
                helium$particleCullFailed = true;
                HeliumClient.LOGGER.warn("particle culling disabled on this mc version ({})", t.getClass().getSimpleName());
            }
        }
    }

    @Unique
    private void helium$cullDistantParticlesInternal(Particle particle, CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.modEnabled || ExternalModCompat.hasAsyncParticles()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        if (config.particleCulling) {
            double dx = particle.getBoundingBox().getCenter().x - client.player.getX();
            double dy = particle.getBoundingBox().getCenter().y - client.player.getY();
            double dz = particle.getBoundingBox().getCenter().z - client.player.getZ();
            double max = config.particleCullDistance;
            if (dx * dx + dy * dy + dz * dz > max * max) {
                ci.cancel();
                return;
            }
        }
        if (config.particleLimiting && ParticleLimiter.isInitialized() && !ParticleLimiter.canAddParticle(particle)) {
            ci.cancel();
            return;
        }
        helium$particleAddCount++;
        if (config.particleBatching && ParticleBatcher.isInitialized()) {
            ParticleBatcher.recordParticleType(particle.getClass().getSimpleName());
        }
    }
}
