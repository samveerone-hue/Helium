package com.helium.rentities.entities;

import com.helium.HeliumClient;
import net.minecraft.entity.EntityType;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

final class EntityBatchQueue {
    final EntityType<?>[] queuedTypes;
    final EntityType<?>[] extractionTypes;
    final int[] originalIndices;
    private final long[] sortKeys;
    private final AtomicInteger size = new AtomicInteger();
    private final Object lock = new Object();
    private final ThreadLocal<Deque<Integer>> reservedSlots = ThreadLocal.withInitial(ArrayDeque::new);

    EntityBatchQueue(int capacity) {
        queuedTypes = new EntityType<?>[capacity];
        extractionTypes = new EntityType<?>[capacity];
        originalIndices = new int[capacity];
        sortKeys = new long[capacity];
    }

    Object lock() {
        return lock;
    }

    boolean enqueue(EntityBatchRenderer renderer, long extractionBuffer, EntityType<?> type,
                    Object state, double x, double y, double z, boolean debugLogging) {
        int index = size.getAndIncrement();
        if (index >= queuedTypes.length) {
            size.decrementAndGet();
            if (debugLogging) {
                HeliumClient.LOGGER.warn("[Rentities] Instance queue full ({}); falling back to vanilla rendering",
                        queuedTypes.length);
            }
            return false;
        }

        queuedTypes[index] = type;
        extractionTypes[index] = type;
        originalIndices[index] = index;
        if (!renderer.writeEntityInstance(
                extractionBuffer + (long) index * EntityInstance.STRIDE, state, x, y, z)) {
            clearSlot(index);
            size.decrementAndGet();
            return false;
        }
        return true;
    }

    long reserve(long extractionBuffer, EntityType<?> type, boolean debugLogging) {
        int index = size.getAndIncrement();
        if (index >= queuedTypes.length) {
            size.decrementAndGet();
            if (debugLogging) {
                HeliumClient.LOGGER.warn("[Rentities] Instance queue full ({}); falling back to vanilla rendering",
                        queuedTypes.length);
            }
            return 0L;
        }
        queuedTypes[index] = type;
        extractionTypes[index] = type;
        originalIndices[index] = index;
        reservedSlots.get().addLast(index);
        return extractionBuffer + (long) index * EntityInstance.STRIDE;
    }

    void release(EntityType<?> type) {
        Deque<Integer> slots = reservedSlots.get();
        Integer index = slots.peekLast();
        if (index == null || index < 0 || index >= queuedTypes.length) return;
        if (queuedTypes[index] != type || extractionTypes[index] != type) return;
        slots.pollLast();
        clearSlot(index);
    }

    int takeCount() {
        return Math.min(size.getAndSet(0), queuedTypes.length);
    }

    void clearReservations() {
        reservedSlots.get().clear();
    }

    int compact(int rawCount, long extractionBuffer) {
        int write = 0;
        for (int read = 0; read < rawCount; read++) {
            if (queuedTypes[read] == null && extractionTypes[read] == null) continue;
            if (write != read) {
                queuedTypes[write] = queuedTypes[read];
                extractionTypes[write] = extractionTypes[read];
                originalIndices[write] = write;
                MemoryUtil.memCopy(
                        extractionBuffer + (long) read * EntityInstance.STRIDE,
                        extractionBuffer + (long) write * EntityInstance.STRIDE,
                        EntityInstance.STRIDE);
            }
            write++;
        }
        for (int index = write; index < rawCount; index++) clearSlot(index);
        return write;
    }

    void clearReferences(int count) {
        for (int index = 0; index < count; index++) clearSlot(index);
    }

    void sortByEntityType(int count) {
        for (int index = 0; index < count; index++) {
            EntityType<?> type = queuedTypes[index];
            int typeIndex = type != null ? EntityBatchRegistry.getEntityTypeIndex(type) : 0;
            sortKeys[index] = ((long) typeIndex << 32) | (originalIndices[index] & 0xFFFFFFFFL);
        }
        Arrays.sort(sortKeys, 0, count);
        for (int index = 0; index < count; index++) {
            originalIndices[index] = (int) (sortKeys[index] & 0xFFFFFFFFL);
        }
        for (int index = 0; index < count; index++) {
            queuedTypes[index] = extractionTypes[originalIndices[index]];
        }
    }

    private void clearSlot(int index) {
        queuedTypes[index] = null;
        extractionTypes[index] = null;
        originalIndices[index] = 0;
    }
}
