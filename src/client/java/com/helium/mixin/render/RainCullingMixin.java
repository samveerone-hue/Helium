package com.helium.mixin.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.render.CullingHelper;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WeatherRendering.class)
public abstract class RainCullingMixin {
    @Unique private static boolean helium$failed = false;

    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true, require = 0)
    private void helium$cullRain(World world, BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (helium$failed) return;
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null || !config.modEnabled || !config.rainCulling) return;
            int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ());
            Box column = new Box(pos.getX(), surfaceY, pos.getZ(),
                    pos.getX() + 1, world.getHeight(), pos.getZ() + 1);
            if (!CullingHelper.isvisible(column)) cir.setReturnValue(Biome.Precipitation.NONE);
        } catch (Throwable t) {
            helium$failed = true;
            HeliumClient.LOGGER.warn("rain culling disabled ({})", t.getClass().getSimpleName());
        }
    }
}