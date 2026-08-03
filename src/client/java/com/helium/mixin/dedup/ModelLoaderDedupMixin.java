package com.helium.mixin.dedup;

import com.helium.HeliumClient;
import com.helium.dedup.DeduplicationManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;

@Pseudo
@Mixin(targets = {
        "net.minecraft.client.resources.model.ModelBakery",
        "net.minecraft.client.resources.model.ModelBaker"
})
public abstract class ModelLoaderDedupMixin {

    @Unique
    private static boolean helium$failed = false;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void helium$replacecollections(CallbackInfo ci) {
        if (helium$failed) return;

        try {
            if (!DeduplicationManager.isenabled()) return;

            Class<?> clazz = this.getClass();
            for (Field f : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (!Modifier.isFinal(f.getModifiers())) continue;

                f.setAccessible(true);

                try {
                    java.lang.reflect.Field modifiersField;
                    try {
                        modifiersField = Field.class.getDeclaredField("modifiers");
                        modifiersField.setAccessible(true);
                        modifiersField.setInt(f, f.getModifiers() & ~Modifier.FINAL);
                    } catch (NoSuchFieldException ignored) {
                        // [NOTE]: java 17+ may need unsafe or VarHandle
                    }
                } catch (Throwable ignored) {}

                Object val = f.get(this);

                if (val instanceof Map<?, ?> map && !(val instanceof Object2ObjectOpenHashMap)) {
                    try {
                        f.set(this, new Object2ObjectOpenHashMap<>(map));
                    } catch (Throwable ignored) {}
                } else if (val instanceof Set<?> set && !(val instanceof ObjectOpenHashSet)) {
                    try {
                        f.set(this, new ObjectOpenHashSet<>(set));
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("model loader dedup disabled ({})", t.getClass().getSimpleName());
            }
        }
    }
}
