package com.helium.rentities.gl;

import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL30C.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT;
import static org.lwjgl.opengl.GL44C.GL_MAP_COHERENT_BIT;
import static org.lwjgl.opengl.GL44C.GL_MAP_PERSISTENT_BIT;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.glNamedBufferStorage;
import static org.lwjgl.opengl.GL45C.glUnmapNamedBuffer;
import static org.lwjgl.opengl.GL45C.nglMapNamedBufferRange;
import static org.lwjgl.opengl.GL11C.glGetInteger;

/** One immutable buffer object carved into N equally sized, alignment-padded slots. */
public final class GpuRingBuffer {

    private int id;
    private final int slots;
    private final long slotSize;
    private final long stride;
    private long baseAddr;

    public GpuRingBuffer(long bytesPerSlot, int slots, boolean hostVisible) {
        if (bytesPerSlot <= 0) throw new IllegalArgumentException("bytesPerSlot must be > 0");
        if (slots <= 0) throw new IllegalArgumentException("slots must be > 0");

        this.slots = slots;
        this.slotSize = bytesPerSlot;
        int align = Math.max(glGetInteger(GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT), 256);
        this.stride = ((bytesPerSlot + align - 1) / align) * align;

        this.id = glCreateBuffers();
        long total = Math.multiplyExact(stride, (long) slots);
        try {
            if (hostVisible) {
                int flags = GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_MAP_WRITE_BIT;
                glNamedBufferStorage(id, total, flags);
                this.baseAddr = nglMapNamedBufferRange(id, 0, total, flags);
                if (this.baseAddr == 0L) throw new IllegalStateException("persistent map returned null");
            } else {
                glNamedBufferStorage(id, total, 0);
                this.baseAddr = 0L;
            }
        } catch (Throwable t) {
            if (id != 0) glDeleteBuffers(id);
            id = 0;
            throw t;
        }
    }

    public int id() { return id; }
    public int slots() { return slots; }
    public long slotSize() { return slotSize; }
    public long offsetOf(int slot) { return stride * slot; }
    public long addrOf(int slot) { return baseAddr == 0L ? 0L : baseAddr + stride * slot; }

    public void delete() {
        if (id == 0) return;
        if (baseAddr != 0L) {
            glUnmapNamedBuffer(id);
            baseAddr = 0L;
        }
        glDeleteBuffers(id);
        id = 0;
    }
}
