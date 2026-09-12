package com.helium.rentities.entities;

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
        float[] result = new float[captured.size() * 9];
        int offset = 0;
        for (float[] vertex : captured) {
            System.arraycopy(vertex, 0, result, offset, 9);
            offset += 9;
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
