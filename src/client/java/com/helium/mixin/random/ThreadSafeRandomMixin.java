package com.helium.mixin.random;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.math.TableGaussianGenerator;
import net.minecraft.world.level.levelgen.MarsagliaPolarGaussian;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("deprecation")
@Mixin(ThreadSafeLegacyRandomSource.class)
public abstract class ThreadSafeRandomMixin {

    @Mutable
    @Shadow
    @Final
    private MarsagliaPolarGaussian gaussianSource;

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void helium$modifyGaussian(long seed, CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        boolean enabled = config == null || config.fastRandom;
        if (enabled) {
            this.gaussianSource = new TableGaussianGenerator((RandomSource) this);
        }
    }
}
