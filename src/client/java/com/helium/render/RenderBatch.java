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
