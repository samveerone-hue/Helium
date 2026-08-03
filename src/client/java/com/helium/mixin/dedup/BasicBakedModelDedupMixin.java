package com.helium.mixin.dedup;

import com.helium.HeliumClient;
import com.helium.dedup.DeduplicationManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

@Pseudo
@Mixin(targets = "net.minecraft.client.resources.model.SimpleModelWrapper")
public abstract class BasicBakedModelDedupMixin {

    @Unique
    private static boolean helium$failed = false;

    @SuppressWarnings("unchecked")
    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void helium$immutablelists(CallbackInfo ci) {
        if (helium$failed) return;

        try {
            if (!DeduplicationManager.isenabled()) return;

            Class<?> clazz = this.getClass();

            for (Field f : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;

                f.setAccessible(true);
                Object val = f.get(this);

                if (val instanceof List<?> list && !list.getClass().getName().contains("Unmodifiable")) {
                    try {
                        helium$removefinal(f);
                        f.set(this, List.copyOf(list));
                    } catch (Throwable ignored) {}
                } else if (val instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (entry.getValue() instanceof List<?> entrylist) {
                            try {
                                ((Map.Entry<Object, Object>) entry).setValue(List.copyOf(entrylist));
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("basic baked model dedup disabled ({})", t.getClass().getSimpleName());
            }
        }
    }

    @Unique
    private static void helium$removefinal(Field f) {
        try {
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(f, f.getModifiers() & ~Modifier.FINAL);
        } catch (Throwable ignored) {}
    }
}
