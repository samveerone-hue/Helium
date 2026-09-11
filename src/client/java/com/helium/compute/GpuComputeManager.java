package com.helium.compute;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Helium-owned compute layer inspired by the documented shape of Laminar's GPU features.
 * It never becomes authoritative: a cold/missing GPU result falls back to vanilla.
 */
public final class GpuComputeManager {
    private record LosKey(int sourceId, int targetId) {}
    private record LosRequest(LosKey key, float ox, float oy, float oz, float tx, float ty, float tz, long tick) {}
    private record LosResult(boolean visible, long tick) {}
    private record PathKey(int mobId, long targetPos, int reach) {}
    private record PathResult(Path path, long tick) {}

    private static volatile HeliumConfig config;
    private static volatile OpenClComputeBackend backend;
    private static volatile boolean initialized;
    private static volatile boolean attempted;
    private static volatile boolean loggedUnavailable;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Helium-GPU-Compute");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private static final ConcurrentMap<LosKey, LosRequest> pendingLos = new ConcurrentHashMap<>();
    private static final ConcurrentMap<LosKey, LosResult> losResults = new ConcurrentHashMap<>();
    private static final ConcurrentMap<PathKey, PathResult> pathResults = new ConcurrentHashMap<>();
    private static volatile long lastSensingTick = Long.MIN_VALUE;

    private GpuComputeManager() {}

    private static void ensureInitialized() {
        if (!attempted) {
            synchronized (GpuComputeManager.class) {
                if (!attempted) init(HeliumConfig.load());
            }
        }
    }

    public static synchronized void init(HeliumConfig cfg) {
        attempted = true;
        config = cfg;
        initialized = false;
        OpenClComputeBackend old = backend;
        backend = null;
        if (old != null) {
            try { old.close(); } catch (Throwable ignored) {}
        }
        pendingLos.clear();
        losResults.clear();
        pathResults.clear();

        if (!cfg.gpuCompute || (!cfg.gpuLineOfSight && !cfg.gpuPathfinding)) return;

        try {
            backend = OpenClComputeBackend.create();
            if (backend == null) {
                if (!loggedUnavailable) {
                    HeliumClient.LOGGER.info("gpu compute unavailable - OpenCL unified-memory GPU not found");
                    loggedUnavailable = true;
                }
                return;
            }
            initialized = true;
            HeliumClient.LOGGER.info("gpu compute enabled on {} (unified memory: {})", backend.deviceName(), backend.usesUnifiedMemory());
        } catch (Throwable t) {
            backend = null;
            HeliumClient.LOGGER.warn("gpu compute initialization failed; vanilla AI will be used", t);
        }
    }

    public static boolean isAvailable() {
        ensureInitialized();
        return initialized && backend != null;
    }

    public static boolean lineOfSightEnabled() {
        ensureInitialized();
        HeliumConfig cfg = config;
        return isAvailable() && cfg != null && cfg.gpuCompute && cfg.gpuLineOfSight;
    }

    public static boolean pathfindingEnabled() {
        ensureInitialized();
        HeliumConfig cfg = config;
        return isAvailable() && cfg != null && cfg.gpuCompute && cfg.gpuPathfinding;
    }

    public static Boolean getCachedLineOfSight(int sourceId, int targetId, long tick) {
        LosResult result = losResults.get(new LosKey(sourceId, targetId));
        if (result == null) return null;
        HeliumConfig cfg = config;
        if (cfg == null || tick - result.tick > Math.max(1, cfg.gpuComputeRefreshTicks)) return null;
        return result.visible;
    }

    public static void requestLineOfSight(Mob source, Entity target) {
        if (!lineOfSightEnabled() || source.level().isClientSide()) return;
        double maxDistance = Math.max(8, config.gpuComputeGridSize - 4);
        if (source.distanceToSqr(target) > maxDistance * maxDistance) return;

        long tick = source.level().getGameTime();
        LosKey key = new LosKey(source.getId(), target.getId());
        pendingLos.putIfAbsent(key, new LosRequest(key,
                (float) source.getX(), (float) source.getEyeY(), (float) source.getZ(),
                (float) target.getX(), (float) target.getEyeY(), (float) target.getZ(), tick));
    }

    /** Called from Sensing.tick on the server/integrated-server thread. */
    public static void onSensingTick(Mob mob) {
        if (!lineOfSightEnabled() || mob.level().isClientSide()) return;
        long tick = mob.level().getGameTime();
        long previous = lastSensingTick;
        if (previous == tick) return;
        lastSensingTick = tick;
        if (previous != Long.MIN_VALUE) flushLineOfSight(mob.level(), previous);
    }

    private static void flushLineOfSight(Level level, long tick) {
        ArrayList<LosRequest> requests = new ArrayList<>();
        for (LosRequest request : pendingLos.values()) {
            if (request.tick == tick && pendingLos.remove(request.key, request)) requests.add(request);
        }
        if (requests.isEmpty()) return;

        HeliumConfig cfg = config;
        OpenClComputeBackend cl = backend;
        if (cfg == null || cl == null) return;

        LosRequest anchor = requests.get(0);
        int size = Math.max(24, Math.min(64, cfg.gpuComputeGridSize));
        int half = size / 2;
        int minX = ((int) Math.floor(anchor.tx)) - half;
        int minY = ((int) Math.floor(anchor.ty)) - half;
        int minZ = ((int) Math.floor(anchor.tz)) - half;

        requests.removeIf(r -> outside(r.ox, r.oy, r.oz, minX, minY, minZ, size)
                || outside(r.tx, r.ty, r.tz, minX, minY, minZ, size));
        if (requests.isEmpty()) return;

        byte[] solid = snapshotSolid(level, minX, minY, minZ, size);
        float[] rays = new float[requests.size() * 6];
        for (int i = 0; i < requests.size(); i++) {
            LosRequest r = requests.get(i);
            int o = i * 6;
            rays[o] = r.ox; rays[o + 1] = r.oy; rays[o + 2] = r.oz;
            rays[o + 3] = r.tx; rays[o + 4] = r.ty; rays[o + 5] = r.tz;
        }

        EXECUTOR.execute(() -> {
            try {
                boolean[] values = cl.runLineOfSight(rays, solid, size, minX, minY, minZ);
                if (values == null) return;
                for (int i = 0; i < values.length; i++) {
                    LosRequest r = requests.get(i);
                    losResults.put(r.key, new LosResult(values[i], tick));
                }
            } catch (Throwable t) {
                HeliumClient.LOGGER.debug("gpu line-of-sight batch failed; using vanilla", t);
            }
        });
    }

    public static Path getCachedPath(Mob mob, BlockPos target, int reach) {
        if (!pathfindingEnabled()) return null;
        PathResult result = pathResults.get(new PathKey(mob.getId(), target.asLong(), reach));
        if (result == null) return null;
        long age = mob.level().getGameTime() - result.tick;
        if (age > Math.max(1, config.gpuComputeRefreshTicks)) return null;
        return result.path.copy();
    }

    public static void requestPath(Mob mob, BlockPos target, int reach) {
        if (!pathfindingEnabled() || mob.level().isClientSide() || !(mob.getNavigation() instanceof GroundPathNavigation)) return;
        HeliumConfig cfg = config;
        OpenClComputeBackend cl = backend;
        if (cfg == null || cl == null) return;

        int size = Math.max(24, Math.min(64, cfg.gpuComputeGridSize));
        double maxDistance = Math.max(8, size / 2.0 - 2);
        if (mob.position().distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > maxDistance * maxDistance) return;

        PathKey key = new PathKey(mob.getId(), target.asLong(), reach);
        if (pathResults.containsKey(key)) return;

        int centerX = (int) Math.floor((mob.getX() + target.getX()) * 0.5);
        int centerY = (int) Math.floor((mob.getY() + target.getY()) * 0.5);
        int centerZ = (int) Math.floor((mob.getZ() + target.getZ()) * 0.5);
        int half = size / 2;
        int minX = centerX - half;
        int minY = centerY - half;
        int minZ = centerZ - half;
        int startX = (int) Math.floor(mob.getX()) - minX;
        int startY = (int) Math.floor(mob.getY()) - minY;
        int startZ = (int) Math.floor(mob.getZ()) - minZ;
        int targetX = target.getX() - minX;
        int targetY = target.getY() - minY;
        int targetZ = target.getZ() - minZ;
        if (!inside(startX, startY, startZ, size) || !inside(targetX, targetY, targetZ, size)) return;

        byte[] blocked = snapshotWalkable(mob.level(), minX, minY, minZ, size);
        if (blocked[(startZ * size + startY) * size + startX] != 0
                || blocked[(targetZ * size + targetY) * size + targetX] != 0) return;

        long tick = mob.level().getGameTime();
        EXECUTOR.execute(() -> {
            try {
                int[] distances = cl.runFlowField(blocked, size, targetX, targetY, targetZ);
                Path path = buildPath(distances, blocked, size, minX, minY, minZ,
                        startX, startY, startZ, targetX, targetY, targetZ, target);
                if (path != null) pathResults.put(key, new PathResult(path, tick));
            } catch (Throwable t) {
                HeliumClient.LOGGER.debug("gpu pathfinding batch failed; using vanilla", t);
            }
        });
    }

    private static Path buildPath(int[] distances, byte[] blocked, int size,
                                  int minX, int minY, int minZ,
                                  int sx, int sy, int sz,
                                  int tx, int ty, int tz,
                                  BlockPos target) {
        if (distances == null) return null;
        int current = (sz * size + sy) * size + sx;
        int targetIndex = (tz * size + ty) * size + tx;
        if (distances[current] >= 1073741824 || distances[targetIndex] >= 1073741824) return null;

        List<PathNode> nodes = new ArrayList<>();
        int x = sx, y = sy, z = sz;
        int safety = size * size;
        while (safety-- > 0) {
            nodes.add(new PathNode(x + minX, y + minY, z + minZ));
            if (x == tx && y == ty && z == tz) return new Path(nodes, target, true);

            int bestX = x, bestY = y, bestZ = z;
            int best = distances[current];
            int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
            for (int[] d : dirs) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (!inside(nx, ny, nz, size)) continue;
                int idx = (nz * size + ny) * size + nx;
                if (blocked[idx] != 0 || distances[idx] >= best) continue;
                best = distances[idx]; bestX = nx; bestY = ny; bestZ = nz;
            }
            if (bestX == x && bestY == y && bestZ == z) return null;
            x = bestX; y = bestY; z = bestZ;
            current = (z * size + y) * size + x;
        }
        return null;
    }

    private static byte[] snapshotSolid(Level level, int minX, int minY, int minZ, int size) {
        byte[] solid = new byte[size * size * size];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int z = 0; z < size; z++) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++, i++) {
                    pos.set(minX + x, minY + y, minZ + z);
                    var state = level.getBlockState(pos);
                    solid[i] = (byte) (state.canOcclude() && state.isCollisionShapeFullBlock(level, pos) ? 1 : 0);
                }
            }
        }
        return solid;
    }

    private static byte[] snapshotWalkable(Level level, int minX, int minY, int minZ, int size) {
        byte[] blocked = snapshotSolid(level, minX, minY, minZ, size);
        for (int z = 0; z < size; z++) {
            for (int y = 1; y < size - 1; y++) {
                for (int x = 0; x < size; x++) {
                    int idx = (z * size + y) * size + x;
                    if (blocked[idx] != 0) continue;
                    int below = ((z * size + (y - 1)) * size) + x;
                    int above = ((z * size + (y + 1)) * size) + x;
                    if (blocked[below] == 0 || blocked[above] != 0) blocked[idx] = 1;
                }
            }
        }
        return blocked;
    }

    private static boolean inside(int x, int y, int z, int size) {
        return x >= 0 && y >= 0 && z >= 0 && x < size && y < size && z < size;
    }

    private static boolean outside(float x, float y, float z, int minX, int minY, int minZ, int size) {
        return x < minX || y < minY || z < minZ || x >= minX + size || y >= minY + size || z >= minZ + size;
    }

    public static void shutdown() {
        initialized = false;
        OpenClComputeBackend cl = backend;
        backend = null;
        if (cl != null) {
            try { cl.close(); } catch (Throwable ignored) {}
        }
        EXECUTOR.shutdownNow();
        try { EXECUTOR.awaitTermination(1, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
