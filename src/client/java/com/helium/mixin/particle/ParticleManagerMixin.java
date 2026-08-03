package com.helium.mixin.particle;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.particle.ParticleBatcher;
import com.helium.particle.ParticleLimiter;
import com.helium.threading.ParticleWorkerPool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.helium.util.VersionMethodResolver;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

@Mixin(ParticleEngine.class)
public abstract class ParticleManagerMixin {

    @Unique
    private int helium$particleAddCount = 0;

    @Unique
    private static boolean helium$particleCullFailed = false;

    @Unique
    private static boolean helium$lodFailed = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void helium$initParticlePool(CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null) return;

        if (config.threadOptimizations && !ParticleWorkerPool.isInitialized()) {
            ParticleWorkerPool.init(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
        }

        if (config.particleLimiting && !ParticleLimiter.isInitialized()) {
            ParticleLimiter.init(config.maxParticles);
        }

        if (config.particleBatching && !ParticleBatcher.isInitialized()) {
            ParticleBatcher.init();
        }

        if (ParticleBatcher.isInitialized()) {
            ParticleBatcher.tick();
        }

        if (config.particleLimiting && ParticleLimiter.isInitialized()) {
            ParticleLimiter.setParticleCount(helium$particleAddCount);
        }

        helium$particleAddCount = Math.max(0, helium$particleAddCount - 20);
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void helium$applyParticleLODOnTick(CallbackInfo ci) {
        if (helium$lodFailed) return;

        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.particleLOD) return;

            Minecraft client = Minecraft.getInstance();
            if (client.gameRenderer == null) return;

            Camera camera = client.gameRenderer.getMainCamera();
            if (camera == null) return;

            net.minecraft.world.phys.Vec3 camPos = com.helium.util.VersionCompat.getCameraPosition(camera);
            double threshold = config.particleLODDistance;
            double thresholdSq = threshold * threshold;

            helium$applylodtoparticles(camPos, thresholdSq, config.particleLODReduction);
        } catch (Throwable t) {
            if (!helium$lodFailed) {
                helium$lodFailed = true;
                HeliumClient.LOGGER.warn("particle LOD disabled ({})", t.getClass().getSimpleName());
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Unique
    private void helium$applylodtoparticles(net.minecraft.world.phys.Vec3 camPos, double thresholdSq, double reduction) {
        Field mapField = VersionMethodResolver.particlesmapfield();
        if (mapField == null) return;

        try {
            Map<?, ?> particlesMap = (Map<?, ?>) mapField.get(this);
            if (particlesMap == null || particlesMap.isEmpty()) return;

            List<Particle> toKill = new ArrayList<>();
            boolean modernApi = VersionMethodResolver.hasmodernparticlerenderer();
            MethodHandle getParticles = VersionMethodResolver.getparticlesfromrenderer();

            for (Object value : particlesMap.values()) {
                Collection<? extends Particle> particleCollection = null;

                if (modernApi && getParticles != null) {
                    try {
                        Queue<?> queue = (Queue<?>) getParticles.invoke(value);
                        if (queue != null) {
                            particleCollection = (Collection<? extends Particle>) queue;
                        }
                    } catch (Throwable ignored) {}
                }

                if (particleCollection == null && value instanceof Queue) {
                    particleCollection = (Queue<Particle>) value;
                }

                if (particleCollection == null) continue;

                for (Particle particle : particleCollection) {
                    if (!helium$shouldApplyLOD(particle)) continue;

                    double dx = particle.getBoundingBox().getCenter().x - camPos.x;
                    double dy = particle.getBoundingBox().getCenter().y - camPos.y;
                    double dz = particle.getBoundingBox().getCenter().z - camPos.z;
                    double distSq = dx * dx + dy * dy + dz * dz;

                    if (distSq > thresholdSq) {
                        if (ThreadLocalRandom.current().nextDouble() > reduction) {
                            toKill.add(particle);
                        }
                    }
                }
            }

            for (Particle p : toKill) {
                p.remove();
            }
        } catch (Throwable t) {
            helium$lodFailed = true;
            HeliumClient.LOGGER.warn("particle LOD iteration failed ({})", t.getClass().getSimpleName());
        }
    }

    @Unique
    private boolean helium$shouldApplyLOD(Particle particle) {
        String name = particle.getClass().getName().toLowerCase();
        return name.contains("rain") || name.contains("snow") ||
                name.contains("cloud") || name.contains("ash") ||
                name.contains("drip") || name.contains("spore") ||
                name.contains("smoke") || name.contains("dust");
    }

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void helium$cullDistantParticles(Particle particle, CallbackInfo ci) {
        try {
            helium$cullDistantParticlesInternal(particle, ci);
        } catch (Throwable t) {
            if (!helium$particleCullFailed) {
                helium$particleCullFailed = true;
                HeliumClient.LOGGER.warn("particle culling disabled on this mc version ({})", t.getClass().getSimpleName());
            }
        }
    }

    @Unique
    private void helium$cullDistantParticlesInternal(Particle particle, CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.modEnabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        boolean doCulling = config.particleCulling;
        boolean doLimiting = config.particleLimiting;
        boolean doBatching = config.particleBatching;

        if (doCulling) {
            int cullDist = config.particleCullDistance;
            double px = client.player.getX();
            double py = client.player.getY();
            double pz = client.player.getZ();
            double dx = particle.getBoundingBox().getCenter().x - px;
            double dy = particle.getBoundingBox().getCenter().y - py;
            double dz = particle.getBoundingBox().getCenter().z - pz;
            double dist = dx * dx + dy * dy + dz * dz;
            double maxDist = cullDist * cullDist;
            if (dist > maxDist) {
                ci.cancel();
                return;
            }
        }

        if (doLimiting && ParticleLimiter.isInitialized() && !ParticleLimiter.canAddParticle(particle)) {
            ci.cancel();
            return;
        }

        helium$particleAddCount++;

        if (doBatching && ParticleBatcher.isInitialized()) {
            ParticleBatcher.recordParticleType(particle.getClass().getSimpleName());
        }
    }
}
