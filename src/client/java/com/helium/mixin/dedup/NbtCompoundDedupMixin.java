package com.helium.mixin.dedup;

import com.helium.HeliumClient;
import com.helium.dedup.DeduplicationManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NbtCompound.class)
public abstract class NbtCompoundDedupMixin {

    @Mutable
    @Shadow
    @Final
    private Map<String, NbtElement> entries;

    @Unique
    private static boolean helium$failed = false;

    @Inject(method = "<init>(Ljava/util/Map;)V", at = @At("RETURN"), require = 0)
    private void helium$replaceMapImplementation(Map<String, NbtElement> entries, CallbackInfo ci) {
        helium$replaceWithObjectMap();
    }

    @Inject(method = "<init>()V", at = @At("RETURN"), require = 0)
    private void helium$replaceDefaultMapImplementation(CallbackInfo ci) {
        helium$replaceWithObjectMap();
    }

    @Unique
    private void helium$replaceWithObjectMap() {
        if (helium$failed || !DeduplicationManager.isenabled()) {
            return;
        }

        try {
            if (!(this.entries instanceof Object2ObjectOpenHashMap)) {
                this.entries = new Object2ObjectOpenHashMap<>(this.entries);
            }
        } catch (Throwable t) {
            helium$failed = true;
            HeliumClient.LOGGER.warn(
                    "nbt compound map optimization disabled ({})",
                    t.getClass().getSimpleName()
            );
        }
    }
}
