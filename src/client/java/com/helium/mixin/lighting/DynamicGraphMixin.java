package com.helium.mixin.lighting;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.LongPredicate;

@Mixin(DynamicGraphMinFixedPoint.class)
public abstract class DynamicGraphMixin {

    @Mutable
    @Shadow
    @Final
    private Long2ByteMap computedLevels;

    @Shadow
    protected abstract void removeFromQueue(long id);

    @Unique
    private static boolean helium$failed = false;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void helium$optimizeLightMap(int levelCount, int expectedLevelSize, int expectedTotalSize, CallbackInfo ci) {
        if (helium$failed) return;

        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.optimizedLightEngine) {
                return;
            }

            Long2ByteOpenHashMap optimized = new Long2ByteOpenHashMap(expectedTotalSize, 0.75f);
            optimized.defaultReturnValue((byte) -1);
            this.computedLevels = optimized;
        } catch (Throwable t) {
            helium$failed = true;
            HeliumClient.LOGGER.warn("light engine optimization failed ({})", t.getClass().getSimpleName());
        }
    }

    @Inject(method = "removeIf", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$optimizedRemoveIf(LongPredicate predicate, CallbackInfo ci) {
        if (helium$failed) return;

        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.optimizedLightEngine) {
                return;
            }

            if (!(this.computedLevels instanceof Long2ByteOpenHashMap optimized)) {
                return;
            }

            ci.cancel();

            LongSet keys = optimized.keySet();
            LongIterator it = keys.iterator();
            while (it.hasNext()) {
                long pos = it.nextLong();
                if (predicate.test(pos)) {
                    removeFromQueue(pos);
                }
            }
        } catch (Throwable t) {
            helium$failed = true;
            HeliumClient.LOGGER.warn("light engine removeIf optimization failed ({})", t.getClass().getSimpleName());
        }
    }
}
