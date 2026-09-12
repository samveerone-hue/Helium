package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.rentities.RendererCapabilityState;
import com.helium.rentities.entities.EntityBatchRenderer;
import com.helium.rentities.entities.RentitiesRenderStatePolicy;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * State-based Rentities interception for Yarn 1.21.11.
 *
 * Rentities normally replaces the whole entity render call. Living entities are
 * slightly different when they have feature renderers (armor, held items,
 * saddles, capes, custom heads, etc.): those features still need vanilla's
 * feature pipeline for correctness. In that case Rentities replaces only the
 * base body model command and lets the rest of the vanilla renderer continue.
 */
@Mixin(EntityRenderManager.class)
public abstract class RentitiesEntityRenderManagerMixin {

    @Inject(
            method = "render",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private <S extends EntityRenderState> void helium$batchEntity(
            S state,
            CameraRenderState cameraState,
            double offsetX,
            double offsetY,
            double offsetZ,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.modEnabled || !config.entityGpuBatching) return;

        RendererCapabilityState caps = RendererCapabilityState.current();
        EntityBatchRenderer renderer = EntityBatchRenderer.INSTANCE;
        if (caps == null || !caps.gpuBatchingAllowed(config) || renderer == null || state == null) return;

        EntityType<?> type = EntityBatchRenderer.getEntityType(state);
        if (!RentitiesRenderStatePolicy.canBatch(state, type)) return;

        try {
            // A cache miss is recoverable. Build the missing base mesh on the render
            // thread, let this frame fall through to vanilla, then batch from the
            // completed cache on subsequent frames.
            if (!renderer.hasMeshFor(type)) {
                renderer.getMeshBaker().ensureMeshFor(type);
                return;
            }

            // These checks remain immediately before cancellation/substitution so a
            // texture/shader failure or async rejection can never make an entity disappear.
            if (!renderer.canBatchEntity(type) || !renderer.asyncAllowsBatch(type)) return;
            if (!EntityBatchRenderer.queueEntityState(state, offsetX, offsetY, offsetZ)) return;

            EntityRenderer<?, ?> entityRenderer = ((EntityRenderManager) (Object) this).getRenderer(state);
            if (entityRenderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer) {
                Model<?> model = livingRenderer.getModel();
                if (model != null) {
                    RentitiesBodyModelSuppression.mark(state, model);
                    return;
                }
            }

            // Non-living entities have no feature pipeline to preserve, so the
            // existing full-render replacement remains appropriate for them.
            ci.cancel();
        } catch (Throwable t) {
            RentitiesBodyModelSuppression.clear();
            HeliumClient.LOGGER.debug(
                    "[Rentities] Entity state batching rejected; falling back to vanilla: {}",
                    t.toString());
        }
    }

    @Inject(
            method = "render",
            at = @At("TAIL"),
            require = 0
    )
    private <S extends EntityRenderState> void helium$clearBodySuppression(
            S state,
            CameraRenderState cameraState,
            double offsetX,
            double offsetY,
            double offsetZ,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CallbackInfo ci) {
        RentitiesBodyModelSuppression.clear();
    }
}
