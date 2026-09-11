package com.helium.mixin.compute;

import com.helium.compute.GpuComputeManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.Sensing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Sensing.class)
public abstract class SensingMixin {
    @Shadow @Final private Mob mob;

    @Inject(method = "hasLineOfSight", at = @At("HEAD"), cancellable = true)
    private void helium$gpuLineOfSight(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (!GpuComputeManager.lineOfSightEnabled()) return;

        long tick = mob.level().getGameTime();
        Boolean cached = GpuComputeManager.getCachedLineOfSight(mob.getId(), target.getId(), tick);
        if (cached != null) {
            cir.setReturnValue(cached);
            return;
        }

        GpuComputeManager.requestLineOfSight(mob, target);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void helium$flushGpuLineOfSight(CallbackInfo ci) {
        GpuComputeManager.onSensingTick(mob);
    }
}
