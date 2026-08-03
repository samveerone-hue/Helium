package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.CullingHelper;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WeatherEffectRenderer.class)
public abstract class RainCullingMixin {

    @Unique
    private static boolean helium$failed = false;

    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$cullrain(Level world, BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (helium$failed) return;
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled || !config.rainCulling) return;

            int surfacey = world.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
            AABB column = new AABB(
                    pos.getX(), surfacey, pos.getZ(),
                    pos.getX() + 1, world.getHeight(), pos.getZ() + 1
            );

            if (!CullingHelper.isvisible(column)) {
                cir.setReturnValue(Biome.Precipitation.NONE);
            }
        } catch (Throwable t) {
            if (!helium$failed) {
                helium$failed = true;
                HeliumClient.LOGGER.warn("rain culling failed ({})", t.getClass().getSimpleName());
            }
        }
    }
}
