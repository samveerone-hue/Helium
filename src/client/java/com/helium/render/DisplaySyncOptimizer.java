package com.helium.render;

/**
 * Compatibility shim for legacy configuration code.
 *
 * Display synchronization is no longer throttled by Helium on 1.21.11.
 * The legacy options UI still calls reset(), so keep the API as a no-op
 * until those old settings are removed from the shared options model.
 */
public final class DisplaySyncOptimizer {

    private DisplaySyncOptimizer() {}

    public static void reset() {
        // Display synchronization is intentionally unmanaged by Helium.
    }
}
