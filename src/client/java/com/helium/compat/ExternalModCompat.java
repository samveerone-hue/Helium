package com.helium.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Detects optional optimization mods that replace functionality also provided
 * by Helium. Helium keeps its fallback implementations available when these
 * mods are absent, but avoids installing overlapping hooks when they are present.
 */
public final class ExternalModCompat {

    private static final FabricLoader LOADER = FabricLoader.getInstance();

    private ExternalModCompat() {}

    public static boolean hasOxidizium() {
        return LOADER.isModLoaded("oxidizium");
    }

    public static boolean hasFastServerPings() {
        return LOADER.isModLoaded("fastserverpings");
    }

    public static boolean shouldUseHeliumFastMath() {
        return !hasOxidizium();
    }

    public static boolean shouldUseHeliumServerPings() {
        return !hasFastServerPings();
    }

    public static boolean shouldUseHeliumFastIpPing() {
        return !hasFastServerPings();
    }
}
