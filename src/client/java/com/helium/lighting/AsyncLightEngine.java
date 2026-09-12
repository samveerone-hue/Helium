package com.helium.lighting;

import com.helium.HeliumClient;

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
    private static final AtomicBoolean drainScheduled = new AtomicBoolean();
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
        drainScheduled.set(false);
        HeliumClient.LOGGER.info("async light preparation initialized (batch={})", maxPerBatch);
    }

    public static boolean isInitialized() { return initialized.get(); }

    /** Queues a block light invalidation and coalesces work into shared worker batches. */
    public static void queueBlock(long posKey) {
        if (!initialized.get()) return;
        if (queued.size() >= 4096) {
            dropped.incrementAndGet();
            return;
        }
        if (queued.putIfAbsent(posKey, Boolean.TRUE) != null) return;
        pending.offer(posKey);
        scheduleDrain();
    }

    private static void scheduleDrain() {
        ExecutorService pool = executor;
        if (pool == null || pool.isShutdown() || !initialized.get()) return;
        if (!drainScheduled.compareAndSet(false, true)) return;

        submitted.incrementAndGet();
        try {
            pool.execute(() -> {
                try {
                    int processed = 0;
                    Long key;
                    while (processed < maxPerBatch && (key = pending.poll()) != null) {
                        queued.remove(key);
                        prepared.offer(key);
                        preparedCount.incrementAndGet();
                        processed++;
                    }
                } finally {
                    drainScheduled.set(false);
                    // More work may have arrived while this batch was running. Schedule exactly
                    // one follow-up worker instead of one executor task per queued block.
                    if (!pending.isEmpty()) scheduleDrain();
                }
            });
        } catch (RuntimeException rejected) {
            drainScheduled.set(false);
            // Preserve the queued entries; a later queueBlock call can retry scheduling.
            HeliumClient.LOGGER.warn("async light preparation worker rejected task: {}", rejected.toString());
        }
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
    public static int getPreparedCount() { return Math.max(0, preparedCount.get()); }
    public static int getSubmittedCount() { return submitted.get(); }
    public static int getDroppedCount() { return dropped.get(); }

    public static synchronized void shutdown() {
        initialized.set(false);
        drainScheduled.set(false);
        ExecutorService pool = executor;
        executor = null;
        if (pool != null) pool.shutdownNow();
        pending.clear();
        prepared.clear();
        queued.clear();
        submitted.set(0);
        preparedCount.set(0);
        dropped.set(0);
    }
}
