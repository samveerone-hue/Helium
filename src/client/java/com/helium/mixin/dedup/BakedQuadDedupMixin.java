package com.helium.mixin.dedup;

import com.helium.HeliumClient;
import com.helium.dedup.DeduplicationManager;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

@Mixin(BakedQuad.class)
public abstract class BakedQuadDedupMixin {

    @Unique
    private static boolean helium$failed = false;

    @Unique
    private static boolean helium$resolved = false;

    @Unique
    private static Field helium$vertexField = null;

    @Unique
    private static void helium$resolve() {
        if (helium$resolved) return;
        helium$resolved = true;

        String[] names = {"vertexData", "vertices", "field_3969"};
        for (String name : names) {
            try {
                Field f = BakedQuad.class.getDeclaredField(name);
                if (f.getType() == int[].class) {
                    f.setAccessible(true);
                    helium$vertexField = f;
                    return;
                }
            } catch (NoSuchFieldException ignored) {}
        }

        for (Field f : BakedQuad.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() == int[].class) {
                f.setAccessible(true);
                helium$vertexField = f;
                return;
            }
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void helium$dedupquadvertices(CallbackInfo ci) {
        if (helium$failed) return;

        try {
            if (!DeduplicationManager.isenabled()) return;

            helium$resolve();
            if (helium$vertexField == null) {
                helium$failed = true;
                return;
            }

            int[] verts = (int[]) helium$vertexField.get(this);
            if (verts != null) {
                helium$vertexField.set(this, DeduplicationManager.QUADS.deduplicate(verts));
            }
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("baked quad dedup disabled ({})", t.getClass().getSimpleName());
            }
        }
    }
}
