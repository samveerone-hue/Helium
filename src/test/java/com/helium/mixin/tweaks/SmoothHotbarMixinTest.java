package com.helium.mixin.tweaks;

import com.helium.HeliumClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SmoothHotbarMixinTest {
    @Test
    void nullConfigDisablesSmoothHotbarWithoutThrowing() throws ReflectiveOperationException {
        Field field = HeliumClient.class.getDeclaredField("config");
        field.setAccessible(true);
        Object previous = field.get(null);
        try {
            field.set(null, null);
            assertFalse(SmoothHotbarMixin.isSmoothHotbarEnabled());
        } finally {
            field.set(null, previous);
        }
    }
}
