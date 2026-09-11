package com.helium.compute;

import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL;
import org.lwjgl.opencl.CL10;

import java.nio.IntBuffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

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
import static org.lwjgl.opencl.CL10.clGetDeviceIDs;
import static org.lwjgl.opencl.CL10.clGetDeviceInfo;
import static org.lwjgl.opencl.CL10.clGetPlatformIDs;
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
    private static final int CL_DEVICE_NAME = 0x102B;
    private static final int CL_DEVICE_HOST_UNIFIED_MEMORY = 0x1035;
    private static final long CL_CONTEXT_PLATFORM = 0x1084L;

    private static final int FLOW_INF = 1073741824;

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

    private final long platform;
    private final long device;
    private final long context;
    private final long queue;
    private final long program;
    private final long losKernel;
    private final long flowKernel;
    private final boolean unifiedMemory;
    private final String deviceName;

    private OpenClComputeBackend(long platform, long device, long context, long queue,
                                 long program, long losKernel, long flowKernel,
                                 boolean unifiedMemory, String deviceName) {
        this.platform = platform;
        this.device = device;
        this.context = context;
        this.queue = queue;
        this.program = program;
        this.losKernel = losKernel;
        this.flowKernel = flowKernel;
        this.unifiedMemory = unifiedMemory;
        this.deviceName = deviceName;
    }

    public static OpenClComputeBackend create() {
        try {
            CL.create();
            try (var stack = stackPush()) {
                IntBuffer platformCount = stack.callocInt(1);
                int err = clGetPlatformIDs(null, platformCount);
                if (err != CL10.CL_SUCCESS || platformCount.get(0) == 0) return null;

                PointerBuffer platforms = stack.mallocPointer(platformCount.get(0));
                err = clGetPlatformIDs(platforms, null);
                if (err != CL10.CL_SUCCESS) return null;

                long bestPlatform = 0L;
                long bestDevice = 0L;
                int bestScore = Integer.MIN_VALUE;
                boolean bestUnified = false;
                String bestName = null;

                for (int p = 0; p < platforms.capacity(); p++) {
                    long platform = platforms.get(p);
                    IntBuffer deviceCount = stack.callocInt(1);
                    err = clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, null, deviceCount);
                    if (err != CL10.CL_SUCCESS || deviceCount.get(0) == 0) continue;

                    PointerBuffer devices = stack.mallocPointer(deviceCount.get(0));
                    err = clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, devices, null);
                    if (err != CL10.CL_SUCCESS) continue;

                    for (int d = 0; d < devices.capacity(); d++) {
                        long device = devices.get(d);
                        boolean unified = queryInt(device, CL_DEVICE_HOST_UNIFIED_MEMORY) != 0;
                        String name = queryString(device, CL_DEVICE_NAME);
                        int score = score(name, unified);
                        if (score > bestScore) {
                            bestScore = score;
                            bestPlatform = platform;
                            bestDevice = device;
                            bestUnified = unified;
                            bestName = name;
                        }
                    }
                }

                if (bestDevice == 0L) return null;

                PointerBuffer properties = stack.mallocPointer(3)
                        .put(CL_CONTEXT_PLATFORM)
                        .put(bestPlatform)
                        .put(0L)
                        .flip();
                PointerBuffer devices = stack.mallocPointer(1).put(bestDevice).flip();
                var contextErr = stack.callocInt(1);
                long context = clCreateContext(properties, devices, null, 0L, contextErr);
                if (contextErr.get(0) != CL10.CL_SUCCESS || context == 0L) return null;

                long queue = clCreateCommandQueue(context, bestDevice, 0L, contextErr);
                if (contextErr.get(0) != CL10.CL_SUCCESS || queue == 0L) {
                    clReleaseContext(context);
                    return null;
                }

                long program = clCreateProgramWithSource(context, PROGRAM_SOURCE, contextErr);
                if (contextErr.get(0) != CL10.CL_SUCCESS || program == 0L
                        || clBuildProgram(program, bestDevice, "", null, 0L) != CL10.CL_SUCCESS) {
                    if (program != 0L) clReleaseProgram(program);
                    clReleaseCommandQueue(queue);
                    clReleaseContext(context);
                    return null;
                }

                long losKernel = clCreateKernel(program, "helium_los", contextErr);
                long flowKernel = clCreateKernel(program, "helium_flow", contextErr);
                if (contextErr.get(0) != CL10.CL_SUCCESS || losKernel == 0L || flowKernel == 0L) {
                    if (losKernel != 0L) clReleaseKernel(losKernel);
                    if (flowKernel != 0L) clReleaseKernel(flowKernel);
                    clReleaseProgram(program);
                    clReleaseCommandQueue(queue);
                    clReleaseContext(context);
                    return null;
                }
                return new OpenClComputeBackend(bestPlatform, bestDevice, context, queue,
                        program, losKernel, flowKernel, bestUnified, bestName);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int score(String name, boolean unified) {
        String normalized = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        int score = unified ? 300 : 100;
        if (normalized.contains("intel") || normalized.contains("uhd") || normalized.contains("iris")) score += 50;
        if (normalized.contains("radeon graphics") || normalized.contains("vega") || normalized.contains("apu")) score += 40;
        if (normalized.contains("nvidia") || normalized.contains("geforce") || normalized.contains("radeon rx")) score -= 30;
        return score;
    }

    private static int queryInt(long device, int parameter) {
        try (var stack = stackPush()) {
            IntBuffer value = stack.callocInt(1);
            if (clGetDeviceInfo(device, parameter, value, null) != CL10.CL_SUCCESS) return 0;
            return value.get(0);
        }
    }

    private static String queryString(long device, int parameter) {
        try (var stack = stackPush()) {
            IntBuffer size = stack.callocInt(1);
            if (clGetDeviceInfo(device, parameter, (ByteBuffer) null, size) != CL10.CL_SUCCESS || size.get(0) <= 1) return "OpenCL GPU";
            ByteBuffer value = stack.malloc(size.get(0));
            if (clGetDeviceInfo(device, parameter, value, null) != CL10.CL_SUCCESS) return "OpenCL GPU";
            byte[] bytes = new byte[value.remaining()];
            value.get(bytes);
            int length = bytes.length;
            while (length > 0 && bytes[length - 1] == 0) length--;
            return new String(bytes, 0, length, StandardCharsets.UTF_8);
        }
    }

    public String deviceName() {
        return deviceName;
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

            raysBuffer = clCreateBuffer(context, CL10.CL_MEM_READ_ONLY | CL10.CL_MEM_COPY_HOST_PTR, rayData, err);
            solidBuffer = clCreateBuffer(context, CL10.CL_MEM_READ_ONLY | CL10.CL_MEM_COPY_HOST_PTR, solidData, err);
            resultBuffer = clCreateBuffer(context, CL10.CL_MEM_WRITE_ONLY, count, err);
            if (raysBuffer == 0 || solidBuffer == 0 || resultBuffer == 0) return null;

            clSetKernelArg1p(losKernel, 0, raysBuffer);
            clSetKernelArg1p(losKernel, 1, solidBuffer);
            clSetKernelArg1p(losKernel, 2, resultBuffer);
            clSetKernelArg1i(losKernel, 3, size);
            clSetKernelArg1i(losKernel, 4, minX);
            clSetKernelArg1i(losKernel, 5, minY);
            clSetKernelArg1i(losKernel, 6, minZ);

            PointerBuffer global = stack.mallocPointer(1).put(count).flip();
            if (clEnqueueNDRangeKernel(queue, losKernel, 1, null, global, null, null, null) != CL10.CL_SUCCESS) return null;
            if (clFinish(queue) != CL10.CL_SUCCESS) return null;
            if (clEnqueueReadBuffer(queue, resultBuffer, true, 0, resultData, null, null) != CL10.CL_SUCCESS) return null;

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
            for (int i = 0; i < voxels; i++) currentData.put(i, i == targetIndex ? 0 : FLOW_INF);

            blockedBuffer = clCreateBuffer(context, CL10.CL_MEM_READ_ONLY | CL10.CL_MEM_COPY_HOST_PTR, blockedData, err);
            currentBuffer = clCreateBuffer(context, CL10.CL_MEM_READ_WRITE | CL10.CL_MEM_COPY_HOST_PTR, currentData, err);
            nextBuffer = clCreateBuffer(context, CL10.CL_MEM_READ_WRITE, (long) voxels * Integer.BYTES, err);
            if (blockedBuffer == 0 || currentBuffer == 0 || nextBuffer == 0) return null;

            PointerBuffer global = stack.mallocPointer(1).put(voxels).flip();
            int iterations = Math.min(size * 2 + 4, 160);
            for (int i = 0; i < iterations; i++) {
                clSetKernelArg1p(flowKernel, 0, blockedBuffer);
                clSetKernelArg1p(flowKernel, 1, currentBuffer);
                clSetKernelArg1p(flowKernel, 2, nextBuffer);
                clSetKernelArg1i(flowKernel, 3, size);
                clSetKernelArg1i(flowKernel, 4, targetIndex);
                if (clEnqueueNDRangeKernel(queue, flowKernel, 1, null, global, null, null, null) != CL10.CL_SUCCESS) return null;
                long temp = currentBuffer; currentBuffer = nextBuffer; nextBuffer = temp;
            }
            if (clFinish(queue) != CL10.CL_SUCCESS) return null;
            if (clEnqueueReadBuffer(queue, currentBuffer, true, 0, currentData, null, null) != CL10.CL_SUCCESS) return null;

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
