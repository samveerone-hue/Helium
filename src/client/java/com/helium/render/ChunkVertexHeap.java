package com.helium.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reusable off-heap staging heap for large render/vertex buffers.
 * Buckets are bounded so the pool cannot grow without limit.
 */
public final class ChunkVertexHeap {
    private static final int[] BUCKETS = {64 * 1024, 256 * 1024, 1024 * 1024, 4 * 1024 * 1024};
    private static final int MAX_PER_BUCKET = 8;
    @SuppressWarnings("unchecked")
    private static final ConcurrentLinkedDeque<ByteBuffer>[] POOLS = new ConcurrentLinkedDeque[BUCKETS.length];
    private static final AtomicInteger[] COUNTS = new AtomicInteger[BUCKETS.length];

    static {
        for (int i = 0; i < BUCKETS.length; i++) {
            POOLS[i] = new ConcurrentLinkedDeque<>();
            COUNTS[i] = new AtomicInteger();
        }
    }

    private ChunkVertexHeap() {}

    public static ByteBuffer borrow(int minCapacity) {
        int i = bucket(minCapacity);
        if (i < 0) return ByteBuffer.allocateDirect(minCapacity).order(ByteOrder.nativeOrder());
        ByteBuffer buffer = POOLS[i].pollFirst();
        if (buffer != null) {
            COUNTS[i].decrementAndGet();
            buffer.clear();
            return buffer;
        }
        return ByteBuffer.allocateDirect(BUCKETS[i]).order(ByteOrder.nativeOrder());
    }

    public static void release(ByteBuffer buffer) {
        if (buffer == null || !buffer.isDirect()) return;
        int i = exact(buffer.capacity());
        if (i < 0) return;
        if (COUNTS[i].get() >= MAX_PER_BUCKET) return;
        buffer.clear();
        POOLS[i].offerFirst(buffer);
        COUNTS[i].incrementAndGet();
    }

    private static int bucket(int size) {
        for (int i = 0; i < BUCKETS.length; i++) if (BUCKETS[i] >= size) return i;
        return -1;
    }

    private static int exact(int size) {
        for (int i = 0; i < BUCKETS.length; i++) if (BUCKETS[i] == size) return i;
        return -1;
    }
}
