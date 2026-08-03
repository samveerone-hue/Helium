package com.helium.mixin.dedup;

import com.helium.dedup.DeduplicationManager;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.block.ComposterBlock$OutputContainer")
public abstract class ComposterOutputMixin {

    @Unique
    private static final int[] HELIUM_OUTPUT = new int[]{0};

    @Unique
    private static final int[] HELIUM_EMPTY = new int[0];

    @Inject(method = "getAvailableSlots", at = @At("RETURN"), cancellable = true, require = 0)
    private void helium$fixoutputslots(Direction direction, CallbackInfoReturnable<int[]> cir) {
        if (!DeduplicationManager.isenabled()) return;
        if (direction == Direction.DOWN) {
            cir.setReturnValue(HELIUM_OUTPUT);
        } else {
            cir.setReturnValue(HELIUM_EMPTY);
        }
    }
}
