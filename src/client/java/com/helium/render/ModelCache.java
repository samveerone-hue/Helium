package com.helium.render;

import com.helium.HeliumClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BlockStateModel;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Small front-cache for repeated BlockState -> BlockStateModel lookups. */
public final class ModelCache {
    private static final class Entry {
        final BlockState state;
        final BlockStateModel model;

        Entry(BlockState state, BlockStateModel model) {
            this.state = state;
            this.model = model;
        }
    }

    private static final ConcurrentHashMap<BlockState, Entry> cache = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Entry> evictionQueue = new ConcurrentLinkedQueue<>();
    private static volatile boolean initialized;
    private static volatile int maxEntries = 8192;
    private static final AtomicInteger hits = new AtomicInteger();
    private static final AtomicInteger misses = new AtomicInteger();

    private ModelCache() {}

    public static synchronized void init(int maxSizeMb) {
        int requested = Math.max(16, Math.min(512, maxSizeMb));
        // BlockStateModel does not expose a retained-size API, so the setting remains an
        // intentionally conservative estimated memory budget rather than a fake exact byte cap.
        maxEntries = Math.max(1024, (requested * 1024 * 1024) / 256);
        cache.clear();
        evictionQueue.clear();
        hits.set(0);
        misses.set(0);
        initialized = true;
        HeliumClient.LOGGER.info("experimental block model front-cache initialized (estimated max {} entries from {} MB budget)",
                maxEntries, requested);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static BlockStateModel get(BlockState state) {
        if (!initialized || state == null) return null;
        Entry entry = cache.get(state);
        if (entry != null) {
            hits.incrementAndGet();
            return entry.model;
        }
        misses.incrementAndGet();
        return null;
    }

    public static void put(BlockState state, BlockStateModel model) {
        if (!initialized || state == null || model == null) return;
        Entry entry = new Entry(state, model);
        Entry existing = cache.putIfAbsent(state, entry);
        if (existing != null) return;
        evictionQueue.offer(entry);
        trim();
    }

    private static void trim() {
        int excess = cache.size() - maxEntries;
        while (excess-- > 0) {
            Entry oldest = evictionQueue.poll();
            if (oldest == null) return;
            // Conditional removal prevents a stale queue entry from evicting a newer value
            // for the same BlockState after invalidation/reinsertion.
            cache.remove(oldest.state, oldest);
        }
    }

    public static void invalidate(BlockState state) {
        if (initialized && state != null) cache.remove(state);
    }

    public static void invalidateAll() {
        if (!initialized) return;
        cache.clear();
        evictionQueue.clear();
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
