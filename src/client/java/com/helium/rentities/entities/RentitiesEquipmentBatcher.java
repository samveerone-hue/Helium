package com.helium.rentities.entities;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.rentities.gl.GlShader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;
import org.joml.Matrix4f;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL15C.*;

/**
 * Small dynamic GPU batch for simple entity equipment models.
 *
 * The equipment model has already been posed by vanilla when this capture point is hit.
 * We therefore preserve the exact model geometry and matrix transform, but replace the
 * per-entity command submission with one VBO draw per texture for the frame.
 *
 * Complex material cases (trim/dye/glint) are deliberately rejected by the caller until
 * their material layers can be represented without losing vanilla visual fidelity.
 */
public final class RentitiesEquipmentBatcher {
    private static final int FLOATS_PER_VERTEX = 13;
    private static final int FLOAT_STRIDE_BYTES = FLOATS_PER_VERTEX * Float.BYTES;

    private static final Map<Integer, List<float[]>> QUEUED = new LinkedHashMap<>();

    private static int vao;
    private static int vbo;
    private static GlShader shader;
    private static int uViewProjection = -1;
    private static int uTexture = -1;
    private static int uLight = -1;
    private static int uColor = -1;
    private static boolean initialized;

    private RentitiesEquipmentBatcher() {}

    public static boolean enabled() {
        HeliumConfig config = HeliumClient.getConfig();
        return config != null && config.modEnabled && config.entityGpuBatching;
    }

    /**
     * Captures one already-posed model into the frame batch.
     *
     * @return true when vanilla's EquipmentRenderer call can be cancelled safely.
     */
    public static boolean capture(
            Object model,
            MatrixStack matrices,
            int light,
            Identifier textureId) {
        if (!enabled() || model == null || matrices == null || textureId == null) return false;
        if (!(model instanceof net.minecraft.client.model.Model<?> genericModel)) return false;

        int texture = resolveTexture(textureId);
        if (texture <= 0) return false;

        try {
            ensureInitialized();
            EquipmentMeshCapturingConsumer consumer = new EquipmentMeshCapturingConsumer(light);
            genericModel.render(matrices, consumer, light, 0, 0xFFFFFFFF);
            float[] vertices = consumer.toArray();
            if (vertices.length == 0) return false;

            synchronized (QUEUED) {
                QUEUED.computeIfAbsent(texture, ignored -> new ArrayList<>()).add(vertices);
            }
            return true;
        } catch (Throwable t) {
            if (HeliumClient.LOGGER != null) {
                HeliumClient.LOGGER.debug("[Rentities] equipment capture rejected: {}", t.toString());
            }
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
                    glBufferData(GL_ARRAY_BUFFER, upload, GL_STREAM_DRAW);

                    glActiveTexture(GL_TEXTURE0);
                    glBindTexture(GL_TEXTURE_2D, texture);
                    glDrawArrays(GL_TRIANGLES, 0, vertexCount);
                } finally {
                    MemoryUtil.memFree(upload);
                }
            }
        } catch (Throwable t) {
            HeliumClient.LOGGER.debug("[Rentities] equipment batch flush failed: {}", t.toString());
        } finally {
            glBindVertexArray(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glUseProgram(0);
            GL30C.glDisableVertexAttribArray(0);
            GL30C.glDisableVertexAttribArray(1);
            GL30C.glDisableVertexAttribArray(2);
            GL30C.glDisableVertexAttribArray(3);
            GL30C.glDisableVertexAttribArray(4);
        }
    }

    private static int resolveTexture(Identifier id) {
        try {
            AbstractTexture texture = MinecraftClient.getInstance()
                    .getTextureManager()
                    .getTexture(id);
            if (texture == null) return -1;
            return texture.getGlId();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static void ensureInitialized() {
        if (initialized) return;

        shader = GlShader.builder()
                .vert(GlShader.loadResource("equipment/equipment_vert.glsl"))
                .frag(GlShader.loadResource("equipment/equipment_frag.glsl"))
                .compile();

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, FLOAT_STRIDE_BYTES * 4096L, GL_STREAM_DRAW);

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
        uLight = shader.getUniformLocation("uLight");
        uColor = shader.getUniformLocation("uColor");
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        initialized = true;
    }

    static final class EquipmentMeshCapturingConsumer implements net.minecraft.client.render.VertexConsumer {
        private final List<float[]> captured = new ArrayList<>();
        private final int packedLight;
        private float vx, vy, vz;
        private float nx, ny, nz;
        private float u, v;
        private int color = 0xFFFFFFFF;

        EquipmentMeshCapturingConsumer(int packedLight) {
            this.packedLight = packedLight;
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
            capture();
            return this;
        }
        @Override
        public net.minecraft.client.render.VertexConsumer lineWidth(float width) { return this; }

        @Override
        public void vertex(float x, float y, float z, int color,
                           float u, float v, int overlay, int light,
                           float nx, float ny, float nz) {
            this.u = u; this.v = v;
            this.color = color;
            this.nx = nx; this.ny = ny; this.nz = nz;
            this.vx = x; this.vy = y; this.vz = z;
            capture();
        }

        private void capture() {
            float a = ((color >>> 24) & 255) / 255.0f;
            float r = ((color >>> 16) & 255) / 255.0f;
            float g = ((color >>> 8) & 255) / 255.0f;
            float b = (color & 255) / 255.0f;
            captured.add(new float[]{vx, vy, vz, nx, ny, nz, u, v, r, g, b, a, Float.intBitsToFloat(packedLight)});
        }

        float[] toArray() {
            float[] result = new float[captured.size() * FLOATS_PER_VERTEX];
            int dst = 0;
            for (float[] vertex : captured) {
                System.arraycopy(vertex, 0, result, dst, vertex.length);
                dst += vertex.length;
            }
            return result;
        }
    }
}
