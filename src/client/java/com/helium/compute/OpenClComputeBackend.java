package com.helium.compute;

import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL;
import org.lwjgl.opencl.CLDevice;
import org.lwjgl.opencl.CLPlatform;

import java.util.Comparator;
import java.util.List;

import static org.lwjgl.opencl.CL10.CL_DEVICE_HOST_UNIFIED_MEMORY;
import static org.lwjgl.opencl.CL10.CL_DEVICE_TYPE_GPU;
import static org.lwjgl.opencl.CL10.clBuildProgram;
import static org.lwjgl.opencl.CL10.clCreateBuffer;
import static org.lwjgl.opencl.CL10.clCreateCommandQueue;
import static org.lwjgl.opencl.CL10.clCreateContext;
import static org.lwjgl.opencl.CL10.clCreateKernel;
import static org.lwjgl.opencl.CL10.clCreateProgramWithSource;
import static org.lwjgl.opencl.CL10.clEnqueueNDRangeKernel;
import static org.lwjgl.opencl.CL10.clEnqueueReadBuffer;
import static org.lwjgl.opencl.CL10.clFinish;
import static org.lwjgl.opencl.CL10.clReleaseCommandQueue;
import static org.lwjgl.opencl.CL10.clReleaseContext;
import static org.lwjgl.opencl.CL10.clReleaseKernel;
import static org.lwjgl.opencl.CL10.clReleaseMemObject;
import static org.lwjgl.opencl.CL10.clReleaseProgram;
import static org.lwjgl.opencl.CL10.clSetKernelArg1i;
import static org.lwjgl.opencl.CL10.clSetKernelArg1p;
import static org.lwjgl.system.MemoryStack.stackPush;

/** Optional OpenCL backend. Helium owns the kernels; no Laminar implementation is bundled. */
public final class OpenClComputeBackend implements AutoCloseable {
    private static final String PROGRAM_SOURCE = """
            __kernel void helium_los(
                __global const float* rays,
                __global const uchar* solid,
                __global uchar* result,
                const int size,
                const int min_x,
                const int min_y,
                const int min_z) {
                const int id = get_global_id(0);
                const float ox = rays[id * 6 + 0];
                const float oy = rays[id * 6 + 1];
                const float oz = rays[id * 6 + 2];
                const float tx = rays[id * 6 + 3];
                const float ty = rays[id * 6 + 4];
                const float tz = rays[id * 6 + 5];
                const float dx = tx - ox;
                const float dy = ty - oy;
                const float dz = tz - oz;
                const float distance = sqrt(dx * dx + dy * dy + dz * dz);
                const int steps = max(1, (int)ceil(distance * 2.0f));
                int visible = 1;
                for (int s = 1; s < steps; ++s) {
                    const float t = ((float)s) / ((float)steps);
                    const float px = ox + dx * t;
                    const float py = oy + dy * t;
                    const float pz = oz + dz * t;
                    const int x = (int)floor(px) - min_x;
                    const int y = (int)floor(py) - min_y;
                    const int z = (int)floor(pz) - min_z;
                    if (x < 0 || y < 0 || z < 0 || x >= size || y >= size || z >= size) continue;
                    const int idx = (z * size + y) * size + x;
                    if (solid[idx] != 0) { visible = 0; break; }
                }
                result[id] = (uchar)visible;
            }

            __kernel void helium_flow(
                __global const uchar* blocked,
                __global const int* current,
                __global int* next,
                const int size,
                const int target_index) {
                const int id = get_global_id(0);
                if (blocked[id] != 0) { next[id] = 1073741824; return; }
                if (id == target_index) { next[id] = 0; return; }
                const int plane = size * size;
                const int z = id / plane;
                const int rem = id - z * plane;
                const int y = rem / size;
                const int x = rem - y * size;
                int best = current[id];
                if (x > 0) best = min(best, current[id - 1] + 1);
                if (x + 1 < size) best = min(best, current[id + 1] + 1);
                if (y > 0) best = min(best, current[id - size] + 1);
                if (y + 1 < size) best = min(best, current[id + size] + 1);
                if (z > 0) best = min(best, current[id - plane] + 1);
                if (z + 1 < size) best = min(best, current[id + plane] + 1);
                next[id] = best;
            }
            """;

    private final CLDevice device;
    private final long context;
    private final long queue;
    private final long program;
    private final long losKernel;
    private final long flowKernel;
    private final boolean unifiedMemory;

    private OpenClComputeBackend(CLDevice device, long context, long queue, long program, long losKernel, long flowKernel) {
        this.device = device;
        this.context = context;
        this.queue = queue;
        this.program = program;
        this.losKernel = losKernel;
        this.flowKernel = flowKernel;
        this.unifiedMemory = device.getInfoBoolean(CL_DEVICE_HOST_UNIFIED_MEMORY);
    }

    public static OpenClComputeBackend create() {
        try {
            if (!CL.isCreated()) CL.create();
            CLDevice best = null;
            for (CLPlatform platform : CLPlatform.getPlatforms()) {
                List<CLDevice> devices = platform.getDevices(CL_DEVICE_TYPE_GPU);
                best = devices.stream()
                        .filter(d -> d.getInfoBoolean(CL_DEVICE_HOST_UNIFIED_MEMORY))
                        .max(Comparator.comparingInt(OpenClComputeBackend::score))
                        .orElse(best);
            }
            if (best == null) return null;

            try (var stack = stackPush()) {
                PointerBuffer properties = stack.mallocPointer(3)
                        .put(org.lwjgl.opencl.CL10.CL_CONTEXT_PLATFORM)
                        .put(best.getPlatform().getPointer())
                        .put(0)
                        .flip();
                PointerBuffer devices = stack.mallocPointer(1).put(best.getPointer()).flip();
                var err = stack.callocInt(1);
                long context = clCreateContext(properties, devices, null, 0, err);
                if (err.get(0) != org.lwjgl.opencl.CL10.CL_SUCCESS || context == 0) return null;

                long queue = clCreateCommandQueue(context, best.getPointer(), 0, err);
                if (err.get(0) != org.lwjgl.opencl.CL10.CL_SUCCESS || queue == 0) {
                    clReleaseContext(context);
                    return null;
                }

                long program = clCreateProgramWithSource(context, PROGRAM_SOURCE, err);
                if (err.get(0) != org.lwjgl.opencl.CL10.CL_SUCCESS || program == 0
                        || clBuildProgram(program, best.getPointer(), "", null, 0) != org.lwjgl.opencl.CL10.CL_SUCCESS) {
                    if (program != 0) clReleaseProgram(program);
                    clReleaseCommandQueue(queue);
                    clReleaseContext(context);
                    return null;
                }

                long losKernel = clCreateKernel(program, "helium_los", err);
                long flowKernel = clCreateKernel(program, "helium_flow", err);
                if (err.get(0) != org.lwjgl.opencl.CL10.CL_SUCCESS || losKernel == 0 || flowKernel == 0) {
                    if (losKernel != 0) clReleaseKernel(losKernel);
                    if (flowKernel != 0) clReleaseKernel(flowKernel);
                    clReleaseProgram(program);
                    clReleaseCommandQueue(queue);
                    clReleaseContext(context);
                    return null;
                }
                return new OpenClComputeBackend(best, context, queue, program, losKernel, flowKernel);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int score(CLDevice device) {
        String name = device.getInfoString(org.lwjgl.opencl.CL10.CL_DEVICE_NAME).toLowerCase(java.util.Locale.ROOT);
        int score = 100;
        if (name.contains("intel") || name.contains("uhd") || name.contains("iris")) score += 50;
        if (name.contains("radeon graphics") || name.contains("vega") || name.contains("apu")) score += 40;
        if (name.contains("nvidia") || name.contains("geforce") || name.contains("radeon rx")) score -= 30;
        return score;
    }

    public String deviceName() {
        return device.getInfoString(org.lwjgl.opencl.CL10.CL_DEVICE_NAME);
    }

    public boolean usesUnifiedMemory() {
        return unifiedMemory;
    }

    public boolean[] runLineOfSight(float[] rays, byte[] solid, int size, int minX, int minY, int minZ) {
        if (rays.length % 6 != 0) throw new IllegalArgumentException("rays must contain 6 floats per request");
        int count = rays.length / 6;
        if (count == 0) return new boolean[0];
        long raysBuffer = 0, solidBuffer = 0, resultBuffer = 0;
        try (var stack = stackPush()) {
            var err = stack.callocInt(1);
            var rayData = stack.mallocFloat(rays.length).put(rays).flip();
            var solidData = stack.malloc(solid.length).put(solid).flip();
            var resultData = stack.malloc(count);

            raysBuffer = clCreateBuffer(context, org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY | org.lwjgl.opencl.CL10.CL_MEM_COPY_HOST_PTR, rayData, err);
            solidBuffer = clCreateBuffer(context, org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY | org.lwjgl.opencl.CL10.CL_MEM_COPY_HOST_PTR, solidData, err);
            resultBuffer = clCreateBuffer(context, org.lwjgl.opencl.CL10.CL_MEM_WRITE_ONLY, count, err);
            if (raysBuffer == 0 || solidBuffer == 0 || resultBuffer == 0) return null;

            clSetKernelArg1p(losKernel, 0, raysBuffer);
            clSetKernelArg1p(losKernel, 1, solidBuffer);
            clSetKernelArg1p(losKernel, 2, resultBuffer);
            clSetKernelArg1i(losKernel, 3, size);
            clSetKernelArg1i(losKernel, 4, minX);
            clSetKernelArg1i(losKernel, 5, minY);
            clSetKernelArg1i(losKernel, 6, minZ);

            PointerBuffer global = stack.mallocPointer(1).put(count).flip();
            if (clEnqueueNDRangeKernel(queue, losKernel, 1, null, global, null, null, null) != org.lwjgl.opencl.CL10.CL_SUCCESS) return null;
            if (clFinish(queue) != org.lwjgl.opencl.CL10.CL_SUCCESS) return null;
            if (clEnqueueReadBuffer(queue, resultBuffer, true, 0, resultData, null, null) != org.lwjgl.opencl.CL10.CL_SUCCESS) return null;

            boolean[] out = new boolean[count];
            for (int i = 0; i < count; i++) out[i] = resultData.get(i) != 0;
            return out;
        } finally {
            if (raysBuffer != 0) clReleaseMemObject(raysBuffer);
            if (solidBuffer != 0) clReleaseMemObject(solidBuffer);
            if (resultBuffer != 0) clReleaseMemObject(resultBuffer);
        }
    }

    public int[] runFlowField(byte[] blocked, int size, int targetX, int targetY, int targetZ) {
        int voxels = size * size * size;
        int targetIndex = (targetZ * size + targetY) * size + targetX;
        if (targetX < 0 || targetY < 0 || targetZ < 0 || targetX >= size || targetY >= size || targetZ >= size || blocked[targetIndex] != 0) return null;

        long blockedBuffer = 0, currentBuffer = 0, nextBuffer = 0;
        try (var stack = stackPush()) {
            var err = stack.callocInt(1);
            var blockedData = stack.malloc(blocked.length).put(blocked).flip();
            var currentData = stack.mallocInt(voxels);
            var nextData = stack.mallocInt(voxels);
            for (int i = 0; i < voxels; i++) currentData.put(i, i == targetIndex ? 0 : 1073741824);

            blockedBuffer = clCreateBuffer(context, org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY | org.lwjgl.opencl.CL10.CL_MEM_COPY_HOST_PTR, blockedData, err);
            currentBuffer = clCreateBuffer(context, org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE | org.lwjgl.opencl.CL10.CL_MEM_COPY_HOST_PTR, currentData, err);
            nextBuffer = clCreateBuffer(context, org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE, (long) voxels * Integer.BYTES, err);
            if (blockedBuffer == 0 || currentBuffer == 0 || nextBuffer == 0) return null;

            PointerBuffer global = stack.mallocPointer(1).put(voxels).flip();
            int iterations = Math.min(size * 2 + 4, 160);
            for (int i = 0; i < iterations; i++) {
                clSetKernelArg1p(flowKernel, 0, blockedBuffer);
                clSetKernelArg1p(flowKernel, 1, currentBuffer);
                clSetKernelArg1p(flowKernel, 2, nextBuffer);
                clSetKernelArg1i(flowKernel, 3, size);
                clSetKernelArg1i(flowKernel, 4, targetIndex);
                if (clEnqueueNDRangeKernel(queue, flowKernel, 1, null, global, null, null, null) != org.lwjgl.opencl.CL10.CL_SUCCESS) return null;
                long temp = currentBuffer; currentBuffer = nextBuffer; nextBuffer = temp;
            }
            if (clFinish(queue) != org.lwjgl.opencl.CL10.CL_SUCCESS) return null;
            if (clEnqueueReadBuffer(queue, currentBuffer, true, 0, currentData, null, null) != org.lwjgl.opencl.CL10.CL_SUCCESS) return null;

            int[] distances = new int[voxels];
            currentData.rewind();
            currentData.get(distances);
            return distances;
        } finally {
            if (blockedBuffer != 0) clReleaseMemObject(blockedBuffer);
            if (currentBuffer != 0) clReleaseMemObject(currentBuffer);
            if (nextBuffer != 0) clReleaseMemObject(nextBuffer);
        }
    }

    @Override
    public void close() {
        if (losKernel != 0) clReleaseKernel(losKernel);
        if (flowKernel != 0) clReleaseKernel(flowKernel);
        if (program != 0) clReleaseProgram(program);
        if (queue != 0) clReleaseCommandQueue(queue);
        if (context != 0) clReleaseContext(context);
    }
}
