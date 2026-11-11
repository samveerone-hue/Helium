package com.helium.memory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class NativeMemoryManagerTest {

    @BeforeEach
    void setUp() {
        NativeMemoryManager.init(1);
    }

    @AfterEach
    void tearDown() {
        NativeMemoryManager.shutdown();
    }

    @Test
    void freeReturnsPooledBufferAndUpdatesCounters() {
        ByteBuffer first = NativeMemoryManager.allocate(512);
        assertNotNull(first);
        assertEquals(1024, NativeMemoryManager.getTotalAllocatedBytes());
        assertEquals(1024, NativeMemoryManager.getActiveBytes());

        NativeMemoryManager.free(first);
        assertEquals(1024, NativeMemoryManager.getTotalAllocatedBytes());
        assertEquals(1024, NativeMemoryManager.getTotalPooledBytes());
        assertEquals(0, NativeMemoryManager.getActiveBytes());
        assertEquals(0, NativeMemoryManager.getAllocationCount());

        ByteBuffer reused = NativeMemoryManager.allocate(512);
        assertSame(first, reused);
        assertEquals(1024, NativeMemoryManager.getTotalAllocatedBytes());
        assertEquals(0, NativeMemoryManager.getTotalPooledBytes());
        assertEquals(1, NativeMemoryManager.getAllocationCount());
    }

    @Test
    void allocationRespectsMemoryLimit() {
        ByteBuffer allocation = NativeMemoryManager.allocate(1024 * 1024);
        assertNotNull(allocation);
        assertNull(NativeMemoryManager.allocate(1));
        assertEquals(1024 * 1024, NativeMemoryManager.getTotalAllocatedBytes());
        assertEquals(1, NativeMemoryManager.getAllocationCount());
    }

    @Test
    void repeatedAllocateAndFreeKeepsPoolAccountingStable() {
        for (int i = 0; i < 100; i++) {
            ByteBuffer allocation = NativeMemoryManager.allocate(512);
            assertNotNull(allocation);
            NativeMemoryManager.free(allocation);
        }

        assertEquals(1024, NativeMemoryManager.getTotalAllocatedBytes());
        assertEquals(1024, NativeMemoryManager.getTotalPooledBytes());
        assertEquals(0, NativeMemoryManager.getActiveBytes());
        assertEquals(0, NativeMemoryManager.getAllocationCount());
    }
}
