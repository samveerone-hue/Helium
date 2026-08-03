package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.Cullable;
import com.helium.render.LeafCullingEngine;
import com.helium.render.LeafCullingEngine.CullingMode;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LeavesBlock.class, priority = 1220)
public class LeavesBlockMixin implements Cullable {

    @Unique
    private static boolean helium$failed = false;

    @Inject(method = "skipRendering", at = @At("HEAD"), cancellable = true, require = 0)
    protected void helium$cullLeafSide(BlockState state, BlockState stateFrom, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (helium$failed) return;
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled) return;

            CullingMode mode = LeafCullingEngine.getmode();
            if (mode == CullingMode.OFF) return;

            if (mode == CullingMode.FAST || LeafCullingEngine.areLeavesOpaque() ||
                    (mode == CullingMode.VERTICAL && direction.getAxis() == Direction.Axis.Y)) {
                if (LeafCullingEngine.isleaflike(stateFrom.getBlock())) {
                    cir.setReturnValue(true);
                }
            }
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("leaf culling failed ({})", t.getClass().getSimpleName());
            }
        }
    }

    @Override
    public boolean helium$shouldCullSide(BlockState state, BlockGetter view, BlockPos pos, Direction facing) {
        return false;
    }
}
