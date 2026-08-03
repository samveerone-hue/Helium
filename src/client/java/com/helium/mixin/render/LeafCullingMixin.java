package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.render.LeafCullingEngine;
import com.helium.render.LeafCullingEngine.CullingMode;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ModelBlockRenderer.class)
public abstract class LeafCullingMixin {

    @Unique
    private static boolean helium$failed = false;

    @Inject(
            method = "shouldRenderFace",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void helium$cullLeafFace(BlockAndTintGetter world, BlockState state, Direction side, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (helium$failed) return;

        try {
            CullingMode mode = LeafCullingEngine.getmode();
            if (mode == CullingMode.OFF || mode == CullingMode.FAST || mode == CullingMode.VERTICAL) return;

            if (!LeafCullingEngine.isleaflike(state.getBlock())) return;

            BlockPos sidepos = pos.relative(side);
            BlockState sidestate = world.getBlockState(sidepos);

            Optional<Boolean> result = LeafCullingEngine.customshoulddraw(world, state, sidestate, pos, sidepos, side);
            if (result.isPresent()) {
                cir.setReturnValue(result.get());
            }
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("leaf culling failed ({})", t.getClass().getSimpleName());
            }
        }
    }
}
