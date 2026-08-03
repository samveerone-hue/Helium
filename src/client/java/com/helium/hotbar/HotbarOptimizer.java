package com.helium.hotbar;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class HotbarOptimizer {

    private static final AtomicBoolean sent = new AtomicBoolean(false);
    private static final AtomicInteger lastslot = new AtomicInteger(-1);
    private static volatile boolean serverdisabled = false;

    private HotbarOptimizer() {}

    public static void resettick() {
        sent.set(false);
    }

    public static void resetslot() {
        lastslot.set(-1);
    }

    public static void setserverdisabled(boolean disabled) {
        serverdisabled = disabled;
    }

    public static boolean isserverdisabled() {
        return serverdisabled;
    }

    public static void syncslot(Minecraft client, int slot) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.hotbarOptimizer) return;
        if (serverdisabled) return;

        if (sent.get() && !config.hotbarMultiSwitch) return;

        LocalPlayer player = client.player;
        if (player == null) return;
        MultiPlayerGameMode im = client.gameMode;
        if (im == null) return;
        if (player.hasInfiniteMaterials()) return;

        setselectedslot(player, slot);
        lastslot.set(slot);

        try {
            java.lang.reflect.Method m = MultiPlayerGameMode.class.getDeclaredMethod("syncSelectedSlot");
            m.setAccessible(true);
            m.invoke(im);
        } catch (NoSuchMethodException e) {
            try {
                String mapped = net.fabricmc.loader.api.FabricLoader.getInstance()
                        .getMappingResolver()
                        .mapMethodName("intermediary",
                                "net.minecraft.class_636",
                                "method_2923",
                                "()V");
                java.lang.reflect.Method m = MultiPlayerGameMode.class.getDeclaredMethod(mapped);
                m.setAccessible(true);
                m.invoke(im);
            } catch (Throwable t) {
                HeliumClient.LOGGER.warn("hotbar sync failed ({})", t.getClass().getSimpleName());
            }
        } catch (Throwable t) {
            HeliumClient.LOGGER.warn("hotbar sync failed ({})", t.getClass().getSimpleName());
        }

        sent.set(true);
    }

    public static void checkscrollsync(Minecraft client) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.hotbarOptimizer) return;
        if (serverdisabled) return;
        if (client.isLocalServer()) return;

        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) return;
        if (player.hasInfiniteMaterials()) return;

        int current = getselectedslot(player);
        if (current < 0) return;
        if (current != lastslot.get()) {
            lastslot.set(current);
            syncslot(client, current);
        }
    }

    private static volatile java.lang.reflect.Field _slotfield;
    private static volatile boolean _slotfieldresolved;

    private static java.lang.reflect.Field resolveslotfield(Object inventory) {
        if (_slotfieldresolved) return _slotfield;
        try {
            String mapped = net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getMappingResolver()
                    .mapFieldName("intermediary",
                            "net.minecraft.class_1661",
                            "field_7545",
                            "I");
            java.lang.reflect.Field f = inventory.getClass().getField(mapped);
            f.setAccessible(true);
            _slotfield = f;
        } catch (Throwable t) {
            HeliumClient.LOGGER.warn("could not resolve selectedSlot field");
        }
        _slotfieldresolved = true;
        return _slotfield;
    }

    private static int getselectedslot(LocalPlayer player) {
        try {
            return player.getInventory().getSelectedSlot();
        } catch (NoSuchMethodError e) {
            java.lang.reflect.Field f = resolveslotfield(player.getInventory());
            if (f == null) return -1;
            try {
                return f.getInt(player.getInventory());
            } catch (Throwable t) {
                return -1;
            }
        }
    }

    private static void setselectedslot(LocalPlayer player, int slot) {
        try {
            player.getInventory().setSelectedSlot(slot);
        } catch (NoSuchMethodError e) {
            java.lang.reflect.Field f = resolveslotfield(player.getInventory());
            if (f == null) return;
            try {
                f.setInt(player.getInventory(), slot);
            } catch (Throwable t) {
                HeliumClient.LOGGER.warn("setselectedslot failed");
            }
        }
    }
}
