package com.helium.render;

import com.helium.HeliumClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BlockStateModel;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Small front-cache for repeated BlockState -> BlockStateModel lookups. */
public final class ModelCache {
    private static final ConcurrentHashMap<BlockState, BlockStateModel> cache = new ConcurrentHashMap<>();
    private static volatile boolean initialized;
    private static volatile int maxEntries = 8192;
    private static final AtomicInteger hits = new AtomicInteger();
    private static final AtomicInteger misses = new AtomicInteger();

    private ModelCache() {}

    public static synchronized void init(int maxSizeMb) {
        int requested = Math.max(16, Math.min(512, maxSizeMb));
        maxEntries = Math.max(1024, (requested * 1024 * 1024) / 256);
        cache.clear();
        hits.set(0);
        misses.set(0);
        initialized = true;
        HeliumClient.LOGGER.info("experimental block model front-cache initialized (max {} entries)", maxEntries);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static BlockStateModel get(BlockState state) {
        if (!initialized || state == null) return null;
        BlockStateModel model = cache.get(state);
        if (model != null) {
            hits.incrementAndGet();
            return model;
        }
        misses.incrementAndGet();
        return null;
    }

    public static void put(BlockState state, BlockStateModel model) {
        if (!initialized || state == null || model == null) return;
        cache.put(state, model);
        if (cache.size() > maxEntries) {
            int remove = Math.max(1, cache.size() - maxEntries);
            var it = cache.keySet().iterator();
            while (remove-- > 0 && it.hasNext()) {
                it.next();
                it.remove();
            }
        }
    }

    public static void invalidate(BlockState state) {
        if (initialized && state != null) cache.remove(state);
    }

    public static void invalidateAll() {
        if (!initialized) return;
        cache.clear();
        hits.set(0);
        misses.set(0);
    }

    public static int size() {
        return cache.size();
    }

    public static int getHits() {
        return hits.get();
    }

    public static int getMisses() {
        return misses.get();
    }

    public static float getHitRate() {
        int total = hits.get() + misses.get();
        return total == 0 ? 0.0f : (float) hits.get() / total;
    }
}
