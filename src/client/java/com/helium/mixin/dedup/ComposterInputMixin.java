package com.helium.mixin.dedup;

import com.helium.dedup.DeduplicationManager;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.block.ComposterBlock$InputContainer")
public abstract class ComposterInputMixin {

    @Unique
    private static final int[] HELIUM_INPUT = new int[]{0};

    @Unique
    private static final int[] HELIUM_EMPTY = new int[0];

    @Inject(method = "getSlotsForFace", at = @At("RETURN"), cancellable = true, require = 0)
    private void helium$fixinputslots(Direction direction, CallbackInfoReturnable<int[]> cir) {
        if (!DeduplicationManager.isenabled()) return;
        if (direction == Direction.UP) {
            cir.setReturnValue(HELIUM_INPUT);
        } else {
            cir.setReturnValue(HELIUM_EMPTY);
        }
    }
}
