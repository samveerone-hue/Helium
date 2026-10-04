package com.helium.mixin.tweaks;

import com.helium.HeliumClient;
import com.helium.tweaks.SmoothHotbar;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(InGameHud.class)
public abstract class SmoothHotbarMixin {

    static boolean isSmoothHotbarEnabled() {
        var config = HeliumClient.getConfig();
        return config != null && config.smoothHotbar;
    }

    @Shadow
    @Nullable
    protected abstract PlayerEntity getCameraPlayer();

    @Inject(method = "renderHotbar", at = @At("HEAD"))
    private void helium$onRenderHotbarHead(DrawContext context, RenderTickCounter counter, CallbackInfo ci) {
        if (!isSmoothHotbarEnabled()) return;

        PlayerEntity player = getCameraPlayer();
        if (player == null) return;

        int slot = player.getInventory().getSelectedSlot();
        float delta = getdelta(counter);
        SmoothHotbar.update(slot, delta);
    }

    private static float getdelta(RenderTickCounter counter) {
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
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 1
            ),
            require = 0
    )
    private void helium$modifyHotbarSelectorPos(Args args) {
        if (!isSmoothHotbarEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        int basex = (client.getWindow().getScaledWidth() / 2) - 92;
        args.set(2, SmoothHotbar.getoffsetx(basex));
    }

    @ModifyArgs(
            method = "renderHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 1
            ),
            require = 0
    )
    private void helium$modifyHotbarSelectorPosAlt(Args args) {
        if (!isSmoothHotbarEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        int basex = (client.getWindow().getScaledWidth() / 2) - 92;
        args.set(2, SmoothHotbar.getoffsetx(basex));
    }
}
