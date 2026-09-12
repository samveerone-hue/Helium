package com.helium.rentities.entities;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.rentities.gl.GlShader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryUtil;
import org.joml.Matrix4f;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.*;
import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;

/** Dynamic GPU batch for simple entity equipment models. */
public final class RentitiesEquipmentBatcher {
    private static final int FLOATS_PER_VERTEX = 13;
    private static final int FLOAT_STRIDE_BYTES = FLOATS_PER_VERTEX * Float.BYTES;
    private static final Map<Integer, List<float[]>> QUEUED = new LinkedHashMap<>();

    private static int vao;
    private static int vbo;
    private static GlShader shader;
    private static int uViewProjection = -1;
    private static int uTexture = -1;
    private static boolean initialized;

    private RentitiesEquipmentBatcher() {}

    public static boolean enabled() {
        HeliumConfig config = HeliumClient.getConfig();
        return config != null && config.modEnabled && config.entityGpuBatching;
    }

    /** Captures one already-posed equipment model into the frame batch. */
    public static boolean capture(Object model, MatrixStack matrices, int light, Identifier textureId) {
        if (!enabled() || model == null || matrices == null || textureId == null) return false;
        if (!(model instanceof Model<?> genericModel)) return false;

        int texture = resolveTexture(textureId);
        if (texture <= 0) return false;

        try {
            ensureInitialized();
            EquipmentMeshCapturingConsumer consumer = new EquipmentMeshCapturingConsumer(light);
            genericModel.render(matrices, consumer, light, 0, 0xFFFFFFFF);
            float[] vertices = consumer.toTriangulatedArray();
            if (vertices == null || vertices.length == 0) return false;

            synchronized (QUEUED) {
                QUEUED.computeIfAbsent(texture, ignored -> new ArrayList<>()).add(vertices);
            }
            return true;
        } catch (Throwable t) {
            HeliumClient.LOGGER.debug("[Rentities] equipment capture rejected: {}", t.toString());
            return false;
        }
    }

    public static void resetFrame() {
        synchronized (QUEUED) {
            QUEUED.clear();
        }
    }

    public static void flush(Matrix4f viewProjection) {
        if (viewProjection == null || !initialized) return;
        Map<Integer, List<float[]>> local;
        synchronized (QUEUED) {
            if (QUEUED.isEmpty()) return;
            local = new LinkedHashMap<>(QUEUED);
            QUEUED.clear();
        }

        boolean oldCull = glIsEnabled(GL_CULL_FACE);
        boolean oldDepth = glIsEnabled(GL_DEPTH_TEST);
        boolean oldBlend = glIsEnabled(GL_BLEND);
        boolean oldDepthMask = glGetBoolean(GL_DEPTH_WRITEMASK);

        try {
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glUseProgram(shader.id);

            try (FloatBuffer matrix = BufferUtils.createFloatBuffer(16)) {
                viewProjection.get(matrix);
                glUniformMatrix4fv(uViewProjection, false, matrix);
            }

            glUniform1i(uTexture, 0);
            glDisable(GL_CULL_FACE);
            glEnable(GL_DEPTH_TEST);
            glDepthMask(true);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glActiveTexture(GL_TEXTURE0);

            for (Map.Entry<Integer, List<float[]>> entry : local.entrySet()) {
                int texture = entry.getKey();
                List<float[]> chunks = entry.getValue();
                int vertexCount = 0;
                for (float[] chunk : chunks) vertexCount += chunk.length / FLOATS_PER_VERTEX;
                if (vertexCount == 0) continue;

                long bytes = (long) vertexCount * FLOAT_STRIDE_BYTES;
                if (bytes > Integer.MAX_VALUE) continue;

                FloatBuffer upload = MemoryUtil.memAllocFloat((int) (bytes / Float.BYTES));
                try {
                    for (float[] chunk : chunks) upload.put(chunk);
                    upload.flip();
                    glBufferData(GL_ARRAY_BUFFER, bytes, GL_STREAM_DRAW);
                    glBufferSubData(GL_ARRAY_BUFFER, 0L, upload);
                    glBindTexture(GL_TEXTURE_2D, texture);
                    glDrawArrays(GL_TRIANGLES, 0, vertexCount);
                } finally {
                    MemoryUtil.memFree(upload);
                }
            }
        } catch (Throwable t) {
            HeliumClient.LOGGER.debug("[Rentities] equipment batch flush failed: {}", t.toString());
        } finally {
            glDepthMask(oldDepthMask);
            if (oldCull) glEnable(GL_CULL_FACE); else glDisable(GL_CULL_FACE);
            if (oldDepth) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
            if (oldBlend) glEnable(GL_BLEND); else glDisable(GL_BLEND);
            glBindVertexArray(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glUseProgram(0);
        }
    }

    private static int resolveTexture(Identifier id) {
        try {
            AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(id);
            return texture == null ? -1 : texture.getGlId();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static synchronized void ensureInitialized() {
        if (initialized) return;

        shader = GlShader.builder()
                .vert(GlShader.loadResource("equipment/equipment_vert.glsl"))
                .frag(GlShader.loadResource("equipment/equipment_frag.glsl"))
                .compile();

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        int stride = FLOAT_STRIDE_BYTES;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, 4, GL_FLOAT, false, stride, 8L * Float.BYTES);
        glEnableVertexAttribArray(4);
        glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 12L * Float.BYTES);

        uViewProjection = shader.getUniformLocation("uViewProjection");
        uTexture = shader.getUniformLocation("uTexture");
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        initialized = true;
    }

    static final class EquipmentMeshCapturingConsumer implements net.minecraft.client.render.VertexConsumer {
        private final List<float[]> captured = new ArrayList<>();
        private final int fallbackPackedLight;
        private float vx, vy, vz;
        private float nx, ny, nz;
        private float u, v;
        private int color = 0xFFFFFFFF;

        EquipmentMeshCapturingConsumer(int packedLight) {
            this.fallbackPackedLight = packedLight;
        }

        @Override
        public net.minecraft.client.render.VertexConsumer vertex(float x, float y, float z) {
            vx = x; vy = y; vz = z;
            return this;
        }

        @Override
        public net.minecraft.client.render.VertexConsumer color(int r, int g, int b, int a) {
            color = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
            return this;
        }

        @Override
        public net.minecraft.client.render.VertexConsumer color(int packedArgb) {
            color = packedArgb;
            return this;
        }

        @Override
        public net.minecraft.client.render.VertexConsumer texture(float u, float v) {
            this.u = u; this.v = v;
            return this;
        }

        @Override
        public net.minecraft.client.render.VertexConsumer overlay(int u, int v) { return this; }
        @Override
        public net.minecraft.client.render.VertexConsumer light(int u, int v) { return this; }

        @Override
        public net.minecraft.client.render.VertexConsumer normal(float nx, float ny, float nz) {
            this.nx = nx; this.ny = ny; this.nz = nz;
            capture(fallbackPackedLight);
            return this;
        }

        @Override
        public net.minecraft.client.render.VertexConsumer lineWidth(float width) { return this; }

        @Override
        public void vertex(float x, float y, float z, int color,
                           float u, float v, int overlay, int light,
                           float nx, float ny, float nz) {
            this.vx = x; this.vy = y; this.vz = z;
            this.u = u; this.v = v;
            this.color = color;
            this.nx = nx; this.ny = ny; this.nz = nz;
            capture(light);
        }

        private void capture(int packedLight) {
            float a = ((color >>> 24) & 255) / 255.0f;
            float r = ((color >>> 16) & 255) / 255.0f;
            float g = ((color >>> 8) & 255) / 255.0f;
            float b = (color & 255) / 255.0f;
            captured.add(new float[]{
                    vx, vy, vz, nx, ny, nz, u, v,
                    r, g, b, a, Float.intBitsToFloat(packedLight)
            });
        }

        float[] toTriangulatedArray() {
            if (captured.isEmpty() || (captured.size() % 4) != 0) return null;
            int quadCount = captured.size() / 4;
            float[] result = new float[quadCount * 6 * FLOATS_PER_VERTEX];
            int dst = 0;
            for (int q = 0; q < quadCount; q++) {
                int base = q * 4;
                int[] order = {base, base + 1, base + 2, base + 2, base + 3, base};
                for (int source : order) {
                    float[] vertex = captured.get(source);
                    System.arraycopy(vertex, 0, result, dst, FLOATS_PER_VERTEX);
                    dst += FLOATS_PER_VERTEX;
                }
            }
            return result;
        }
    }
}
