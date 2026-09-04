package com.helium.mixin.dedup;

import com.helium.HeliumClient;
import com.helium.dedup.DeduplicationManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11-specific direct field access. Avoids reflection on every Identifier
 * construction while preserving the immutable values after construction.
 */
@Mixin(value = Identifier.class, priority = 500)
public abstract class IdentifierDedupMixin {

    @Mutable
    @Shadow
    @Final
    private String namespace;

    @Mutable
    @Shadow
    @Final
    private String path;

    @Unique
    private static boolean helium$failed = false;

    @Inject(method = "<init>(Ljava/lang/String;Ljava/lang/String;)V", at = @At("RETURN"), require = 0)
    private void helium$dedupTwoStringCtor(String namespace, String path, CallbackInfo ci) {
        if (helium$failed) return;

        try {
            if (!DeduplicationManager.isenabled()) return;

            this.namespace = DeduplicationManager.NAMESPACES.deduplicate(this.namespace);
            this.path = DeduplicationManager.PATHS.deduplicate(this.path);
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn(
                        "identifier dedup disabled ({})",
                        t.getClass().getSimpleName()
                );
            }
        }
    }
}
