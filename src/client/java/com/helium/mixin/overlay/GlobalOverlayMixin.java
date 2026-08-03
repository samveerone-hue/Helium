package com.helium.mixin.overlay;

import com.helium.overlay.OverlayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class GlobalOverlayMixin {

    @Inject(method = "renderWithTooltip", at = @At("TAIL"), require = 0)
    private void helium$renderglobaloverlay(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            OverlayRenderer.renderglobal(context, client);
        }
    }
}
