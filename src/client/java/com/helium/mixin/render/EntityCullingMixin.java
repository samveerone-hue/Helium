package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.compute.GpuComputeManager;
import com.helium.config.HeliumConfig;
import com.helium.render.TemporalReprojection;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityCullingMixin<T extends Entity> {
    @Unique private static boolean helium$failed = false;

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$cullDistantEntities(
            T entity,
            Frustum frustum,
            double x,
            double y,
            double z,
            CallbackInfoReturnable<Boolean> cir) {
        if (helium$failed) return;
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null || entity instanceof PlayerEntity) return;

            double dx = entity.getX() - client.player.getX();
            double dy = entity.getY() - client.player.getY();
            double dz = entity.getZ() - client.player.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;

            if (config.entityCulling) {
                double maxDist = (double) config.entityCullDistance * config.entityCullDistance;
                if (distSq > maxDist) {
                    cir.setReturnValue(false);
                    return;
                }

                // EntityBatch's proven LOD idea is deliberately opt-in behind the
                // existing GPU-batching switch, so normal Helium culling is unchanged.
                if (config.entityGpuBatching && config.entityGpuFrustumCulling) {
                    double minX = entity.getBoundingBox().minX;
                    double minY = entity.getBoundingBox().minY;
                    double minZ = entity.getBoundingBox().minZ;
                    double maxX = entity.getBoundingBox().maxX;
                    double maxY = entity.getBoundingBox().maxY;
                    double maxZ = entity.getBoundingBox().maxZ;
                    double volume = Math.max(0.0, maxX - minX)
                            * Math.max(0.0, maxY - minY)
                            * Math.max(0.0, maxZ - minZ);

                    if ((volume < 0.3 && distSq > 2304.0)
                            || (volume < 0.8 && distSq > 6400.0)
                            || (volume < 2.0 && distSq > 16384.0)) {
                        cir.setReturnValue(false);
                        return;
                    }
                }

                long tick = client.world.getTime();
                Boolean gpu = GpuComputeManager.cached(client.player.getId(), entity.getId(), tick);
                if (gpu != null && !gpu) {
                    cir.setReturnValue(false);
                    return;
                }
                if (distSq <= 2304.0 && GpuComputeManager.lineOfSightEnabled()) {
                    GpuComputeManager.requestLineOfSight(
                            client.player.getId(),
                            entity.getId(),
                            (float) client.player.getX(),
                            (float) client.player.getEyeY(),
                            (float) client.player.getZ(),
                            (float) entity.getX(),
                            (float) entity.getEyeY(),
                            (float) entity.getZ(),
                            tick,
                            (bx, by, bz) -> {
                                BlockPos p = new BlockPos(bx, by, bz);
                                BlockState s = client.world.getBlockState(p);
                                return s.isOpaque() && s.isFullCube(client.world, p);
                            });
                }
            }

            if (config.temporalReprojection
                    && TemporalReprojection.isInitialized()
                    && !(entity instanceof HostileEntity)
                    && TemporalReprojection.shouldSkipEntity(distSq)) {
                cir.setReturnValue(false);
            }
        } catch (Throwable t) {
            helium$failed = true;
            HeliumClient.LOGGER.warn("entity culling hook disabled ({})", t.getClass().getSimpleName());
        }
    }
}
