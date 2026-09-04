package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.CullingHelper;
import net.minecraft.client.render.block.entity.EndGatewayBlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.EndGatewayBlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndGatewayBlockEntityRenderer.class)
public abstract class EndGatewayBeamCullingMixin {
    @Unique private static boolean helium$failed = false;
    @Inject(method = "render(Lnet/minecraft/client/render/block/entity/state/EndGatewayBlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$cullGatewayBeam(EndGatewayBlockEntityRenderState renderState, MatrixStack matrices,
                                        OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
        if (helium$failed) return;
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled || !config.beaconBeamCulling) return;
            BlockPos pos = renderState.pos;
            if (pos == null) return;
            Box box = new Box(pos.getX()-1,pos.getY()-1,pos.getZ()-1,
                    pos.getX()+2,Math.min(pos.getY()+1024,1024),pos.getZ()+2);
            if (!CullingHelper.isvisible(box)) ci.cancel();
        } catch (Throwable t) {
            helium$failed = true;
            HeliumClient.LOGGER.warn("gateway beam culling disabled ({})", t.getClass().getSimpleName());
        }
    }
}