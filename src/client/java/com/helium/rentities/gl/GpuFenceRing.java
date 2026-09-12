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
import static org.lwjgl.opengl.GL11C.glFinish;

/** Per-slot GPU fences for a {@link GpuRingBuffer}. */
public final class GpuFenceRing {

    private static final long WAIT_CHUNK_NS = 1_000_000_000L;
    private final long[] fences;

    public GpuFenceRing(int slots) {
        if (slots <= 0) throw new IllegalArgumentException("slots must be > 0");
        this.fences = new long[slots];
    }

    /** Blocks until the GPU is done with everything that read {@code slot}. */
    public void waitFor(int slot) {
        checkSlot(slot);
        long fence = fences[slot];
        if (fence == 0L) return;

        int flags = GL_SYNC_FLUSH_COMMANDS_BIT;
        int spins = 0;
        while (true) {
            int result = glClientWaitSync(fence, flags, WAIT_CHUNK_NS);
            if (result == GL_ALREADY_SIGNALED || result == GL_CONDITION_SATISFIED) break;
            if (result == GL_WAIT_FAILED) {
                HeliumClient.LOGGER.error("[Entity] glClientWaitSync failed on ring slot {}; forcing GPU completion", slot);
                glFinish();
                break;
            }
            flags = 0;
            if (++spins == 1) {
                HeliumClient.LOGGER.warn("[Entity] GPU more than {} frames behind on ring slot {}", fences.length, slot);
            }
        }
        glDeleteSync(fence);
        fences[slot] = 0L;
    }

    /** Records that all commands issued so far must complete before {@code slot} is reused. */
    public void signal(int slot) {
        checkSlot(slot);
        if (fences[slot] != 0L) waitFor(slot);
        fences[slot] = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    public void deleteAll() {
        for (int i = 0; i < fences.length; i++) {
            if (fences[i] != 0L) {
                glDeleteSync(fences[i]);
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
