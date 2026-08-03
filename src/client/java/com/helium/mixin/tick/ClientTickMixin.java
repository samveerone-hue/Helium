package com.helium.mixin.tick;

import com.helium.compat.CrossLoaderCompat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class ClientTickMixin {
    
    @Inject(method = "tick", at = @At("TAIL"))
    private void helium$ontickend(CallbackInfo ci) {
        if (!CrossLoaderCompat.isfabrictickavailable()) {
            CrossLoaderCompat.tick();
        }
    }
}
