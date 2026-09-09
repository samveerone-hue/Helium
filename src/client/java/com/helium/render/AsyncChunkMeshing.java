package com.helium.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.util.ChunkPosUtil;
import com.helium.util.ChunkScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

/** Bounded, camera-prioritized chunk scheduling layer for 26.1.2. */
public final class AsyncChunkMeshing {
    private static final PriorityBlockingQueue<ChunkTask> PENDING =
            new PriorityBlockingQueue<>(256, Comparator.comparingDouble(ChunkTask::priority));
    private static final ConcurrentHashMap<Long, ChunkTask> QUEUED = new ConcurrentHashMap<>();
    private static volatile Vec3 cameraPos = Vec3.ZERO;
    private static volatile long cameraChunkKey = 0L;
    private static volatile boolean bypassing;

    private AsyncChunkMeshing() {}

    public static void updateCamera(Vec3 pos) {
        if (pos == null) return;
        cameraPos = pos;
        int cx = Mth.floor(pos.x / 16.0D);
        int cz = Mth.floor(pos.z / 16.0D);
        cameraChunkKey = ChunkPosUtil.packPos(cx, 0, cz);
    }

    public static boolean queue(int x, int y, int z, boolean important) {
        long key = ChunkPosUtil.packPos(x, y, z);
        synchronized (AsyncChunkMeshing.class) {
            ChunkTask existing = QUEUED.get(key);
            if (existing != null) {
                if (!important || existing.important()) return true;
                ChunkTask upgraded = new ChunkTask(
                        x, y, z, calculatePriority(x, y, z, true), true);
                if (QUEUED.replace(key, existing, upgraded)) {
                    PENDING.remove(existing);
                    PENDING.offer(upgraded);
                    return true;
                }
                return false;
            }
            if (QUEUED.size() >= 8192) return false;
            ChunkTask task = new ChunkTask(x, y, z, calculatePriority(x, y, z, important), important);
            QUEUED.put(key, task);
            PENDING.offer(task);
            return true;
        }
    }

    public static int drainQueue(LevelRenderer renderer, int maxPerFrame) {
        if (renderer == null || PENDING.isEmpty()) return 0;
        return ChunkScheduler.drainLimited(renderer, Math.max(1, Math.min(maxPerFrame, 64)),
                AsyncChunkMeshing::dequeue, value -> bypassing = value);
    }

    private static ChunkScheduler.ChunkEntry dequeue() {
        ChunkTask task = PENDING.poll();
        if (task == null) return null;
        QUEUED.remove(ChunkPosUtil.packPos(task.x(), task.y(), task.z()), task);
        return new ChunkScheduler.ChunkEntry(task.x(), task.y(), task.z(), task.important());
    }

    public static int getDrainBudget(int configured) {
        int max = Math.max(1, Math.min(configured, 64));
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.adaptiveChunkScheduling || !RenderPipeline.isInitialized()) return max;
        double frameMs = RenderPipeline.getSmoothedFrameTimeMs();
        double budgetMs = 1000.0 / Math.max(1, Minecraft.getInstance().options.framerateLimit().get());
        if (frameMs <= budgetMs) return max;
        return Math.max(1, (int) Math.floor(max * Math.max(0.25, budgetMs / frameMs)));
    }

    public static boolean isBypassing() { return bypassing; }
    public static int size() { return PENDING.size(); }

    public static void clear() {
        synchronized (AsyncChunkMeshing.class) {
            PENDING.clear();
            QUEUED.clear();
            bypassing = false;
        }
    }

    private static double calculatePriority(int x, int y, int z, boolean important) {
        long dx = (long) x - unpackCameraX();
        long dz = (long) z - unpackCameraZ();
        long dy = (long) y - Mth.floor(cameraPos.y / 16.0D);
        double distSq = (double) dx * dx + (double) dy * dy + (double) dz * dz;
        return important ? distSq * 0.5D : distSq;
    }

    private static int unpackCameraX() { return ChunkPosUtil.unpackX(cameraChunkKey); }
    private static int unpackCameraZ() { return ChunkPosUtil.unpackZ(cameraChunkKey); }

    public record ChunkTask(int x, int y, int z, double priority, boolean important) {}
}
