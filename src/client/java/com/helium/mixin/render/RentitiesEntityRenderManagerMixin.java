package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.rentities.RendererCapabilityState;
import com.helium.rentities.entities.EntityBatchRenderer;
import com.helium.rentities.entities.RentitiesRenderStatePolicy;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
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
 * Vanilla state extraction always runs. Rentities replaces the expensive geometry
 * submission only after the complete GPU path is known to be ready. A failed
 * preflight always falls through to vanilla for that frame.
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
            // completed cache on subsequent frames. ensureMeshFor() also refreshes
            // the GPU buffers and texture bootstrap after extraction.
            if (!renderer.hasMeshFor(type)) {
                renderer.getMeshBaker().ensureMeshFor(type);
                return;
            }

            // These checks remain immediately before cancellation so a texture/shader
            // failure or async rejection can never make an entity disappear.
            if (!renderer.canBatchEntity(type) || !renderer.asyncAllowsBatch(type)) return;

            if (EntityBatchRenderer.queueEntityState(state, offsetX, offsetY, offsetZ)) {
                ci.cancel();
            }
        } catch (Throwable t) {
            HeliumClient.LOGGER.debug(
                    "[Rentities] Entity state batching rejected; falling back to vanilla: {}",
                    t.toString());
        }
    }
}
