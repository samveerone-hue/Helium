package com.helium.rentities;

import com.helium.rentities.entities.EntityPoseNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityPoseNormalizerTest {
    @Test
    void normalizeYawKeepsBodyAndHeadAlignedWithinClampRange() {
        assertEquals(95.0f, EntityPoseNormalizer.clampBodyYaw(180.0f, 10.0f), 0.01f);
        assertEquals(-95.0f, EntityPoseNormalizer.clampBodyYaw(-180.0f, -10.0f), 0.01f);
        assertEquals(-90.0f, EntityPoseNormalizer.wrapDegrees(270.0f), 0.01f);
        assertEquals(65.0f, EntityPoseNormalizer.relativeHeadYaw(45.0f, 110.0f), 0.01f);
    }
}
