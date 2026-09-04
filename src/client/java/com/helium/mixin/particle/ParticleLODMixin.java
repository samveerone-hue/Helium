package com.helium.mixin.particle;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(Particle.class)
public abstract class ParticleLODMixin {

    @Unique
    private static boolean helium$lodFailed = false;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$applyParticleLODModern(CallbackInfo ci) {
        if (helium$lodFailed || !com.helium.mixin.particle.ParticleManagerMixin.helium$isLodEnabled()) {
            return;
        }

        try {
            HeliumConfig config = HeliumClient.getConfig();
            Particle self = (Particle) (Object) this;

            if (!helium$shouldApplyLOD(self, config)) {
                return;
            }

            double dx = self.getBoundingBox().getCenter().x
                    - ParticleManagerMixin.helium$getCameraX();
            double dy = self.getBoundingBox().getCenter().y
                    - ParticleManagerMixin.helium$getCameraY();
            double dz = self.getBoundingBox().getCenter().z
                    - ParticleManagerMixin.helium$getCameraZ();

            double threshold = Math.max(0.0, config.particleLODDistance);
            double thresholdSq = threshold * threshold;

            if (dx * dx + dy * dy + dz * dz > thresholdSq
                    && ThreadLocalRandom.current().nextDouble() > config.particleLODReduction) {
                self.markDead();
                ci.cancel();
            }
        } catch (Throwable t) {
            if (!helium$lodFailed) {
                helium$lodFailed = true;
                HeliumClient.LOGGER.warn(
                        "particle LOD disabled ({})",
                        t.getClass().getSimpleName()
                );
            }
        }
    }

    @Unique
    private static boolean helium$shouldApplyLOD(
            Particle particle,
            HeliumConfig config
    ) {
        if (config == null) {
            return false;
        }

        // Keep the same conservative particle families as the previous
        // implementation, but classify by the concrete class only once.
        return ParticleLodClassifier.shouldApply(particle.getClass());
    }
}

final class ParticleLodClassifier {
    private ParticleLodClassifier() {}

    private static final ClassValue<Boolean> LOD_TYPES = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            String name = type.getName().toLowerCase(java.util.Locale.ROOT);
            return name.contains("rain") || name.contains("snow")
                    || name.contains("cloud") || name.contains("ash")
                    || name.contains("drip") || name.contains("spore")
                    || name.contains("smoke") || name.contains("dust");
        }
    };

    static boolean shouldApply(Class<?> type) {
        return LOD_TYPES.get(type);
    }
}
