package com.helium.mixin.particle;

import com.helium.compat.ExternalModCompat;
import com.helium.particle.ParticleLimiter;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the particle limiter synchronized with the actual particle lifetime. */
@Mixin(Particle.class)
public abstract class ParticleLifecycleMixin {
    @Unique private boolean helium$limiterRemovalRecorded;

    @Inject(method = "remove", at = @At("HEAD"), require = 0)
    private void helium$recordParticleRemoval(CallbackInfo ci) {
        if (helium$limiterRemovalRecorded) return;
        helium$limiterRemovalRecorded = true;
        if (ParticleLimiter.isInitialized() && !ExternalModCompat.hasAsyncParticles()) {
            ParticleLimiter.onParticleRemoved();
        }
    }
}
