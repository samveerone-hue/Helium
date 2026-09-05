package com.helium.rentities.entities;

import net.minecraft.client.render.VertexConsumer;

import java.util.ArrayList;
import java.util.List;

public class EntityMeshCapturingConsumer implements VertexConsumer {

    private final List<float[]> captured = new ArrayList<>(); //

    private float vx, vy, vz;
    private float vnx, vny, vnz;
    private float vu, vv;
    private int currentBone = 0;

    private float pivotX = 0, pivotY = 0, pivotZ = 0;
    private boolean hasPivot = false;

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
        this.pivotX = 0;
        this.pivotY = 0;
        this.pivotZ = 0;
        this.hasPivot = false;
    }

    public int capturedVertexCount() {
        return captured.size();
    }

    public float[] bakeAndReset() {
        // Preserve Minecraft's vertex order exactly. UVs are attached to the
        // corresponding vertex, so reversing a quad here mirrors/flips the texture.
        float[] result = new float[captured.size() * 9];
        int offset = 0;
        for (float[] vertex : captured) {
            System.arraycopy(vertex, 0, result, offset, 9);
            offset += 9;
        }
        captured.clear();
        return result;
    }

    public void reset() {
        captured.clear();
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        this.vx = x;
        this.vy = y;
        this.vz = z;
        this.lastNormalMatrix.identity();
        return this;
    }

    private org.joml.Matrix3f lastNormalMatrix = new org.joml.Matrix3f();

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
        org.joml.Vector3f norm = new org.joml.Vector3f(nx, ny, nz).mul(lastNormalMatrix);
        this.vnx = norm.x;
        this.vny = norm.y;
        this.vnz = norm.z;
        
        float fx = hasPivot ? vx - pivotX : vx;
        float fy = hasPivot ? vy - pivotY : vy;
        float fz = hasPivot ? vz - pivotZ : vz;
        
        captured.add(new float[]{fx, fy, fz, vnx, vny, vnz, vu, vv, currentBone});
        return this;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        float fx = hasPivot ? x - pivotX : x;
        float fy = hasPivot ? y - pivotY : y;
        float fz = hasPivot ? z - pivotZ : z;

        // Preserve the exact UV pair supplied by ModelPart.Cube. Do not infer or mirror
        // coordinates from face winding: vanilla's generated vertex order is the source
        // of truth for entity texture orientation.
        captured.add(new float[]{fx, fy, fz, nx, ny, nz, u, v, currentBone});
    }
}
