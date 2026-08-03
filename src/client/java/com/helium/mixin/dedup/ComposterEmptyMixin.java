package com.helium.mixin.dedup;

import com.helium.dedup.DeduplicationManager;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.block.ComposterBlock$EmptyContainer")
public abstract class ComposterEmptyMixin {

    @Unique
    private static final int[] HELIUM_EMPTY = new int[0];

    @Inject(method = "getSlotsForFace", at = @At("RETURN"), cancellable = true, require = 0)
    private void helium$fixemptyslots(Direction direction, CallbackInfoReturnable<int[]> cir) {
        if (!DeduplicationManager.isenabled()) return;
        cir.setReturnValue(HELIUM_EMPTY);
    }
}
