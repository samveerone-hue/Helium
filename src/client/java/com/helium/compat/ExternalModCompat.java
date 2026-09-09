package com.helium.compat;

import net.fabricmc.loader.api.FabricLoader;

/** Detects optional mods that overlap with Helium's optimizations. */
public final class ExternalModCompat {
    private static final FabricLoader LOADER = FabricLoader.getInstance();

    private ExternalModCompat() {}

    public static boolean hasOxidizium() { return LOADER.isModLoaded("oxidizium"); }
    public static boolean hasAsyncParticles() { return LOADER.isModLoaded("asyncparticles"); }
    public static boolean hasFastServerPings() { return LOADER.isModLoaded("fastserverpings"); }
    public static boolean hasStableFps() { return LOADER.isModLoaded("stable-fps") || LOADER.isModLoaded("stablefps"); }

    public static boolean shouldUseHeliumFastMath() { return !hasOxidizium(); }
    public static boolean shouldUseHeliumServerPings() { return !hasFastServerPings(); }
    public static boolean shouldUseHeliumFastIpPing() { return !hasFastServerPings(); }
    public static boolean shouldUseHeliumFramePacing() { return !hasStableFps(); }
}
