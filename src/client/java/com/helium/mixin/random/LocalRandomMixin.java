package com.helium.mixin.random;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.math.TableGaussianGenerator;
import net.minecraft.world.level.levelgen.MarsagliaPolarGaussian;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleThreadedRandomSource.class)
public abstract class LocalRandomMixin {

    @Mutable
    @Shadow
    @Final
    private MarsagliaPolarGaussian gaussianGenerator;

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void helium$modifyGaussian(long seed, CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        boolean enabled = config == null || config.fastRandom;
        if (enabled) {
            this.gaussianGenerator = new TableGaussianGenerator((RandomSource) this);
        }
    }
}
