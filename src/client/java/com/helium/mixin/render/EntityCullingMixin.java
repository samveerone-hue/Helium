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
import net.minecraft.util.math.Box;
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

            double dx = entity.getX() - x;
            double dy = entity.getY() - y;
            double dz = entity.getZ() - z;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (config.entityCulling) {
                double maxDist = (double) config.entityCullDistance * config.entityCullDistance;
                if (distSq > maxDist) {
                    cir.setReturnValue(false);
                    return;
                }

                if (config.entityGpuBatching && config.entityGpuFrustumCulling) {
                    Box bounds = entity.getBoundingBox();
                    double volume = Math.max(0.0, bounds.getLengthX())
                            * Math.max(0.0, bounds.getLengthY())
                            * Math.max(0.0, bounds.getLengthZ());

                    if ((volume < 0.3 && distSq > 2304.0)
                            || (volume < 0.8 && distSq > 6400.0)
                            || (volume < 2.0 && distSq > 16384.0)) {
                        cir.setReturnValue(false);
                        return;
                    }
                }

                long tick = client.world.getTime();
                int sourceId = client.player.getId();
                int targetId = entity.getId();
                Boolean gpu = GpuComputeManager.cached(sourceId, targetId, tick, distSq);
                if (gpu != null && !gpu) {
                    cir.setReturnValue(false);
                    return;
                }

                if (distSq <= 2304.0 && GpuComputeManager.lineOfSightEnabled()) {
                    Box bounds = entity.getBoundingBox();
                    float centerX = (float) ((bounds.minX + bounds.maxX) * 0.5);
                    float centerY = (float) ((bounds.minY + bounds.maxY) * 0.5);
                    float centerZ = (float) ((bounds.minZ + bounds.maxZ) * 0.5);
                    float topY = (float) (bounds.maxY - 0.05);
                    float bottomY = (float) (bounds.minY + 0.05);
                    float leftX = (float) (bounds.minX + 0.05);
                    float rightX = (float) (bounds.maxX - 0.05);
                    float ox = (float) x;
                    float oy = (float) y;
                    float oz = (float) z;

                    float[] rays = new float[]{
                            ox, oy, oz, centerX, centerY, centerZ,
                            ox, oy, oz, centerX, topY, centerZ,
                            ox, oy, oz, centerX, bottomY, centerZ,
                            ox, oy, oz, leftX, centerY, centerZ,
                            ox, oy, oz, rightX, centerY, centerZ
                    };
                    GpuComputeManager.requestLineOfSightMulti(
                            sourceId,
                            targetId,
                            rays,
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
