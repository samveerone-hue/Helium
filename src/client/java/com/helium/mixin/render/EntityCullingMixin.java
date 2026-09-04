package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.TemporalReprojection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityCullingMixin<T extends Entity> {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$cullDistantEntities(T entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || entity instanceof PlayerEntity) return;

            double dx=entity.getX()-client.player.getX(), dy=entity.getY()-client.player.getY(), dz=entity.getZ()-client.player.getZ();
            double distSq=dx*dx+dy*dy+dz*dz;
            if (config.entityCulling) {
                double maxDist=(double)config.entityCullDistance*config.entityCullDistance;
                if (distSq>maxDist) { cir.setReturnValue(false); return; }
            }
            if (config.temporalReprojection && TemporalReprojection.isInitialized() && !(entity instanceof HostileEntity)
                    && TemporalReprojection.shouldSkipEntity(distSq)) cir.setReturnValue(false);
        } catch (Throwable t) {
            HeliumClient.LOGGER.warn("entity culling hook disabled ({})", t.getClass().getSimpleName());
        }
    }
}