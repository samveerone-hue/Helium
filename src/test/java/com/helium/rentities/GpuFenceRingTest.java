package com.helium.rentities;

import com.helium.rentities.gl.GpuFenceRing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL32C.GL_ALREADY_SIGNALED;
import static org.lwjgl.opengl.GL32C.GL_TIMEOUT_EXPIRED;
import static org.lwjgl.opengl.GL32C.GL_WAIT_FAILED;

class GpuFenceRingTest {

    @Test
    void signaledFenceIsDeletedAndWaitDurationRecorded() {
        FakeSyncOperations sync = new FakeSyncOperations();
        sync.result = GL_ALREADY_SIGNALED;
        GpuFenceRing ring = new GpuFenceRing(1, sync);

        assertTrue(ring.signal(0));
        assertEquals(GpuFenceRing.WaitResult.SIGNALED, ring.waitFor(0));
        assertEquals(1, sync.deleteCount);
        assertTrue(ring.lastWaitDurationNanos() >= 0);
    }

    @Test
    void timeoutLeavesFenceForRetryWithoutBlocking() {
        FakeSyncOperations sync = new FakeSyncOperations();
        sync.result = GL_TIMEOUT_EXPIRED;
        GpuFenceRing ring = new GpuFenceRing(1, sync);

        assertTrue(ring.signal(0));
        assertEquals(GpuFenceRing.WaitResult.TIMEOUT, ring.waitFor(0));
        assertEquals(0, sync.timeoutNanos);
        assertEquals(0, sync.deleteCount);

        sync.result = GL_ALREADY_SIGNALED;
        assertEquals(GpuFenceRing.WaitResult.SIGNALED, ring.waitFor(0));
        assertEquals(1, sync.deleteCount);
    }

    @Test
    void failedFenceIsNotReused() {
        FakeSyncOperations sync = new FakeSyncOperations();
        sync.result = GL_WAIT_FAILED;
        GpuFenceRing ring = new GpuFenceRing(1, sync);

        assertTrue(ring.signal(0));
        assertEquals(GpuFenceRing.WaitResult.FAILED, ring.waitFor(0));
        assertEquals(GpuFenceRing.WaitResult.FAILED, ring.waitFor(0));
        assertEquals(1, sync.waitCount);
        assertFalse(ring.signal(0));
    }

    private static final class FakeSyncOperations implements GpuFenceRing.SyncOperations {
        private int result;
        private int waitCount;
        private int deleteCount;
        private long timeoutNanos = -1;

        @Override
        public int waitSync(long fence, int flags, long timeoutNanos) {
            waitCount++;
            this.timeoutNanos = timeoutNanos;
            return result;
        }

        @Override
        public void deleteSync(long fence) {
            deleteCount++;
        }

        @Override
        public long fenceSync(int condition, int flags) {
            return 1;
        }
    }
}
