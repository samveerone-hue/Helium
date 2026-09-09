package com.helium.render;

import java.util.concurrent.atomic.AtomicInteger;

/** Lightweight per-frame accounting for chunk scheduling. */
public final class RenderBatch {
    private static final AtomicInteger FRAME_SECTIONS = new AtomicInteger();
    private static volatile int lastSections;

    private RenderBatch() {}
    public static void beginFrame() { lastSections = FRAME_SECTIONS.getAndSet(0); }
    public static void trackSection() { FRAME_SECTIONS.incrementAndGet(); }
    public static int getLastSections() { return lastSections; }
    public static boolean isBypassing() { return AsyncChunkMeshing.isBypassing(); }
    public static void clear() { FRAME_SECTIONS.set(0); lastSections = 0; }
}
