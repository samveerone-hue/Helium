package com.helium.compute;

import com.helium.HeliumClient;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GpuComputeManager {
    @FunctionalInterface public interface SolidSampler { boolean isSolid(int x, int y, int z); }
    private record Key(int source, int target) {}
    private record Request(Key key, float[] rays, int rayCount, long tick, long generation, SolidSampler sampler) {}
    private record Result(boolean visible, long tick, long generation) {}
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
    private static volatile GpuComputeConfig config;
    private static volatile OpenClComputeBackend backend;
    private static volatile long lastFlush = Long.MIN_VALUE;
    private static volatile long generation;

    private GpuComputeManager() {}

    private static synchronized boolean enabled() {
        GpuComputeConfig c = config == null ? (config = GpuComputeConfig.load()) : config;
        boolean wanted = c.enabled && (c.lineOfSight || c.pathfinding);
        if (!wanted) {
            if (backend != null || !pending.isEmpty() || !results.isEmpty()) closeBackend();
            return false;
        }
        if (backend == null) {
            synchronized (BACKEND_LOCK) {
                if (backend == null) backend = OpenClComputeBackend.create();
            }
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
        lastFlush = Long.MIN_VALUE;
    }

    public static void clearWorldState() {
        pending.clear();
        results.clear();
        lastVisible.clear();
        lastFlush = Long.MIN_VALUE;
        generation++;
    }

    public static boolean lineOfSightEnabled() { return enabled() && config.lineOfSight; }
    public static boolean pathfindingEnabled() { return enabled() && config.pathfinding; }

    public static Boolean cached(int source, int target, long tick) {
        return cached(source, target, tick, 0.0D);
    }

    /**
     * Returns the most recent GPU visibility answer. Negative answers are subject to a small
     * distance-scaled grace window after the entity was last confirmed visible, preventing
     * camera-grazing occlusion from flickering an entity on and off.
     */
    public static Boolean cached(int source, int target, long tick, double distanceSq) {
        GpuComputeConfig c = config == null ? (config = GpuComputeConfig.load()) : config;
        Result r = results.get(new Key(source, target));
        if (r == null || r.generation != generation) return null;
        if (tick - r.tick > Math.max(1, c.refreshTicks)) return null;
        if (r.visible) return true;

        Long positive = lastVisible.get(new Key(source, target));
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
        long requestGeneration = generation;
        pending.putIfAbsent(key, new Request(key, rays, rays.length / 6, tick, requestGeneration, sampler));
        flush(tick - 1);
    }

    private static void flush(long tick) {
        if (tick == Long.MIN_VALUE || lastFlush == tick) return;
        lastFlush = tick;
        ArrayList<Request> batch = new ArrayList<>();
        int max = Math.max(1, config.maxBatch);
        for (Request r : pending.values()) {
            if (r.tick == tick && r.generation == generation && batch.size() < max && pending.remove(r.key, r)) {
                batch.add(r);
            }
        }
        if (batch.isEmpty() || backend == null) return;

        // Build the smallest axis-aligned snapshot that contains every ray endpoint,
        // then pad it by one voxel. Unlike the old anchor-centered cube, this remains
        // correct when rays travel farther than gridSize/2 from their source.
        float minFx = Float.POSITIVE_INFINITY, minFy = Float.POSITIVE_INFINITY, minFz = Float.POSITIVE_INFINITY;
        float maxFx = Float.NEGATIVE_INFINITY, maxFy = Float.NEGATIVE_INFINITY, maxFz = Float.NEGATIVE_INFINITY;
        for (Request r : batch) {
            for (int n = 0; n < r.rayCount; n++) {
                int base = n * 6;
                minFx = Math.min(minFx, Math.min(r.rays[base], r.rays[base + 3]));
                minFy = Math.min(minFy, Math.min(r.rays[base + 1], r.rays[base + 4]));
                minFz = Math.min(minFz, Math.min(r.rays[base + 2], r.rays[base + 5]));
                maxFx = Math.max(maxFx, Math.max(r.rays[base], r.rays[base + 3]));
                maxFy = Math.max(maxFy, Math.max(r.rays[base + 1], r.rays[base + 4]));
                maxFz = Math.max(maxFz, Math.max(r.rays[base + 2], r.rays[base + 5]));
            }
        }

        int minX = (int) Math.floor(minFx) - 1;
        int minY = (int) Math.floor(minFy) - 1;
        int minZ = (int) Math.floor(minFz) - 1;
        int maxX = (int) Math.floor(maxFx) + 1;
        int maxY = (int) Math.floor(maxFy) + 1;
        int maxZ = (int) Math.floor(maxFz) + 1;
        int requiredX = maxX - minX + 1;
        int requiredY = maxY - minY + 1;
        int requiredZ = maxZ - minZ + 1;
        int requestedSize = Math.max(16, Math.min(48, config.gridSize));
        int size = Math.max(requestedSize, Math.max(requiredX, Math.max(requiredY, requiredZ)));
        if (size > 64) {
            for (Request r : batch) pending.putIfAbsent(r.key, r);
            lastFlush = Long.MIN_VALUE;
            return;
        }

        int centerX = (minX + maxX) / 2;
        int centerY = (minY + maxY) / 2;
        int centerZ = (minZ + maxZ) / 2;
        minX = centerX - size / 2;
        minY = centerY - size / 2;
        minZ = centerZ - size / 2;
        maxX = minX + size - 1;
        maxY = minY + size - 1;
        maxZ = minZ + size - 1;
        if (maxX < (int) Math.ceil(maxFx) || maxY < (int) Math.ceil(maxFy) || maxZ < (int) Math.ceil(maxFz)
                || minX > (int) Math.floor(minFx) || minY > (int) Math.floor(minFy) || minZ > (int) Math.floor(minFz)) {
            for (Request r : batch) pending.putIfAbsent(r.key, r);
            lastFlush = Long.MIN_VALUE;
            return;
        }

        final int snapshotMinX = minX;
        final int snapshotMinY = minY;
        final int snapshotMinZ = minZ;
        final int snapshotSize = size;
        Request anchor = batch.get(0);
        byte[] solid = new byte[snapshotSize * snapshotSize * snapshotSize];
        int i = 0;
        try {
            for (int z = 0; z < snapshotSize; z++) for (int y = 0; y < snapshotSize; y++) for (int xx = 0; xx < snapshotSize; xx++, i++) {
                solid[i] = (byte) (anchor.sampler.isSolid(snapshotMinX + xx, snapshotMinY + y, snapshotMinZ + z) ? 1 : 0);
            }
        } catch (Throwable t) {
            HeliumClient.LOGGER.debug("gpu compute world snapshot failed", t);
            for (Request r : batch) pending.putIfAbsent(r.key, r);
            lastFlush = Long.MIN_VALUE;
            return;
        }

        int totalRays = 0;
        for (Request r : batch) totalRays += r.rayCount;
        float[] rays = new float[totalRays * 6];
        int rayCursor = 0;
        for (Request r : batch) {
            System.arraycopy(r.rays, 0, rays, rayCursor * 6, r.rayCount * 6);
            rayCursor += r.rayCount;
        }

        final OpenClComputeBackend b;
        synchronized (BACKEND_LOCK) {
            b = backend;
            if (b == null) {
                for (Request r : batch) pending.putIfAbsent(r.key, r);
                lastFlush = Long.MIN_VALUE;
                return;
            }
        }
        long batchGeneration = generation;
        EXECUTOR.execute(() -> {
            synchronized (BACKEND_LOCK) {
                if (b != backend || batchGeneration != generation) return;
                try {
                    boolean[] values = b.runLineOfSight(rays, solid, snapshotSize, snapshotMinX, snapshotMinY, snapshotMinZ);
                    if (values == null || batchGeneration != generation) return;

                    int valueCursor = 0;
                    for (Request r : batch) {
                        boolean visible = false;
                        for (int n = 0; n < r.rayCount && valueCursor < values.length; n++, valueCursor++) {
                            if (values[valueCursor]) visible = true;
                        }
                        if (r.generation == generation) {
                            results.put(r.key, new Result(visible, tick, generation));
                            if (visible) lastVisible.put(r.key, tick);
                        }
                    }
                } catch (Throwable t) {
                    HeliumClient.LOGGER.debug("gpu line-of-sight batch failed", t);
                }
            }
        });
    }

    public static int[] runFlowField(byte[] blocked, int size, int targetX, int targetY, int targetZ) {
        if (!pathfindingEnabled() || backend == null) return null;
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
}
