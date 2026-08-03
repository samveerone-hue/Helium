package com.helium.mixin.random;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.math.TableGaussianGenerator;
import net.minecraft.world.level.levelgen.MarsagliaPolarGaussian;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(XoroshiroRandomSource.class)
public abstract class Xoroshiro128PlusPlusRandomMixin {

    @Mutable
    @Shadow
    @Final
    private MarsagliaPolarGaussian gaussianGenerator;

    @Inject(method = "<init>(J)V", at = @At("TAIL"), require = 0)
    private void helium$modifyGaussianSeed(long seed, CallbackInfo ci) {
        helium$replacegaussian();
    }

    @Inject(method = "<init>(Lnet/minecraft/world/level/levelgen/RandomSupport$Seed128bit;)V", at = @At("TAIL"), require = 0)
    private void helium$modifyGaussianXoroshiroSeed(RandomSupport.Seed128bit seed, CallbackInfo ci) {
        helium$replacegaussian();
    }

    @Inject(method = "<init>(JJ)V", at = @At("TAIL"), require = 0)
    private void helium$modifyGaussianTwoLongs(long seedLo, long seedHi, CallbackInfo ci) {
        helium$replacegaussian();
    }

    @Inject(method = "<init>(Lnet/minecraft/world/level/levelgen/Xoroshiro128PlusPlus;)V", at = @At("TAIL"), require = 0)
    private void helium$modifyGaussianImpl(Xoroshiro128PlusPlus implementation, CallbackInfo ci) {
        helium$replacegaussian();
    }

    @Unique
    private void helium$replacegaussian() {
        HeliumConfig config = HeliumClient.getConfig();
        boolean enabled = config == null || config.fastRandom;
        if (enabled) {
            this.gaussianGenerator = new TableGaussianGenerator((RandomSource) this);
        }
    }
}
