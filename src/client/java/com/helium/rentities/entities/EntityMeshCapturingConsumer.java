package com.helium.rentities.entities;

import com.helium.math.SimdMath;
import net.minecraft.client.render.VertexConsumer;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures the bind-pose mesh emitted by Minecraft's model parts.
 *
 * The VertexConsumer receives the normal after Minecraft has applied the model
 * transform, so applying a second (and stale) normal matrix here was incorrect.
 * The consumer intentionally preserves vertex order and source UVs verbatim.
 */
public final class EntityMeshCapturingConsumer implements VertexConsumer {

    private final List<float[]> captured = new ArrayList<>();

    private float vx, vy, vz;
    private float vnx, vny, vnz;
    private float vu, vv;
    private int currentBone;

    private float pivotX, pivotY, pivotZ;
    private boolean hasPivot;

    public void setBone(int boneIndex) {
        this.currentBone = boneIndex;
    }

    public void setBonePivot(float px, float py, float pz) {
        this.pivotX = px;
        this.pivotY = py;
        this.pivotZ = pz;
        this.hasPivot = true;
    }

    public void clearBonePivot() {
        this.pivotX = 0.0f;
        this.pivotY = 0.0f;
        this.pivotZ = 0.0f;
        this.hasPivot = false;
    }

    public int capturedVertexCount() {
        return captured.size();
    }

    public float[] bakeAndReset() {
        // Preserve Minecraft's vertex order exactly. UVs are attached to the
        // corresponding vertex, so reversing a quad here mirrors the texture.
        int vertexCount = captured.size();
        float[] result = new float[vertexCount * 9];
        int offset = 0;
        for (float[] vertex : captured) {
            System.arraycopy(vertex, 0, result, offset, 9);
            offset += 9;
        }

        // Rentities uploads large contiguous mesh batches. Normalize the captured
        // vertex normals once here so the shader does not need to compensate for
        // malformed model-space normals. The inverse lengths are scalar because
        // Java's Vector API does not make a 3-float AoS stride cheap; the expensive
        // contiguous component-wise multiply is delegated to the actual SIMD path.
        if (vertexCount >= 32) {
            SimdMath.init();
            if (SimdMath.isVectorApiAvailable()) {
                float[] normals = new float[vertexCount * 3];
                float[] scale = new float[normals.length];
                for (int i = 0; i < vertexCount; i++) {
                    int src = i * 9;
                    int dst = i * 3;
                    float x = result[src + 3];
                    float y = result[src + 4];
                    float z = result[src + 5];
                    normals[dst] = x;
                    normals[dst + 1] = y;
                    normals[dst + 2] = z;
                    float lenSq = x * x + y * y + z * z;
                    float invLen = lenSq > 1.0e-8f ? (float) (1.0 / Math.sqrt(lenSq)) : 0.0f;
                    scale[dst] = invLen;
                    scale[dst + 1] = invLen;
                    scale[dst + 2] = invLen;
                }

                float[] normalized = new float[normals.length];
                SimdMath.batchMultiply(normals, scale, normalized, normalized.length);
                for (int i = 0; i < vertexCount; i++) {
                    int src = i * 3;
                    int dst = i * 9;
                    result[dst + 3] = normalized[src];
                    result[dst + 4] = normalized[src + 1];
                    result[dst + 5] = normalized[src + 2];
                }
            }
        }

        reset();
        return result;
    }

    public void reset() {
        captured.clear();
        vx = vy = vz = 0.0f;
        vnx = vny = vnz = 0.0f;
        vu = vv = 0.0f;
        currentBone = 0;
        clearBonePivot();
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        this.vx = x;
        this.vy = y;
        this.vz = z;
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        return this;
    }

    @Override
    public VertexConsumer color(int packedArgb) {
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        this.vu = u;
        this.vv = v;
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer lineWidth(float width) {
        return this;
    }

    @Override
    public VertexConsumer normal(float nx, float ny, float nz) {
        this.vnx = nx;
        this.vny = ny;
        this.vnz = nz;
        capture(vx, vy, vz);
        return this;
    }

    @Override
    public void vertex(float x, float y, float z, int color, float u, float v,
                       int overlay, int light, float nx, float ny, float nz) {
        this.vu = u;
        this.vv = v;
        this.vnx = nx;
        this.vny = ny;
        this.vnz = nz;
        capture(x, y, z);
    }

    private void capture(float x, float y, float z) {
        float fx = hasPivot ? x - pivotX : x;
        float fy = hasPivot ? y - pivotY : y;
        float fz = hasPivot ? z - pivotZ : z;
        captured.add(new float[]{fx, fy, fz, vnx, vny, vnz, vu, vv, currentBone});
    }
}
