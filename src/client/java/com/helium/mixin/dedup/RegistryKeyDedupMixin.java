package com.helium.mixin.dedup;

import com.helium.HeliumClient;
import com.helium.dedup.DeduplicationManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

@Mixin(ResourceKey.class)
public abstract class RegistryKeyDedupMixin {

    @Unique
    private static boolean helium$failed = false;

    @Unique
    private static boolean helium$resolved = false;

    @Unique
    private static Field helium$registryField = null;

    @Unique
    private static Field helium$valueField = null;

    @Unique
    private static void helium$resolve() {
        if (helium$resolved) return;
        helium$resolved = true;

        String[] registrynames = {"registryRef", "registry", "registryName", "field_25103"};
        String[] valuenames = {"value", "location", "field_25104"};

        for (String name : registrynames) {
            try {
                Field f = ResourceKey.class.getDeclaredField(name);
                if (Identifier.class.isAssignableFrom(f.getType()) || f.getType() == Object.class) {
                    f.setAccessible(true);
                    helium$registryField = f;
                    break;
                }
            } catch (NoSuchFieldException ignored) {}
        }

        for (String name : valuenames) {
            try {
                Field f = ResourceKey.class.getDeclaredField(name);
                if (Identifier.class.isAssignableFrom(f.getType()) || f.getType() == Object.class) {
                    f.setAccessible(true);
                    helium$valueField = f;
                    break;
                }
            } catch (NoSuchFieldException ignored) {}
        }

        if (helium$registryField == null || helium$valueField == null) {
            int found = 0;
            for (Field f : ResourceKey.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (Identifier.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    if (found == 0) {
                        helium$registryField = f;
                    } else if (found == 1) {
                        helium$valueField = f;
                    }
                    found++;
                    if (found >= 2) break;
                }
            }
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void helium$dedupregistrykey(CallbackInfo ci) {
        if (helium$failed) return;

        try {
            if (!DeduplicationManager.isenabled()) return;

            helium$resolve();
            if (helium$registryField == null || helium$valueField == null) {
                helium$failed = true;
                return;
            }

            Object reg = helium$registryField.get(this);
            Object val = helium$valueField.get(this);

            if (reg instanceof Identifier regId) {
                helium$registryField.set(this, DeduplicationManager.KEY_REGISTRY.deduplicate(regId));
            }
            if (val instanceof Identifier valId) {
                helium$valueField.set(this, DeduplicationManager.KEY_LOCATION.deduplicate(valId));
            }
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("registry key dedup disabled ({})", t.getClass().getSimpleName());
            }
        }
    }
}
