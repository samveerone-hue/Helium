package com.helium.mixin.dedup;

import com.helium.HeliumClient;
import com.helium.dedup.DeduplicationManager;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
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
@Mixin(targets = "net.minecraft.client.renderer.block.dispatch.multipart.MultiPartModel")
public abstract class MultipartBakedModelDedupMixin {

    @Unique
    private static boolean helium$failed = false;

    @SuppressWarnings("unchecked")
    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void helium$optimizecollections(CallbackInfo ci) {
        if (helium$failed) return;

        try {
            if (!DeduplicationManager.isenabled()) return;

            Class<?> clazz = this.getClass();

            for (Field f : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;

                f.setAccessible(true);
                Object val = f.get(this);

                if (val instanceof Map<?, ?> map && !(val instanceof Reference2ObjectOpenHashMap)) {
                    try {
                        helium$removefinal(f);
                        f.set(this, new Reference2ObjectOpenHashMap<>(map));
                    } catch (Throwable ignored) {}
                } else if (val instanceof List<?> list) {
                    try {
                        helium$removefinal(f);
                        f.set(this, List.copyOf(list));
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("multipart baked model dedup disabled ({})", t.getClass().getSimpleName());
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
