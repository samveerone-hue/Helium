package com.helium.crafting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.core.NonNullList;

import java.lang.reflect.Method;
import java.util.Optional;

public final class CraftingInventoryUtils {

    private static Method onmouseclickmethod = null;
    private static boolean onmouseclickresolved = false;

    private CraftingInventoryUtils() {}

    public static void clickslot(AbstractContainerScreen<? extends AbstractContainerMenu> gui, int slotNum, int mouseButton, ContainerInput type) {
        if (slotNum >= 0 && slotNum < gui.getMenu().slots.size()) {
            Slot slot = gui.getMenu().getSlot(slotNum);
            clickslot(gui, slot, slotNum, mouseButton, type);
        } else {
            Minecraft mc = Minecraft.getInstance();
            MultiPlayerGameMode gameMode = mc.gameMode;
            if (gameMode != null) {
                gameMode.handleContainerInput(gui.getMenu().containerId, slotNum, mouseButton, type, mc.player);
            }
        }
    }

    public static void clickslot(AbstractContainerScreen<? extends AbstractContainerMenu> gui, Slot slot, int slotNum, int mouseButton, ContainerInput type) {
        try {
            if (!onmouseclickresolved) {
                onmouseclickresolved = true;
                for (Method m : AbstractContainerScreen.class.getDeclaredMethods()) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 4 && params[0] == Slot.class && params[1] == int.class
                            && params[2] == int.class && params[3] == ContainerInput.class) {
                        m.setAccessible(true);
                        onmouseclickmethod = m;
                        break;
                    }
                }
            }
            if (onmouseclickmethod != null) {
                onmouseclickmethod.invoke(gui, slot, slotNum, mouseButton, type);
            }
        } catch (Throwable t) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameMode != null) {
                mc.gameMode.handleContainerInput(gui.getMenu().containerId, slotNum, mouseButton, type, mc.player);
            }
        }
    }

    public static void leftclickslot(AbstractContainerScreen<? extends AbstractContainerMenu> gui, Slot slot) {
        clickslot(gui, slot, slot.index, 0, ContainerInput.PICKUP);
    }

    public static void leftclickslot(AbstractContainerScreen<? extends AbstractContainerMenu> gui, int slotNum) {
        clickslot(gui, slotNum, 0, ContainerInput.PICKUP);
    }

    public static void movematchingintoslot(AbstractContainerScreen<? extends AbstractContainerMenu> gui, int slotNum) {
        clickslot(gui, slotNum, 0, ContainerInput.PICKUP);
        clickslot(gui, slotNum, 0, ContainerInput.PICKUP_ALL);
        clickslot(gui, slotNum, 0, ContainerInput.PICKUP);
    }

    public static void rightclickslot(AbstractContainerScreen<? extends AbstractContainerMenu> gui, Slot slot) {
        clickslot(gui, slot, slot.index, 1, ContainerInput.PICKUP);
    }

    public static void rightclickslot(AbstractContainerScreen<? extends AbstractContainerMenu> gui, int slotNum) {
        clickslot(gui, slotNum, 1, ContainerInput.PICKUP);
    }

    public static void shiftclickslot(AbstractContainerScreen<? extends AbstractContainerMenu> gui, Slot slot) {
        clickslot(gui, slot, slot.index, 0, ContainerInput.QUICK_MOVE);
    }

    public static void shiftclickslot(AbstractContainerScreen<? extends AbstractContainerMenu> gui, int slotNum) {
        clickslot(gui, slotNum, 0, ContainerInput.QUICK_MOVE);
    }

    public static void dropitemsfromcursor(AbstractContainerScreen<? extends AbstractContainerMenu> gui) {
        clickslot(gui, -999, 0, ContainerInput.PICKUP);
    }

    public static void dropitem(AbstractContainerScreen<? extends AbstractContainerMenu> gui, int slotNum) {
        clickslot(gui, slotNum, 0, ContainerInput.THROW);
    }

    public static void dropstack(AbstractContainerScreen<? extends AbstractContainerMenu> gui, int slotNum) {
        clickslot(gui, slotNum, 1, ContainerInput.THROW);
    }

    public static Slot getslot(AbstractContainerScreen<? extends AbstractContainerMenu> gui, int slotNum) {
        return gui.getMenu().getSlot(slotNum);
    }

    @SuppressWarnings("unchecked")
    public static Optional<Slot> findmatchingslot(AbstractContainerScreen<? extends AbstractContainerMenu> gui, Object ingredient) {
        NonNullList<Slot> slots = gui.getMenu().slots;
        for (Slot slot : slots) {
            if (!(slot.container instanceof Inventory)) continue;
            try {
                Class<?> ingredientClass = Class.forName("net.minecraft.world.item.crafting.Ingredient");
                Method matchesMethod = ingredientClass.getMethod("matches", Optional.class, net.minecraft.world.item.ItemStack.class);
                Boolean matches = (Boolean) matchesMethod.invoke(null, Optional.of(ingredient), slot.getItem());
                if (matches) return Optional.of(slot);
            } catch (Throwable t) {
                continue;
            }
        }
        return Optional.empty();
    }

    public static Optional<Slot> findemptyslot(AbstractContainerScreen<? extends AbstractContainerMenu> gui) {
        NonNullList<Slot> slots = gui.getMenu().slots;
        for (Slot slot : slots) {
            if (!(slot.container instanceof Inventory)) continue;
            if (!slot.getItem().is(Items.AIR)) continue;
            return Optional.of(slot);
        }
        return Optional.empty();
    }
}
