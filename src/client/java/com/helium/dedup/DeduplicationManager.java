package com.helium.dedup;

import com.helium.HeliumClient;
import it.unimi.dsi.fastutil.Hash;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DeduplicationManager {

    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    public static final Deduplicator<String> NAMESPACES = new Deduplicator<>();
    public static final Deduplicator<String> PATHS = new Deduplicator<>();
    public static final Deduplicator<String> PROPERTIES = new Deduplicator<>();

    public static final Deduplicator<int[]> QUADS = new Deduplicator<>(new Hash.Strategy<>() {
        @Override
        public int hashCode(int[] ints) {
            return Arrays.hashCode(ints);
        }

        @Override
        public boolean equals(int[] a, int[] b) {
            return Arrays.equals(a, b);
        }
    });

    public static final Deduplicator<Identifier> KEY_REGISTRY = new Deduplicator<>();
    public static final Deduplicator<Identifier> KEY_LOCATION = new Deduplicator<>();

    private DeduplicationManager() {}

    public static void init() {
        if (initialized.getAndSet(true)) return;
        HeliumClient.LOGGER.info("deduplication manager initialized");
    }

    public static boolean isinitialized() {
        return initialized.get();
    }

    public static boolean isenabled() {
        try {
            return initialized.get() && HeliumClient.getConfig() != null && HeliumClient.getConfig().objectDeduplication;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void clearcaches() {
        QUADS.clearcache();
        KEY_REGISTRY.clearcache();
        KEY_LOCATION.clearcache();
        logstats();
    }

    public static void logstats() {
        HeliumClient.LOGGER.info("==deduplication stats==");
        HeliumClient.LOGGER.info("namespaces: {}", NAMESPACES);
        HeliumClient.LOGGER.info("paths: {}", PATHS);
        HeliumClient.LOGGER.info("properties: {}", PROPERTIES);
        HeliumClient.LOGGER.info("quads: {}", QUADS);
        HeliumClient.LOGGER.info("registry keys: {}", KEY_REGISTRY);
        HeliumClient.LOGGER.info("key locations: {}", KEY_LOCATION);
    }
}
