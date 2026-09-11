package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.compute.GpuComputeManager;
import com.helium.config.HeliumConfig;
import com.helium.render.TemporalReprojection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityCullingMixin<T extends Entity> {
    @Unique private static boolean helium$frustumFailed = false;

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void helium$cullDistantEntities(T entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        HeliumConfig config=HeliumClient.getConfig(); if(config==null||!config.modEnabled)return;
        Minecraft client=Minecraft.getInstance(); if(client.player==null||entity instanceof Player)return;
        double dx=entity.getX()-client.player.getX(),dy=entity.getY()-client.player.getY(),dz=entity.getZ()-client.player.getZ(),distSq=dx*dx+dy*dy+dz*dz;
        if(config.entityCulling){
            double maxDist=(double)config.entityCullDistance*config.entityCullDistance;if(distSq>maxDist){cir.setReturnValue(false);return;}
            if(!helium$frustumFailed){try{float yaw=client.player.getYRot(),rad=(float)Math.toRadians(yaw);double dot=dx*(-Math.sin(rad))+dz*Math.cos(rad);if(dot<-16.0&&distSq>256.0){cir.setReturnValue(false);return;}}catch(Throwable t){helium$frustumFailed=true;}}
            long tick=client.level==null?0L:client.level.getGameTime();Boolean gpu=GpuComputeManager.cached(client.player.getId(),entity.getId(),tick);if(gpu!=null&&!gpu){cir.setReturnValue(false);return;}
            if(client.level!=null&&distSq<=2304.0&&GpuComputeManager.lineOfSightEnabled()){
                GpuComputeManager.requestLineOfSight(client.player.getId(),entity.getId(),(float)client.player.getX(),(float)client.player.getEyeY(),(float)client.player.getZ(),(float)entity.getX(),(float)entity.getEyeY(),(float)entity.getZ(),tick,
                    (bx,by,bz)->{var p=new net.minecraft.core.BlockPos(bx,by,bz);var s=client.level.getBlockState(p);return s.canOcclude()&&s.isCollisionShapeFullBlock(client.level,p);});
            }
        }
        if(config.temporalReprojection&&TemporalReprojection.isInitialized()&&!(entity instanceof Monster)&&TemporalReprojection.shouldSkipEntity(distSq))cir.setReturnValue(false);
    }
}
