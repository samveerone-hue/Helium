package com.helium.mixin.compat;

import com.helium.HeliumClient;
import com.helium.render.LeafCullingEngine;
import com.helium.render.LeafCullingEngine.CullingMode;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Sodium bypasses {@code ModelBlockRenderer.shouldRenderFace}, so the smart culling modes
 * need the same hook on Sodium's own block render context. The FAST/VERTICAL modes already
 * reach Sodium through {@code BlockState.skipRendering}.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext", remap = false)
public abstract class SodiumLeafCullingMixin {

    @Shadow
    protected BlockAndTintGetter level;

    @Shadow
    protected BlockState state;

    @Shadow
    protected BlockPos pos;

    @Unique
    private static boolean helium$failed = false;

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$cullLeafFace(Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (helium$failed) return;

        try {
            CullingMode mode = LeafCullingEngine.getmode();
            if (mode == CullingMode.OFF || mode == CullingMode.FAST || mode == CullingMode.VERTICAL) return;

            if (!LeafCullingEngine.isleaflike(this.state.getBlock())) return;

            BlockPos sidepos = this.pos.relative(side);
            BlockState sidestate = this.level.getBlockState(sidepos);

            Optional<Boolean> result = LeafCullingEngine.customshoulddraw(this.level, this.state, sidestate, this.pos, sidepos, side);
            result.ifPresent(cir::setReturnValue);
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("sodium leaf culling failed ({})", t.getClass().getSimpleName());
            }
        }
    }
}
