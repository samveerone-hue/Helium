package com.helium.mixin.tweaks;

import com.helium.HeliumClient;
import com.helium.tweaks.SmoothHotbar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Gui.class)
public abstract class SmoothHotbarMixin {

    @Shadow
    @Nullable
    protected abstract Player getCameraPlayer();

    @Inject(method = "renderHotbar", at = @At("HEAD"))
    private void helium$onRenderHotbarHead(GuiGraphicsExtractor context, DeltaTracker counter, CallbackInfo ci) {
        if (!HeliumClient.getConfig().smoothHotbar) return;

        Player player = getCameraPlayer();
        if (player == null) return;

        int slot = player.getInventory().getSelectedSlot();
        float delta = getdelta(counter);
        SmoothHotbar.update(slot, delta);
    }

    private static float getdelta(DeltaTracker counter) {
        try {
            var method = counter.getClass().getMethod("getTickDelta", boolean.class);
            return ((Number) method.invoke(counter, true)).floatValue();
        } catch (Exception e1) {
            try {
                var method = counter.getClass().getMethod("getLastFrameDuration");
                return ((Number) method.invoke(counter)).floatValue();
            } catch (Exception e2) {
                return 1.0f;
            }
        }
    }

    @ModifyArgs(
            method = "renderHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/resources/Identifier;IIII)V",
                    ordinal = 1
            ),
            require = 0
    )
    private void helium$modifyHotbarSelectorPos(Args args) {
        if (!HeliumClient.getConfig().smoothHotbar) return;

        Minecraft client = Minecraft.getInstance();
        int basex = (client.getWindow().getGuiScaledWidth() / 2) - 92;
        args.set(2, SmoothHotbar.getoffsetx(basex));
    }

    @ModifyArgs(
            method = "renderHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
                    ordinal = 1
            ),
            require = 0
    )
    private void helium$modifyHotbarSelectorPosAlt(Args args) {
        if (!HeliumClient.getConfig().smoothHotbar) return;

        Minecraft client = Minecraft.getInstance();
        int basex = (client.getWindow().getGuiScaledWidth() / 2) - 92;
        args.set(2, SmoothHotbar.getoffsetx(basex));
    }
}
