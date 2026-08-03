package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PowderSnowBlock.class, priority = 1200)
public abstract class PowderSnowCullingMixin {

    @Unique
    private static boolean helium$failed = false;

    @Inject(method = "isSideInvisible", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$fixpowdersnowculling(BlockState state, BlockState stateFrom, Direction direction,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (helium$failed) return;
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled) return;
            cir.setReturnValue(false);
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("powder snow culling failed ({})", t.getClass().getSimpleName());
            }
        }
    }
}
