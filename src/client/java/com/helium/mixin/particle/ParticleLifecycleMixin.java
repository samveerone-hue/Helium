package com.helium.mixin.particle;

import com.helium.particle.ParticleLimiter;
import com.helium.compat.ExternalModCompat;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps Helium's particle limiter synchronized with the real particle lifetime.
 * The removal hook is idempotent because particles may be marked dead more than once.
 */
@Mixin(Particle.class)
public abstract class ParticleLifecycleMixin {

    @Unique
    private boolean helium$limiterRemovalRecorded;

    @Inject(method = "markDead", at = @At("HEAD"))
    private void helium$recordParticleRemoval(CallbackInfo ci) {
        if (helium$limiterRemovalRecorded) {
            return;
        }

        helium$limiterRemovalRecorded = true;
        if (ParticleLimiter.isInitialized() && !ExternalModCompat.hasAsyncParticles()) {
            ParticleLimiter.onParticleRemoved();
        }
    }
}
