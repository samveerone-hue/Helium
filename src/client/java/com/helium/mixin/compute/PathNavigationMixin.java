package com.helium.mixin.compute;

import com.helium.compute.GpuComputeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PathNavigation.class)
public abstract class PathNavigationMixin {
    @Shadow @Final protected Mob mob;

    @Inject(
            method = "createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void helium$gpuPathfinding(BlockPos target, int reachRange, CallbackInfoReturnable<Path> cir) {
        if (!((Object) this instanceof GroundPathNavigation) || !GpuComputeManager.pathfindingEnabled()) return;

        Path cached = GpuComputeManager.getCachedPath(mob, target, reachRange);
        if (cached != null) {
            cir.setReturnValue(cached);
            return;
        }

        GpuComputeManager.requestPath(mob, target, reachRange);
    }
}
