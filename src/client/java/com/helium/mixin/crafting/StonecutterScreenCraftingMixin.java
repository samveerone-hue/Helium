package com.helium.mixin.crafting;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.crafting.OneClickCraftingManager;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StonecutterScreen.class)
public abstract class StonecutterScreenCraftingMixin {
    @Inject(method = "onButtonClick", at = @At("RETURN"), require = 0)
    private void helium$onButtonClick(int id, CallbackInfo ci) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.oneClickCrafting || !OneClickCraftingManager.isinitialized()) return;
        StonecutterScreen screen = (StonecutterScreen) (Object) this;
        int selectedRecipe = screen.getMenu().getSelectedRecipeIndex();
        if (selectedRecipe >= 0) {
            OneClickCraftingManager.setlastbutton(1);
            OneClickCraftingManager.stonecutterrecipeclicked(screen, 1, selectedRecipe);
        }
    }
}
