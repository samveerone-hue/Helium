package com.helium.rentities;

import com.helium.rentities.entities.RentitiesRenderStatePolicy;
import net.minecraft.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class RentitiesRenderStatePolicyTest {

    @Test
    void unsupportedStateAndCpuAnimatedModelsStayOnVanillaPath() {
        assertFalse(RentitiesRenderStatePolicy.canBatch(new Object(), EntityType.BLAZE));
        assertFalse(RentitiesRenderStatePolicy.canBatch(new Object(), EntityType.WARDEN));
        assertFalse(RentitiesRenderStatePolicy.canBatch(null, EntityType.ZOMBIE));
    }
}
