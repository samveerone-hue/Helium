package com.helium.mixin.random;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.math.TableGaussianGenerator;
import net.minecraft.util.math.random.GaussianGenerator;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.RandomSeed;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandomImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Xoroshiro128PlusPlusRandom.class)
public abstract class Xoroshiro128PlusPlusRandomMixin {

    @Mutable
    @Shadow
    @Final
    private GaussianGenerator gaussianGenerator;

    @Inject(method = "<init>(J)V", at = @At("TAIL"), require = 0)
    private void helium$modifyGaussianSeed(long seed, CallbackInfo ci) {
        helium$replacegaussian();
    }

    @Inject(method = "<init>(Lnet/minecraft/util/math/random/RandomSeed$XoroshiroSeed;)V", at = @At("TAIL"), require = 0)
    private void helium$modifyGaussianXoroshiroSeed(RandomSeed.XoroshiroSeed seed, CallbackInfo ci) {
        helium$replacegaussian();
    }

    @Inject(method = "<init>(JJ)V", at = @At("TAIL"), require = 0)
    private void helium$modifyGaussianTwoLongs(long seedLo, long seedHi, CallbackInfo ci) {
        helium$replacegaussian();
    }

    @Inject(method = "<init>(Lnet/minecraft/util/math/random/Xoroshiro128PlusPlusRandomImpl;)V", at = @At("TAIL"), require = 0)
    private void helium$modifyGaussianImpl(Xoroshiro128PlusPlusRandomImpl implementation, CallbackInfo ci) {
        helium$replacegaussian();
    }

    @Unique
    private void helium$replacegaussian() {
        HeliumConfig config = HeliumClient.getConfig();
        boolean enabled = config != null && config.fastRandom;
        if (enabled) {
            this.gaussianGenerator = new TableGaussianGenerator((Random) this);
        }
    }
}
