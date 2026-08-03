package com.helium.mixin.ui;

import com.helium.ui.ScrollMath;
import com.helium.ui.ScrollableWidgetManipulator;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractScrollArea.class)
public abstract class ScrollableWidgetMixin implements ScrollableWidgetManipulator {

    @Shadow
    private double scrollAmount;

    @Shadow
    public abstract int maxScrollAmount();

    @Shadow
    public abstract void setScrollAmount(double scrollAmount);

    @Unique
    private double helium$animationTimer = 0;

    @Unique
    private double helium$scrollStartVelocity = 0;

    @Unique
    private boolean helium$renderSmooth = false;

    @Override
    public void helium$manipulateScrollAmount(float delta) {
        if (!ScrollMath.isEnabled()) return;

        helium$renderSmooth = true;
        helium$checkOutOfBounds(delta);

        if (Math.abs(ScrollMath.scrollbarVelocity(helium$animationTimer, helium$scrollStartVelocity)) < 1.0) return;
        helium$applyMotion(delta);
    }

    @Unique
    private void helium$applyMotion(float delta) {
        setScrollAmount(scrollAmount + ScrollMath.scrollbarVelocity(helium$animationTimer, helium$scrollStartVelocity) * delta);
        helium$animationTimer += delta * 10 / ScrollMath.getAnimationDuration();
    }

    @Unique
    private void helium$checkOutOfBounds(float delta) {
        if (scrollAmount < 0) {
            setScrollAmount(scrollAmount + ScrollMath.pushBackStrength(Math.abs(scrollAmount), delta));
            if (scrollAmount > -0.2) scrollAmount = 0;
        }
        if (scrollAmount > maxScrollAmount()) {
            setScrollAmount(scrollAmount - ScrollMath.pushBackStrength(scrollAmount - maxScrollAmount(), delta));
            if (scrollAmount < maxScrollAmount() + 0.2) scrollAmount = maxScrollAmount();
        }
    }

    @WrapOperation(
            method = "mouseScrolled",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/AbstractScrollArea;setScrollAmount(D)V"),
            require = 0
    )
    private void helium$captureVelocity(AbstractScrollArea instance, double targetScrollY, Operation<Void> original) {
        if (!ScrollMath.isEnabled() || !helium$renderSmooth) {
            original.call(instance, targetScrollY);
            return;
        }

        double diff = targetScrollY - this.scrollAmount;
        diff = Math.signum(diff) * Math.min(Math.abs(diff), 10);
        diff *= ScrollMath.getScrollSpeed();

        if (Math.signum(diff) != Math.signum(helium$scrollStartVelocity)) {
            diff *= 2.5d;
        }

        helium$animationTimer *= 0.5;
        helium$scrollStartVelocity = ScrollMath.scrollbarVelocity(helium$animationTimer, helium$scrollStartVelocity) + diff;
        helium$animationTimer = 0;
    }

    @WrapOperation(
            method = "mouseDragged",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/AbstractScrollArea;setScrollAmount(D)V"),
            require = 0
    )
    private void helium$clampDraggedScrollY(AbstractScrollArea instance, double targetScrollY, Operation<Void> original) {
        original.call(instance, Mth.clamp(targetScrollY, 0.0, this.maxScrollAmount()));
    }

    @WrapMethod(method = "setScrollAmount", require = 0)
    private void helium$setScrollAmountUnclamped(double targetScrollY, Operation<Void> original) {
        if (!ScrollMath.isEnabled() || !helium$renderSmooth) {
            original.call(targetScrollY);
            return;
        }
        if (targetScrollY > maxScrollAmount() + 1e5 || targetScrollY < -1e5) {
            original.call(targetScrollY);
        } else {
            this.scrollAmount = targetScrollY;
        }
    }
}
