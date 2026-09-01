package com.helium.render;

import com.helium.util.ChunkPosUtil;
import com.helium.util.ChunkScheduler;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * 1.21.11 port of Catalyst's AsyncMeshing queue.
 * Scheduling is deferred to the client tick and prioritized around the camera.
 */
public final class AsyncChunkMeshing {
    public static final int DEFAULT_MAX_PER_TICK = 16;
    private static final int QUEUE_INITIAL_CAPACITY = 256;

    private static final PriorityBlockingQueue<ChunkTask> PENDING =
            new PriorityBlockingQueue<>(QUEUE_INITIAL_CAPACITY, Comparator.comparingDouble(ChunkTask::priority));
    private static final Set<Long> QUEUED_POSITIONS = ConcurrentHashMap.newKeySet();

    private static volatile Vec3d cameraPos = Vec3d.ZERO;
    private static volatile ChunkPos cameraChunk = ChunkPos.ORIGIN;
    private static volatile boolean bypassing;

    private AsyncChunkMeshing() {}

    public static void updateCamera(Vec3d pos) {
        if (pos == null) return;
        cameraPos = pos;
        cameraChunk = new ChunkPos((int) Math.floor(pos.x / 16.0D), (int) Math.floor(pos.z / 16.0D));
    }

    public static boolean queue(int x, int y, int z, boolean important) {
        long key = ChunkPosUtil.packPos(x, y, z);
        if (!QUEUED_POSITIONS.add(key)) return false;

        PENDING.offer(new ChunkTask(x, y, z, calculatePriority(x, y, z, important), important));
        return true;
    }

    public static ChunkTask dequeue() {
        ChunkTask task = PENDING.poll();
        if (task != null) {
            QUEUED_POSITIONS.remove(ChunkPosUtil.packPos(task.x(), task.y(), task.z()));
        }
        return task;
    }

    public static int drainQueue(WorldRenderer renderer, int maxPerTick) {
        if (renderer == null || PENDING.isEmpty()) return 0;

        return ChunkScheduler.drainLimited(
                renderer,
                Math.max(1, maxPerTick),
                AsyncChunkMeshing::pollEntry,
                value -> bypassing = value
        );
    }

    private static ChunkScheduler.ChunkEntry pollEntry() {
        ChunkTask task = dequeue();
        return task == null ? null : new ChunkScheduler.ChunkEntry(
                task.x(), task.y(), task.z(), task.important());
    }

    public static boolean isBypassing() {
        return bypassing;
    }

    public static int size() {
        return PENDING.size();
    }

    public static boolean isEmpty() {
        return PENDING.isEmpty();
    }

    public static void clear() {
        PENDING.clear();
        QUEUED_POSITIONS.clear();
        bypassing = false;
    }

    private static double calculatePriority(int x, int y, int z, boolean important) {
        long dx = (long) x - cameraChunk.x;
        long dz = (long) z - cameraChunk.z;
        long dy = (long) y - (long) Math.floor(cameraPos.y / 16.0D);
        double distSq = (double) dx * dx + (double) dy * dy + (double) dz * dz;
        return important ? distSq * 0.5D : distSq;
    }

    public record ChunkTask(int x, int y, int z, double priority, boolean important) {}
}
