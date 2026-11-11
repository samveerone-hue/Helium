package com.helium.rentities.entities;

import net.minecraft.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityBatchQueueTest {

    @Test
    void cancelledReservationsCompactAndCapacityFallsBack() {
        EntityBatchQueue queue = new EntityBatchQueue(3);
        long buffer = MemoryUtil.nmemAlloc((long) EntityInstance.STRIDE * 3);
        try {
            queue.reserve(buffer, EntityType.ZOMBIE, false);
            queue.reserve(buffer, EntityType.PIG, false);
            queue.release(EntityType.PIG);
            queue.reserve(buffer, EntityType.COW, false);
            assertEquals(0L, queue.reserve(buffer, EntityType.SHEEP, false));
            MemoryUtil.memPutInt(buffer + (long) EntityInstance.STRIDE * 2, 1234);

            int count = queue.compact(queue.takeCount(), buffer);

            assertEquals(2, count);
            assertEquals(EntityType.ZOMBIE, queue.queuedTypes[0]);
            assertEquals(EntityType.COW, queue.queuedTypes[1]);
            assertEquals(1234, MemoryUtil.memGetInt(buffer + EntityInstance.STRIDE));
            assertEquals(0, queue.takeCount());
        } finally {
            MemoryUtil.nmemFree(buffer);
        }
    }
}
