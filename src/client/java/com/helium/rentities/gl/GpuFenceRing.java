package com.helium.rentities.gl;

import com.helium.HeliumClient;

import static org.lwjgl.opengl.GL32C.GL_ALREADY_SIGNALED;
import static org.lwjgl.opengl.GL32C.GL_CONDITION_SATISFIED;
import static org.lwjgl.opengl.GL32C.GL_SYNC_FLUSH_COMMANDS_BIT;
import static org.lwjgl.opengl.GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE;
import static org.lwjgl.opengl.GL32C.GL_WAIT_FAILED;
import static org.lwjgl.opengl.GL32C.glClientWaitSync;
import static org.lwjgl.opengl.GL32C.glDeleteSync;
import static org.lwjgl.opengl.GL32C.glFenceSync;

/** Per-slot GPU fences for a {@link GpuRingBuffer}. */
public final class GpuFenceRing {
    private static final long NONBLOCKING_TIMEOUT_NS = 0L;
    private final long[] fences;
    private final boolean[] failedSlots;
    private final boolean[] warnedTimeouts;
    private final SyncOperations syncOperations;
    private volatile long lastWaitDurationNanos;

    public enum WaitResult { SIGNALED, TIMEOUT, FAILED }

    interface SyncOperations {
        int waitSync(long fence, int flags, long timeoutNanos);
        void deleteSync(long fence);
        long fenceSync(int condition, int flags);
    }

    public GpuFenceRing(int slots) {
        this(slots, new SyncOperations() {
            @Override
            public int waitSync(long fence, int flags, long timeoutNanos) {
                return glClientWaitSync(fence, flags, timeoutNanos);
            }

            @Override
            public void deleteSync(long fence) {
                glDeleteSync(fence);
            }

            @Override
            public long fenceSync(int condition, int flags) {
                return glFenceSync(condition, flags);
            }
        });
    }

    GpuFenceRing(int slots, SyncOperations syncOperations) {
        if (slots <= 0) throw new IllegalArgumentException("slots must be > 0");
        this.fences = new long[slots];
        this.failedSlots = new boolean[slots];
        this.warnedTimeouts = new boolean[slots];
        this.syncOperations = syncOperations;
    }

    /** Waits for a bounded interval and leaves timed-out slots untouched for a later retry. */
    public WaitResult waitFor(int slot) {
        checkSlot(slot);
        if (failedSlots[slot]) {
            lastWaitDurationNanos = 0L;
            return WaitResult.FAILED;
        }
        long fence = fences[slot];
        if (fence == 0L) {
            lastWaitDurationNanos = 0L;
            return WaitResult.SIGNALED;
        }

        long start = System.nanoTime();
        int result = syncOperations.waitSync(fence, GL_SYNC_FLUSH_COMMANDS_BIT, NONBLOCKING_TIMEOUT_NS);
        lastWaitDurationNanos = System.nanoTime() - start;
        if (result == GL_ALREADY_SIGNALED || result == GL_CONDITION_SATISFIED) {
            warnedTimeouts[slot] = false;
            syncOperations.deleteSync(fence);
            fences[slot] = 0L;
            return WaitResult.SIGNALED;
        }
        if (result == GL_WAIT_FAILED) {
            failedSlots[slot] = true;
            syncOperations.deleteSync(fence);
            fences[slot] = 0L;
            HeliumClient.LOGGER.error("[Entity] GPU fence failed on ring slot {} after {} ms; disabling entity batching", slot,
                    lastWaitDurationNanos / 1_000_000.0);
            return WaitResult.FAILED;
        }
        if (!warnedTimeouts[slot]) {
            warnedTimeouts[slot] = true;
            HeliumClient.LOGGER.warn("[Entity] GPU fence on ring slot {} is not ready after {} ms; deferring batch",
                    slot, lastWaitDurationNanos / 1_000_000.0);
        }
        return WaitResult.TIMEOUT;
    }

    /** Records that all commands issued so far must complete before {@code slot} is reused. */
    public boolean signal(int slot) {
        checkSlot(slot);
        if (failedSlots[slot]) return false;
        if (fences[slot] != 0L && waitFor(slot) != WaitResult.SIGNALED) return false;
        fences[slot] = syncOperations.fenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        return fences[slot] != 0L;
    }

    public long lastWaitDurationNanos() { return lastWaitDurationNanos; }

    public void deleteAll() {
        for (int i = 0; i < fences.length; i++) {
            if (fences[i] != 0L) {
                syncOperations.deleteSync(fences[i]);
                fences[i] = 0L;
            }
        }
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= fences.length) {
            throw new IndexOutOfBoundsException("slot " + slot + " / " + fences.length);
        }
    }
}
