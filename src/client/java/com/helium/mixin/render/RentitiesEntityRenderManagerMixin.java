package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.rentities.RendererCapabilityState;
import com.helium.rentities.entities.EntityBatchRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * State-based Rentities interception for Yarn 1.21.11.
 *
 * <p>Vanilla still performs state extraction. Rentities replaces only the expensive
 * geometry submission/draw phase. Any unsupported entity type, missing mesh/texture,
 * inactive capability, or runtime extraction problem falls through to vanilla.</p>
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

        // Player skin extraction is not implemented yet. Never cancel vanilla player rendering.
        if (EntityBatchRenderer.getEntityType(state) == net.minecraft.entity.EntityType.PLAYER) return;

        try {
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
