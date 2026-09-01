package com.helium.render;

import com.helium.util.ChunkPosUtil;
import com.helium.util.ChunkScheduler;
import net.minecraft.client.render.WorldRenderer;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 1.21.X Catalyst-style coalescing queue for chunk render scheduling.
 */
public final class RenderBatch {
    private static final AtomicInteger FRAME_SECTIONS = new AtomicInteger();

    private static final Map<Long, Boolean> PENDING = new ConcurrentHashMap<>();
    private static volatile boolean bypassing;
    private static volatile int lastSections;

    private RenderBatch() {}

    public static void beginFrame() {
        lastSections = FRAME_SECTIONS.getAndSet(0);
    }

    public static void trackSection() {
        FRAME_SECTIONS.incrementAndGet();
    }

    public static int getLastSections() {
        return lastSections;
    }

    public static void queue(int x, int y, int z, boolean important) {
        long key = ChunkPosUtil.packPos(x, y, z);
        PENDING.merge(key, important, Boolean::logicalOr);
    }

    public static void drain(WorldRenderer renderer) {
        if (renderer == null || PENDING.isEmpty()) return;

        Iterator<Map.Entry<Long, Boolean>> iterator = PENDING.entrySet().iterator();
        Iterator<ChunkScheduler.ChunkEntry> entries = new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public ChunkScheduler.ChunkEntry next() {
                Map.Entry<Long, Boolean> entry = iterator.next();
                iterator.remove();
                long key = entry.getKey();
                return new ChunkScheduler.ChunkEntry(
                        ChunkPosUtil.unpackX(key),
                        ChunkPosUtil.unpackY(key),
                        ChunkPosUtil.unpackZ(key),
                        Boolean.TRUE.equals(entry.getValue())
                );
            }
        };

        ChunkScheduler.drain(renderer, entries, value -> bypassing = value);
    }

    public static boolean isBypassing() {
        return bypassing;
    }

    public static int queueSize() {
        return PENDING.size();
    }

    public static void clear() {
        FRAME_SECTIONS.set(0);
        lastSections = 0;
        PENDING.clear();
    }
}
