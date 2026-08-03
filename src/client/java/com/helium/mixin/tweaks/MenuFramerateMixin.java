package com.helium.mixin.tweaks;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Minecraft.class)
public class MenuFramerateMixin {

    @ModifyArg(
            method = "renderFrame",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"),
            index = 0
    )
    private int helium$menuframeratelimit(int original) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null) return original;
        int limit = config.menuFramerateLimit;
        if (limit <= 0) return original;
        Minecraft self = (Minecraft) (Object) this;
        if (self.level == null) {
            return limit;
        }
        return original;
    }
}
