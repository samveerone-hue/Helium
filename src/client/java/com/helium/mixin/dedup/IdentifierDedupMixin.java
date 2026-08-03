package com.helium.mixin.dedup;

import com.helium.HeliumClient;
import com.helium.dedup.DeduplicationManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(value = Identifier.class, priority = 500)
public abstract class IdentifierDedupMixin {

    @Unique
    private static boolean helium$failed = false;

    @Unique
    private static boolean helium$resolved = false;

    @Unique
    private static Field helium$namespaceField = null;

    @Unique
    private static Field helium$pathField = null;

    @Unique
    private static void helium$resolve() {
        if (helium$resolved) return;
        helium$resolved = true;

        String[] nsnames = {"namespace", "field_13353"};
        String[] pathnames = {"path", "field_13354"};

        for (String name : nsnames) {
            try {
                Field f = Identifier.class.getDeclaredField(name);
                if (f.getType() == String.class) {
                    f.setAccessible(true);
                    helium$namespaceField = f;
                    break;
                }
            } catch (NoSuchFieldException ignored) {}
        }

        for (String name : pathnames) {
            try {
                Field f = Identifier.class.getDeclaredField(name);
                if (f.getType() == String.class) {
                    f.setAccessible(true);
                    helium$pathField = f;
                    break;
                }
            } catch (NoSuchFieldException ignored) {}
        }

        if (helium$namespaceField == null || helium$pathField == null) {
            for (Field f : Identifier.class.getDeclaredFields()) {
                if (f.getType() == String.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    if (helium$namespaceField == null) {
                        helium$namespaceField = f;
                    } else if (helium$pathField == null) {
                        helium$pathField = f;
                        break;
                    }
                }
            }
        }
    }

    @Inject(method = "<init>(Ljava/lang/String;Ljava/lang/String;)V", at = @At("RETURN"), require = 0)
    private void helium$deduptwostringctor(String namespace, String path, CallbackInfo ci) {
        helium$dedup();
    }

    @Unique
    private void helium$dedup() {
        if (helium$failed) return;

        try {
            if (!DeduplicationManager.isenabled()) return;

            helium$resolve();
            if (helium$namespaceField == null || helium$pathField == null) {
                helium$failed = true;
                return;
            }

            String ns = (String) helium$namespaceField.get(this);
            String p = (String) helium$pathField.get(this);

            if (ns != null) helium$namespaceField.set(this, DeduplicationManager.NAMESPACES.deduplicate(ns));
            if (p != null) helium$pathField.set(this, DeduplicationManager.PATHS.deduplicate(p));
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("identifier dedup disabled ({})", t.getClass().getSimpleName());
            }
        }
    }
}
