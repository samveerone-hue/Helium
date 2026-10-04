package com.helium.compute;

import com.helium.HeliumClient;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GpuComputeManager {
    @FunctionalInterface public interface SolidSampler { boolean isSolid(int x, int y, int z); }
    private record Key(int source, int target) {}
    private record Request(Key key, float[] rays, int rayCount, long tick, long generation, SolidSampler sampler) {}
    private record Result(boolean visible, long tick, long generation) {}
    private static final int MAX_CACHED_RESULTS = 16_384;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Helium-GPU-Compute");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final Object BACKEND_LOCK = new Object();
    private static final ConcurrentMap<Key, Request> pending = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Key, Result> results = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Key, Long> lastVisible = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Key> resultOrder = new ConcurrentLinkedQueue<>();
    private static volatile GpuComputeConfig config;
    private static volatile OpenClComputeBackend backend;
    private static volatile long lastFlush = Long.MIN_VALUE;
    private static volatile long generation;
    private static volatile long backendRetryAfterNanos;
    private static final long BACKEND_RETRY_DELAY_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final AtomicBoolean backendInitializationScheduled = new AtomicBoolean();
    private static final AtomicBoolean losBatchInFlight = new AtomicBoolean();
    private static final int MAX_PENDING_REQUESTS = 4096;

    private GpuComputeManager() {}

    private static GpuComputeConfig getConfig() {
        GpuComputeConfig cached = config;
        if (cached != null) return cached;
        synchronized (GpuComputeManager.class) {
            cached = config;
            if (cached == null) config = cached = GpuComputeConfig.load();
            return cached;
        }
    }

    public static void initializeConfiguration() {
        getConfig();
    }

    public static void updateConfig(GpuComputeConfig updated) {
        if (updated == null) return;
        config = updated;
        backendRetryAfterNanos = 0L;
        if (!updated.enabled || (!updated.lineOfSight && !updated.pathfinding)) closeBackend();
    }

    private static boolean ensureBackend(boolean wanted) {
        if (!wanted) {
            if (backend != null || !pending.isEmpty() || !results.isEmpty()) closeBackend();
            else backendRetryAfterNanos = 0L;
            return false;
        }
        if (backend != null) return true;
        if (System.nanoTime() < backendRetryAfterNanos) return false;

        GpuComputeConfig expectedConfig = getConfig();
        if (!expectedConfig.enabled || (!expectedConfig.lineOfSight && !expectedConfig.pathfinding)) return false;
        if (!backendInitializationScheduled.compareAndSet(false, true)) return false;
        long expectedGeneration = generation;
        try {
            EXECUTOR.execute(() -> {
                OpenClComputeBackend created = null;
                boolean installed = false;
                try {
                    if (generation == expectedGeneration && config == expectedConfig
                            && expectedConfig.enabled
                            && (expectedConfig.lineOfSight || expectedConfig.pathfinding)) {
                        created = OpenClComputeBackend.create();
                    }
                    if (created != null) {
                        synchronized (BACKEND_LOCK) {
                            if (backend == null && generation == expectedGeneration && config == expectedConfig
                                    && expectedConfig.enabled
                                    && (expectedConfig.lineOfSight || expectedConfig.pathfinding)) {
                                backend = created;
                                created = null;
                                installed = true;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (created != null) {
                        try { created.close(); } catch (Throwable ignored) {}
                    }
                    if (!installed && backend == null && generation == expectedGeneration && config == expectedConfig
                            && expectedConfig.enabled
                            && (expectedConfig.lineOfSight || expectedConfig.pathfinding)) {
                        backendRetryAfterNanos = System.nanoTime() + BACKEND_RETRY_DELAY_NANOS;
                    }
                    backendInitializationScheduled.set(false);
                }
            });
        } catch (RuntimeException rejected) {
            backendInitializationScheduled.set(false);
            backendRetryAfterNanos = System.nanoTime() + BACKEND_RETRY_DELAY_NANOS;
        }
        return backend != null;
    }

    private static void closeBackend() {
        synchronized (BACKEND_LOCK) {
            OpenClComputeBackend b = backend;
            backend = null;
            generation++;
            if (b != null) {
                try { b.close(); } catch (Throwable ignored) {}
            }
        }
        pending.clear();
        results.clear();
        lastVisible.clear();
        resultOrder.clear();
        lastFlush = Long.MIN_VALUE;
        backendRetryAfterNanos = 0L;
    }

    public static void clearWorldState() {
        generation++;
        pending.clear();
        results.clear();
        lastVisible.clear();
        resultOrder.clear();
        lastFlush = Long.MIN_VALUE;
    }

    public static boolean lineOfSightEnabled() {
        GpuComputeConfig c = getConfig();
        return c.enabled && c.lineOfSight && ensureBackend(true);
    }

    public static boolean lineOfSightConfigured() {
        GpuComputeConfig c = getConfig();
        return c.enabled && c.lineOfSight;
    }

    public static boolean pathfindingEnabled() {
        GpuComputeConfig c = getConfig();
        return c.enabled && c.pathfinding && ensureBackend(true);
    }

    public static Boolean cached(int source, int target, long tick) {
        return cached(source, target, tick, 0.0D);
    }

    /**
     * Returns the most recent GPU visibility answer. Negative answers are subject to a small
     * distance-scaled grace window after the entity was last confirmed visible, preventing
     * camera-grazing occlusion from flickering an entity on and off.
     */
    public static Boolean cached(int source, int target, long tick, double distanceSq) {
        GpuComputeConfig c = getConfig();
        Key key = new Key(source, target);
        Result r = results.get(key);
        if (r == null || r.generation != generation) return null;
        if (tick - r.tick > Math.max(1, c.refreshTicks)) return null;
        if (r.visible) return true;

        Long positive = lastVisible.get(key);
        if (positive == null) return false;
        return tick - positive <= occlusionGraceFrames(distanceSq);
    }

    private static int occlusionGraceFrames(double distanceSq) {
        if (distanceSq <= 32.0D * 32.0D) return 4;
        if (distanceSq <= 64.0D * 64.0D) return 8;
        return 12;
    }

    public static void requestLineOfSight(int source, int target, float ox, float oy, float oz,
                                          float tx, float ty, float tz, long tick, SolidSampler sampler) {
        requestLineOfSightMulti(source, target, new float[]{ox, oy, oz, tx, ty, tz}, tick, sampler);
    }

    public static void requestLineOfSightMulti(int source, int target, float[] rays, long tick, SolidSampler sampler) {
        if (!lineOfSightEnabled() || source == target || rays == null || sampler == null || rays.length < 6) return;
        if ((rays.length % 6) != 0) return;
        if (cached(source, target, tick, 0.0D) != null) return;

        Key key = new Key(source, target);
        int maxAge = Math.max(1, getConfig().refreshTicks);
        Request existing = pending.get(key);
        if (existing != null && (existing.generation != generation || tick - existing.tick > maxAge)) {
            pending.remove(key, existing);
        }
        if (pending.size() >= MAX_PENDING_REQUESTS && !pending.containsKey(key)) return;
        long requestGeneration = generation;
        pending.putIfAbsent(key, new Request(key, rays, rays.length / 6, tick, requestGeneration, sampler));
        flush(tick - 1);
    }

    private static void flush(long tick) {
        if (tick == Long.MIN_VALUE || lastFlush == tick || losBatchInFlight.get()) return;
        lastFlush = tick;
        ArrayList<Request> batch = new ArrayList<>();
        int max = Math.max(1, config.maxBatch);
        int maxAge = Math.max(1, config.refreshTicks);
        SolidSampler batchSampler = null;

        for (Request r : pending.values()) {
            if (r.generation != generation) {
                pending.remove(r.key, r);
                continue;
            }
            if (r.tick > tick) continue;
            if (tick - r.tick > maxAge) {
                pending.remove(r.key, r);
                continue;
            }
            if (batchSampler != null && r.sampler != batchSampler) continue;
            if (batch.size() >= max || !pending.remove(r.key, r)) continue;
            if (batchSampler == null) batchSampler = r.sampler;
            batch.add(r);
        }
        if (batch.isEmpty()) return;
        if (backend == null) {
            for (Request r : batch) pending.putIfAbsent(r.key, r);
            return;
        }
        long batchGeneration = generation;

        int totalRays = 0;
        for (Request r : batch) totalRays += r.rayCount;
        final int expectedRayCount = totalRays;
        float[] rays = new float[totalRays * 6];
        int rayCursor = 0;
        for (Request r : batch) {
            System.arraycopy(r.rays, 0, rays, rayCursor * 6, r.rayCount * 6);
            rayCursor += r.rayCount;
        }

        GpuComputeMath.Grid grid = GpuComputeMath.computeGrid(rays, config.gridSize);
        if (grid == null) {
            markVisibleFallback(batch, batchGeneration);
            return;
        }

        final int snapshotMinX = grid.minX();
        final int snapshotMinY = grid.minY();
        final int snapshotMinZ = grid.minZ();
        final int snapshotSize = grid.size();
        byte[] solid = new byte[snapshotSize * snapshotSize * snapshotSize];
        int i = 0;
        try {
            for (int z = 0; z < snapshotSize; z++) for (int y = 0; y < snapshotSize; y++) for (int xx = 0; xx < snapshotSize; xx++, i++) {
                solid[i] = (byte) (batchSampler.isSolid(snapshotMinX + xx, snapshotMinY + y, snapshotMinZ + z) ? 1 : 0);
            }
        } catch (Throwable t) {
            HeliumClient.LOGGER.debug("gpu compute world snapshot failed", t);
            markVisibleFallback(batch, batchGeneration);
            return;
        }

        final OpenClComputeBackend b;
        synchronized (BACKEND_LOCK) {
            b = backend;
            if (b == null) {
                markVisibleFallback(batch, batchGeneration);
                return;
            }
        }
        if (!losBatchInFlight.compareAndSet(false, true)) {
            for (Request request : batch) pending.putIfAbsent(request.key, request);
            return;
        }
        try {
            EXECUTOR.execute(() -> {
                try {
                    synchronized (BACKEND_LOCK) {
                        if (b != backend || batchGeneration != generation) return;
                        try {
                            boolean[] values = b.runLineOfSight(rays, solid, snapshotSize,
                                    snapshotMinX, snapshotMinY, snapshotMinZ);
                            if (batchGeneration != generation) return;
                            if (values == null || values.length != expectedRayCount) {
                                markVisibleFallback(batch, batchGeneration);
                                return;
                            }

                            int valueCursor = 0;
                            for (Request r : batch) {
                                boolean visible = false;
                                for (int n = 0; n < r.rayCount; n++, valueCursor++) {
                                    if (values[valueCursor]) visible = true;
                                }
                                if (r.generation == generation) {
                                    storeResult(r.key, new Result(visible, r.tick, generation));
                                    if (visible) lastVisible.put(r.key, r.tick);
                                }
                            }
                        } catch (Throwable t) {
                            HeliumClient.LOGGER.debug("gpu line-of-sight batch failed", t);
                            markVisibleFallback(batch, batchGeneration);
                        }
                    }
                } finally {
                    losBatchInFlight.set(false);
                }
            });
        } catch (RuntimeException rejected) {
            losBatchInFlight.set(false);
            markVisibleFallback(batch, batchGeneration);
        }
    }

    public static int[] runFlowField(byte[] blocked, int size, int targetX, int targetY, int targetZ) {
        if (!pathfindingEnabled()) return null;
        synchronized (BACKEND_LOCK) {
            OpenClComputeBackend b = backend;
            if (b == null) return null;
            try { return b.runFlowField(blocked, size, targetX, targetY, targetZ); }
            catch (Throwable t) {
                HeliumClient.LOGGER.debug("gpu flow-field failed", t);
                return null;
            }
        }
    }

    private static void markVisibleFallback(ArrayList<Request> batch, long requestGeneration) {
        if (requestGeneration != generation) return;
        for (Request request : batch) {
            if (request.generation != requestGeneration) continue;
            storeResult(request.key, new Result(true, request.tick, requestGeneration));
            lastVisible.put(request.key, request.tick);
        }
    }

    private static void storeResult(Key key, Result result) {
        if (results.put(key, result) == null) resultOrder.offer(key);
        while (results.size() > MAX_CACHED_RESULTS) {
            Key oldest = resultOrder.poll();
            if (oldest == null) return;
            results.remove(oldest);
            lastVisible.remove(oldest);
        }
    }
}
