package com.helium.mixin.particle;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.compat.ExternalModCompat;
import com.helium.particle.ParticleLimiter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {

    @Unique
    private static final Map<Class<?>, Boolean> helium$lodClassCache = new ConcurrentHashMap<>();

    @Unique
    private static boolean helium$shouldApplyLod(Class<?> type) {
        if (type == null) return false;
        return helium$lodClassCache.computeIfAbsent(type, c -> {
            String name = c.getName();
            return !name.contains("AmbientEntityEffectParticle")
                    && !name.contains("BarrierParticle")
                    && !name.contains("TotemParticle");
        });
    }

    @Unique
    private static boolean helium$particleCullFailed = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void helium$prepareParticleFrame(CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null) {
            return;
        }

        if (config.particleLimiting && !ExternalModCompat.hasAsyncParticles() && !ParticleLimiter.isInitialized()) {
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
            if (config == null || !config.modEnabled || ExternalModCompat.hasAsyncParticles()) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && (config.particleCulling || config.particleLOD)) {
                var box = particle.getBoundingBox();
                double dx = ((box.minX + box.maxX) * 0.5) - client.player.getX();
                double dy = ((box.minY + box.maxY) * 0.5) - client.player.getY();
                double dz = ((box.minZ + box.maxZ) * 0.5) - client.player.getZ();
                double distanceSq = dx * dx + dy * dy + dz * dz;

                if (config.particleCulling) {
                    double cullDist = Math.max(0, config.particleCullDistance);
                    if (distanceSq > cullDist * cullDist) {
                        ci.cancel();
                        return;
                    }
                }

                if (config.particleLOD
                        && helium$shouldApplyLod(particle.getClass())) {
                    double threshold = Math.max(0.0, config.particleLODDistance);
                    double reduction = Math.max(0.0, Math.min(1.0, config.particleLODReduction));
                    if (reduction > 0.0
                            && distanceSq > threshold * threshold
                            && java.util.concurrent.ThreadLocalRandom.current().nextDouble() < reduction) {
                        ci.cancel();
                        return;
                    }
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
