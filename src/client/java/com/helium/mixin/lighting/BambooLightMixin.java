package com.helium.mixin.lighting;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.class)
public abstract class BambooLightMixin {

    @Inject(method = "getAmbientOcclusionLightLevel", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$skipBambooLightCalc(BlockState state, BlockGetter world, BlockPos pos,
                                            CallbackInfoReturnable<Float> cir) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.fastBambooLight) {
            return;
        }
        if (state.getBlock() instanceof BambooStalkBlock) {
            cir.setReturnValue(1.0F);
        }
    }
}
