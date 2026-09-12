package com.helium.lighting;

import com.helium.HeliumClient;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Off-thread light-update preparation/coalescing. Vanilla LightingProvider remains the owner of
 * light propagation and is never touched from a worker thread.
 */
public final class AsyncLightEngine {
    private static ExecutorService executor;
    private static final AtomicBoolean initialized = new AtomicBoolean();
    private static final ConcurrentLinkedQueue<Long> pending = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<Long> prepared = new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<Long, Boolean> queued = new ConcurrentHashMap<>();
    private static final AtomicInteger submitted = new AtomicInteger();
    private static final AtomicInteger preparedCount = new AtomicInteger();
    private static final AtomicInteger dropped = new AtomicInteger();
    private static volatile int maxPerBatch = 256;

    private AsyncLightEngine() {}

    public static synchronized void init(int configuredMaxPerTick) {
        if (initialized.get()) return;
        maxPerBatch = Math.max(8, Math.min(256, configuredMaxPerTick));
        executor = Executors.newFixedThreadPool(
                Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() / 4)),
                r -> {
                    Thread t = new Thread(r, "helium-light-prepare");
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY - 2);
                    return t;
                });
        initialized.set(true);
        HeliumClient.LOGGER.info("async light preparation initialized (batch={})", maxPerBatch);
    }

    public static boolean isInitialized() { return initialized.get(); }

    /** Queues a block light invalidation and schedules a worker-side deduplication pass. */
    public static void queueBlock(long posKey) {
        if (!initialized.get()) return;
        if (queued.size() >= 4096) {
            dropped.incrementAndGet();
            return;
        }
        if (queued.putIfAbsent(posKey, Boolean.TRUE) != null) return;
        pending.offer(posKey);
        submitted.incrementAndGet();
        scheduleDrain();
    }

    private static void scheduleDrain() {
        ExecutorService pool = executor;
        if (pool == null || pool.isShutdown()) return;
        pool.execute(() -> {
            int processed = 0;
            HashSet<Long> local = new HashSet<>();
            Long key;
            while (processed < maxPerBatch && (key = pending.poll()) != null) {
                queued.remove(key);
                local.add(key);
                processed++;
            }
            for (Long value : local) prepared.offer(value);
            preparedCount.addAndGet(local.size());
        });
    }

    /** Drains the prepared work on the owner thread without modifying vanilla lighting state. */
    public static int drainPrepared() {
        if (!initialized.get()) return 0;
        int drained = 0;
        while (drained < maxPerBatch && prepared.poll() != null) drained++;
        preparedCount.addAndGet(-drained);
        return drained;
    }

    public static int getPendingCount() { return pending.size(); }
    public static int getPreparedCount() { return preparedCount.get(); }
    public static int getSubmittedCount() { return submitted.get(); }
    public static int getDroppedCount() { return dropped.get(); }

    public static void shutdown() {
        ExecutorService pool = executor;
        if (pool != null) pool.shutdownNow();
        pending.clear();
        prepared.clear();
        queued.clear();
        submitted.set(0);
        preparedCount.set(0);
        initialized.set(false);
        executor = null;
    }
}
