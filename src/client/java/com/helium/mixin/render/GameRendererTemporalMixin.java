package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.TemporalReprojection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererTemporalMixin {

    @Unique
    private static boolean helium$failed = false;

    @Unique
    private static long helium$frameCounter = 0;

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void helium$captureMatrices(CallbackInfo ci) {
        if (helium$failed) return;
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled || !config.temporalReprojection) return;
            if (!TemporalReprojection.isInitialized()) return;

            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.gameRenderer == null) return;

            Matrix4f proj = client.gameRenderer.getMainCamera().createProjectionMatrixForCulling();
            Matrix4f view = new Matrix4f();

            float yaw = client.gameRenderer.getMainCamera().yRot();
            float pitch = client.gameRenderer.getMainCamera().xRot();

            view.identity();
            view.rotateX((float) Math.toRadians(-pitch));
            view.rotateY((float) Math.toRadians(yaw + 180f));

            Matrix4f combined = new Matrix4f();
            proj.mul(view, combined);

            TemporalReprojection.updateMatrices(combined, helium$frameCounter++);
        } catch (Throwable t) {
            helium$failed = true;
            HeliumClient.LOGGER.warn("temporal reprojection matrix capture disabled ({})", t.getClass().getSimpleName());
        }
    }
}
