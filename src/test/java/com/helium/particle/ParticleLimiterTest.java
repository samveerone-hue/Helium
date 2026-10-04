package com.helium.particle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ParticleLimiterTest {
    @Test
    void initializationIncludesExistingParticlesInTheLimit() {
        ParticleLimiter.init(100, 100);

        assertEquals(100, ParticleLimiter.getCurrentCount());
        assertFalse(ParticleLimiter.canAddParticle(null, false));
    }
}
