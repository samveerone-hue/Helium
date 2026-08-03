package com.helium.crafting;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.inventory.Slot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

public final class OneClickCraftingManager {

    private static volatile boolean initialized = false;
    private static volatile boolean failed = false;

    private static volatile ItemStack lastcraft = null;
    private static volatile boolean isdropping = false;
    private static volatile boolean isshiftdropping = false;
    private static volatile boolean isshifting = false;
    private static volatile int lastbutton = -1;
    private static volatile boolean ispending = false;
    private static volatile Object lastingredient = null;
    private static volatile int lastselected = -1;
    private static volatile java.util.function.Consumer<ItemStack> onnextupdate = null;

    private OneClickCraftingManager() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        HeliumClient.LOGGER.info("one click crafting initialized");
    }

    public static boolean isinitialized() { return initialized; }

    public static void reset() {
        isdropping = false;
        isshiftdropping = false;
        isshifting = false;
        lastcraft = null;
        lastbutton = -1;
        ispending = false;
        lastingredient = null;
        onnextupdate = null;
    }

    public static boolean isenabled() {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.oneClickCrafting) return false;
        if (lastbutton == -1) return false;
        return true;
    }

    public static boolean haslastbutton() { return lastbutton != -1; }

    public static void setlastbutton(int button) {
        lastbutton = button;
    }

    public static void setlastcraft(ItemStack stack) {
        lastcraft = stack;
        ispending = true;
    }

    public static boolean ispending() { return ispending; }

    public static void recipeclicked(Object recipeId) {
        if (!isenabled()) {
            reset();
            return;
        }
        isdropping = CraftingInputHelper.isdropkeypressed();
        isshiftdropping = isdropping && CraftingInputHelper.isshiftdown();

        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null) return;
        LocalPlayer player = client.player;
        if (player == null) return;

        try {
            Map<?, ?> recipes = getrecipebook(player);
            if (recipes == null || recipeId == null) return;
            Object entry = recipes.get(recipeId);
            if (entry == null) return;
            ItemStack result = getresultfromentry(entry, world);
            if (result != null) {
                setlastcraft(result);
            }
        } catch (Throwable t) {
            if (!failed) {
                failed = true;
                HeliumClient.LOGGER.warn("[helium] one click crafting recipe click failed", t);
            }
        }
    }

    public static void onresultslotupdated(ItemStack itemStack) {
        if (lastcraft == null) return;
        if (itemStack.getItem() == Items.AIR) return;
        if (!ItemStack.isSameItem(itemStack, lastcraft)) return;

        Minecraft client = Minecraft.getInstance();
        if (client.gameMode == null) return;
        if (!(client.screen instanceof AbstractContainerScreen<?> gui)) return;

        if (isdropping) {
            if (isshiftdropping) {
                CraftingInventoryUtils.dropstack(gui, 0);
            } else {
                CraftingInventoryUtils.dropitem(gui, 0);
            }
        } else {
            CraftingInventoryUtils.shiftclickslot(gui, 0);
        }
        reset();
    }

    public static void stonecutterrecipeclicked(StonecutterScreen screen, int button, int selectedRecipe) {
        if (!isenabled()) {
            reset();
            return;
        }
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.oneClickCrafting) return;

        isdropping = CraftingInputHelper.isdropkeypressed();
        isshifting = CraftingInputHelper.isshiftdown();
        isshiftdropping = isdropping && isshifting;
        lastselected = selectedRecipe;

        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null) return;
        LocalPlayer player = client.player;
        if (player == null) return;

        try {
            StonecutterMenu handler = screen.getMenu();
            Object recipes = getstonerecipes(handler);
            if (recipes == null) return;
            ItemStack result = getstoneresult(recipes, selectedRecipe);
            if (result != null) {
                setlastcraft(result);
                lastingredient = getstoneingredient(recipes, selectedRecipe);
            }
        } catch (Throwable t) {
            if (!failed) {
                failed = true;
                HeliumClient.LOGGER.warn("[helium] one click crafting stonecutter click failed", t);
            }
        }
    }

    public static void onstonecutterresultupdated(ItemStack itemStack) {
        if (onnextupdate != null) {
            onnextupdate.accept(itemStack);
            return;
        }
        if (lastbutton == -1 || lastcraft == null) return;
        if (itemStack.getItem() == Items.AIR) return;
        if (!ItemStack.isSameItem(itemStack, lastcraft)) return;

        Minecraft client = Minecraft.getInstance();
        if (client.gameMode == null) return;
        if (!(client.screen instanceof AbstractContainerScreen<?> gui)) return;

        Slot input = CraftingInventoryUtils.getslot(gui, 0);
        if (isdropping) {
            if (isshifting) {
                if (input.getItem().getCount() != 64) {
                    CraftingInventoryUtils.movematchingintoslot(gui, 0);
                    gui.getMenu().clickMenuButton(client.player, lastselected);
                    client.gameMode.handleInventoryButtonClick(gui.getMenu().containerId, lastselected);
                    onnextupdate = (m) -> {
                        if (!ItemStack.isSameItem(m, lastcraft) || m.getItem() == Items.AIR) return;
                        onnextupdate = null;
                        CraftingInventoryUtils.dropstack(gui, 1);
                        refill(gui);
                    };
                    return;
                }
            } else {
                boolean shouldRefill = input.getItem().getCount() == 1;
                CraftingInventoryUtils.dropitem(gui, 1);
                CraftingInventoryUtils.leftclickslot(gui, 0);
                CraftingInventoryUtils.leftclickslot(gui, 0);
                if (shouldRefill) {
                    refill(gui);
                } else {
                    ispending = false;
                }
            }
        } else {
            if (isshifting) {
                if (input.getItem().getCount() != 64) {
                    CraftingInventoryUtils.movematchingintoslot(gui, 0);
                    gui.getMenu().clickMenuButton(client.player, lastselected);
                    client.gameMode.handleInventoryButtonClick(gui.getMenu().containerId, lastselected);
                    onnextupdate = (m) -> {
                        if (!ItemStack.isSameItem(m, lastcraft) || m.getItem() == Items.AIR) return;
                        onnextupdate = null;
                        CraftingInventoryUtils.shiftclickslot(gui, 1);
                        refill(gui);
                    };
                    return;
                } else {
                    CraftingInventoryUtils.shiftclickslot(gui, 1);
                    refill(gui);
                }
            } else {
                if (input.getItem().getCount() != 1) {
                    CraftingInventoryUtils.leftclickslot(gui, 0);
                    CraftingInventoryUtils.rightclickslot(gui, 0);
                    Minecraft mc = Minecraft.getInstance();
                    gui.getMenu().clickMenuButton(mc.player, lastselected);
                    mc.gameMode.handleInventoryButtonClick(gui.getMenu().containerId, lastselected);
                    onnextupdate = (next) -> {
                        if (!ItemStack.isSameItem(next, lastcraft) || next.getItem() == Items.AIR) return;
                        onnextupdate = null;
                        CraftingInventoryUtils.shiftclickslot(gui, 1);
                        CraftingInventoryUtils.leftclickslot(gui, 0);
                        ispending = false;
                    };
                    return;
                } else {
                    CraftingInventoryUtils.shiftclickslot(gui, 1);
                    refill(gui);
                }
            }
        }
        reset();
    }

    private static void refill(AbstractContainerScreen<?> gui) {
        if (lastingredient != null) {
            Optional<Slot> refill = CraftingInventoryUtils.findmatchingslot(gui, lastingredient);
            refill.ifPresent(slot -> {
                boolean multi = slot.getItem().getCount() > 1;
                CraftingInventoryUtils.leftclickslot(gui, slot);
                CraftingInventoryUtils.rightclickslot(gui, 0);
                if (multi) {
                    CraftingInventoryUtils.leftclickslot(gui, slot);
                }
            });
        }
        ispending = false;
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> getrecipebook(LocalPlayer player) {
        try {
            Object recipeBook = player.getRecipeBook();
            for (Field f : recipeBook.getClass().getDeclaredFields()) {
                if (Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return (Map<?, ?>) f.get(recipeBook);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static ItemStack getresultfromentry(Object entry, ClientLevel world) {
        try {
            Method displayMethod = null;
            for (Method m : entry.getClass().getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().contains("RecipeDisplay")) {
                    displayMethod = m;
                    break;
                }
            }
            if (displayMethod == null) return null;
            displayMethod.setAccessible(true);
            Object display = displayMethod.invoke(entry);

            Method resultMethod = null;
            for (Method m : display.getClass().getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().contains("SlotDisplay")) {
                    resultMethod = m;
                    break;
                }
            }
            if (resultMethod == null) return null;
            resultMethod.setAccessible(true);
            Object slotDisplay = resultMethod.invoke(display);

            Method getStacksMethod = null;
            for (Method m : slotDisplay.getClass().getMethods()) {
                if (m.getName().equals("getStacks") && m.getParameterCount() == 1) {
                    getStacksMethod = m;
                    break;
                }
            }
            if (getStacksMethod == null) return null;

            Class<?> slotDisplayContexts = Class.forName("net.minecraft.world.item.crafting.display.SlotDisplayContext");
            Method createParams = null;
            for (Method m : slotDisplayContexts.getDeclaredMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0].getSimpleName().contains("World")) {
                    createParams = m;
                    break;
                }
            }
            if (createParams == null) return null;
            createParams.setAccessible(true);
            Object params = createParams.invoke(null, world);

            Object stacks = getStacksMethod.invoke(slotDisplay, params);
            Method getFirst = stacks.getClass().getMethod("getFirst");
            return (ItemStack) getFirst.invoke(stacks);
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object getstonerecipes(StonecutterMenu handler) {
        try {
            Method m = handler.getClass().getMethod("getAvailableRecipes");
            return m.invoke(handler);
        } catch (Throwable ignored) {}
        return null;
    }

    private static ItemStack getstoneresult(Object recipes, int index) {
        try {
            Method entriesMethod = recipes.getClass().getMethod("entries");
            Object entries = entriesMethod.invoke(recipes);
            Method getMethod = entries.getClass().getMethod("get", int.class);
            Object group = getMethod.invoke(entries, index);

            Method recipeMethod = group.getClass().getMethod("recipe");
            Object recipe = recipeMethod.invoke(group);

            Method optionDisplay = recipe.getClass().getMethod("optionDisplay");
            Object display = optionDisplay.invoke(recipe);

            Field stackField = display.getClass().getDeclaredField("stack");
            stackField.setAccessible(true);
            return (ItemStack) stackField.get(display);
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object getstoneingredient(Object recipes, int index) {
        try {
            Method entriesMethod = recipes.getClass().getMethod("entries");
            Object entries = entriesMethod.invoke(recipes);
            Method getMethod = entries.getClass().getMethod("get", int.class);
            Object group = getMethod.invoke(entries, index);

            Method inputMethod = group.getClass().getMethod("input");
            return inputMethod.invoke(group);
        } catch (Throwable ignored) {}
        return null;
    }
}
