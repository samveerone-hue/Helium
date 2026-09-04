package com.helium.mixin.particle;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.particle.ParticleLimiter;
import com.helium.particle.ParticleLODState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {

    @Unique
    private static boolean helium$particleCullFailed = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void helium$prepareParticleFrame(CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null) {
            helium$cameraReady = false;
            return;
        }

        if (config.particleLOD) {
            ParticleLODState.prepare(config);
        } else {
            ParticleLODState.prepare(null);
        }

        if (config.particleLimiting && !ParticleLimiter.isInitialized()) {
            ParticleLimiter.init(config.maxParticles);
        }
    }

    @Inject(
            method = "addParticle(Lnet/minecraft/client/particle/Particle;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void helium$cullDistantParticles(Particle particle, CallbackInfo ci) {
        if (helium$particleCullFailed) {
            return;
        }

        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (config.particleCulling && client.player != null) {
                int cullDist = Math.max(0, config.particleCullDistance);
                var box = particle.getBoundingBox();
                double dx = ((box.minX + box.maxX) * 0.5) - client.player.getX();
                double dy = ((box.minY + box.maxY) * 0.5) - client.player.getY();
                double dz = ((box.minZ + box.maxZ) * 0.5) - client.player.getZ();
                double maxDistSq = (double) cullDist * cullDist;

                if (dx * dx + dy * dy + dz * dz > maxDistSq) {
                    ci.cancel();
                    return;
                }
            }

            if (config.particleLimiting
                    && ParticleLimiter.isInitialized()
                    && !ParticleLimiter.canAddParticle(particle)) {
                ci.cancel();
                return;
            }

            if (ParticleLimiter.isInitialized()) {
                ParticleLimiter.onParticleAdded();
            }
        } catch (Throwable t) {
            helium$particleCullFailed = true;
            HeliumClient.LOGGER.warn(
                    "particle culling disabled on this Minecraft version ({})",
                    t.getClass().getSimpleName()
            );
        }
    }

}
