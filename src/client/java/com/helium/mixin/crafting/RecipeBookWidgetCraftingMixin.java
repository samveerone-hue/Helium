package com.helium.mixin.crafting;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.crafting.OneClickCraftingManager;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookWidgetCraftingMixin {

    @Inject(at = @At("TAIL"), method = "select(Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;Lnet/minecraft/world/item/crafting/display/RecipeDisplayId;Z)Z", require = 0)
    private void helium$clickRecipeTail(RecipeCollection results, RecipeDisplayId recipeId, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.oneClickCrafting) return;
        if (!OneClickCraftingManager.isinitialized()) return;
        if (!OneClickCraftingManager.haslastbutton()) {
            OneClickCraftingManager.setlastbutton(1);
        }
        OneClickCraftingManager.recipeclicked(recipeId);
    }
}
