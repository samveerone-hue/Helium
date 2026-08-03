package com.helium.mixin.dedup;

import com.helium.HeliumClient;
import com.helium.dedup.DeduplicationManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

@Mixin(CompoundTag.class)
public abstract class NbtCompoundDedupMixin {

    @Unique
    private static boolean helium$failed = false;

    @Unique
    private static boolean helium$resolved = false;

    @Unique
    private static Field helium$entriesField = null;

    @Unique
    private static void helium$resolve() {
        if (helium$resolved) return;
        helium$resolved = true;

        String[] names = {"entries", "tags", "field_5765"};
        for (String name : names) {
            try {
                Field f = CompoundTag.class.getDeclaredField(name);
                if (Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    helium$entriesField = f;
                    return;
                }
            } catch (NoSuchFieldException ignored) {}
        }

        for (Field f : CompoundTag.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (Map.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                helium$entriesField = f;
                return;
            }
        }
    }

    @Inject(method = "<init>(Ljava/util/Map;)V", at = @At("RETURN"), require = 0)
    private void helium$replacemapimpl(Map<String, Tag> entries, CallbackInfo ci) {
        helium$replacewitho2o();
    }

    @Inject(method = "<init>()V", at = @At("RETURN"), require = 0)
    private void helium$replacemapimpldefault(CallbackInfo ci) {
        helium$replacewitho2o();
    }

    @Unique
    @SuppressWarnings("unchecked")
    private void helium$replacewitho2o() {
        if (helium$failed) return;

        try {
            if (!DeduplicationManager.isenabled()) return;

            helium$resolve();
            if (helium$entriesField == null) {
                helium$failed = true;
                return;
            }

            Object current = helium$entriesField.get(this);
            if (current instanceof Map<?, ?> map && !(current instanceof Object2ObjectOpenHashMap)) {
                helium$entriesField.set(this, new Object2ObjectOpenHashMap<>((Map<String, Tag>) map));
            }
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("nbt compound dedup disabled ({})", t.getClass().getSimpleName());
            }
        }
    }
}
