package com.helium.mixin.particle;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(Particle.class)
public abstract class ParticleLODMixin {

    @Unique
    private static boolean helium$lodFailed = false;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$applyParticleLODModern(CallbackInfo ci) {
        if (helium$lodFailed) return;

        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.particleLOD) return;

            Particle self = (Particle) (Object) this;
            if (!helium$shouldApplyLOD(self)) return;

            Minecraft client = Minecraft.getInstance();
            if (client.gameRenderer == null) return;

            Camera camera = client.gameRenderer.getMainCamera();
            if (camera == null) return;

            net.minecraft.world.phys.Vec3 camPos = com.helium.util.VersionCompat.getCameraPosition(camera);
            double dx = self.getBoundingBox().getCenter().x - camPos.x;
            double dy = self.getBoundingBox().getCenter().y - camPos.y;
            double dz = self.getBoundingBox().getCenter().z - camPos.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            double threshold = config.particleLODDistance;

            if (distSq > threshold * threshold) {
                if (ThreadLocalRandom.current().nextDouble() > config.particleLODReduction) {
                    self.remove();
                    ci.cancel();
                }
            }
        } catch (Throwable t) {
            if (!helium$lodFailed) {
                helium$lodFailed = true;
                HeliumClient.LOGGER.warn("particle LOD (modern) disabled ({})", t.getClass().getSimpleName());
            }
        }
    }

    @Unique
    private static boolean helium$shouldApplyLOD(Particle particle) {
        String name = particle.getClass().getName().toLowerCase();
        return name.contains("rain") || name.contains("snow") ||
                name.contains("cloud") || name.contains("ash") ||
                name.contains("drip") || name.contains("spore") ||
                name.contains("smoke") || name.contains("dust");
    }
}
