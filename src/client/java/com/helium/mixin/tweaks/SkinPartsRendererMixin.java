package com.helium.mixin.tweaks;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AvatarRenderer.class)
public abstract class SkinPartsRendererMixin {

    @Redirect(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Avatar;isModelPartShown(Lnet/minecraft/world/entity/player/PlayerModelPart;)Z"),
            require = 0
    )
    private boolean helium$forceskinparts(Avatar instance, PlayerModelPart part) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config != null && config.forceSkinParts) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && instance == mc.player) {
                return mc.options.isModelPartEnabled(part);
            }
        }
        return instance.isModelPartShown(part);
    }
}
