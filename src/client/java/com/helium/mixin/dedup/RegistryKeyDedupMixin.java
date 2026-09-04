package com.helium.mixin.dedup;

import com.helium.HeliumClient;
import com.helium.dedup.DeduplicationManager;
import net.minecraft.registry.RegistryKey;
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
 * 1.21.11-specific direct access. RegistryKey already exposes stable named
 * fields in the target mapping, avoiding reflective field discovery per key.
 */
@Mixin(RegistryKey.class)
public abstract class RegistryKeyDedupMixin {

    @Mutable
    @Shadow
    @Final
    private Identifier registry;

    @Mutable
    @Shadow
    @Final
    private Identifier value;

    @Unique
    private static boolean helium$failed = false;

    @Inject(method = "<init>(Lnet/minecraft/util/Identifier;Lnet/minecraft/util/Identifier;)V",
            at = @At("RETURN"), require = 0)
    private void helium$dedupRegistryKey(
            Identifier registry,
            Identifier value,
            CallbackInfo ci
    ) {
        if (helium$failed || !DeduplicationManager.isenabled()) {
            return;
        }

        try {
            this.registry = DeduplicationManager.KEY_REGISTRY.deduplicate(this.registry);
            this.value = DeduplicationManager.KEY_LOCATION.deduplicate(this.value);
        } catch (Throwable t) {
            helium$failed = true;
            HeliumClient.LOGGER.warn(
                    "registry key dedup disabled ({})",
                    t.getClass().getSimpleName()
            );
        }
    }
}
