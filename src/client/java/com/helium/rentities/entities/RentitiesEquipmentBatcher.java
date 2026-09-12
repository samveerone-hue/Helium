package com.helium.rentities.entities;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.rentities.gl.GlShader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryUtil;

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

/** Dynamic GPU batch for simple entity equipment and held-item geometry. */
public final class RentitiesEquipmentBatcher {
    private static final int FLOATS_PER_VERTEX = 13;
    private static final int FLOAT_STRIDE_BYTES = FLOATS_PER_VERTEX * Float.BYTES;
    private static final Map<Integer, List<float[]>> QUEUED = new LinkedHashMap<>();

    private static int vao;
    private static int vbo;
    private static GlShader shader;
    private static int uViewProjection = -1;
    private static int uTexture = -1;
    private static int uLightMap = -1;
    private static int uHasLightMap = -1;
    private static boolean initialized;

    private RentitiesEquipmentBatcher() {}

    public static boolean enabled() {
        HeliumConfig config = HeliumClient.getConfig();
        return config != null && config.modEnabled && config.entityGpuBatching;
    }

    public static boolean capture(Object model, MatrixStack matrices, int light, Identifier textureId) {
        if (!enabled() || model == null || matrices == null || textureId == null) return false;
        if (!(model instanceof Model<?> genericModel)) return false;
        int texture = EntityGlTextureResolver.resolveGlId(textureId);
        if (texture <= 0) return false;
        try {
            ensureInitialized();
            EquipmentMeshCapturingConsumer consumer = new EquipmentMeshCapturingConsumer(light);
            genericModel.render(matrices, consumer, light, 0, 0xFFFFFFFF);
            float[] vertices = consumer.toTriangulatedArray();
            if (vertices == null || vertices.length == 0) return false;
            queue(texture, vertices);
            return true;
        } catch (Throwable t) {
            HeliumClient.LOGGER.debug("[Rentities] equipment capture rejected: {}", t.toString());
            return false;
        }
    }

    public static boolean captureBakedQuads(MatrixStack matrices, List<BakedQuad> quads, int[] tints, int light) {
        if (!enabled() || matrices == null || quads == null || quads.isEmpty()) return false;
        try {
            ensureInitialized();
            Matrix4f transform = new Matrix4f(matrices.peek().getPositionMatrix());
            LinkedHashMap<Integer, List<float[]>> local = new LinkedHashMap<>();
            for (BakedQuad quad : quads) {
                if (quad == null || quad.sprite() == null) return false;
                int texture = EntityGlTextureResolver.resolveGlId(quad.sprite().getAtlasId());
                if (texture <= 0) return false;
                int tint = 0xFFFFFFFF;
                int tintIndex = quad.tintIndex();
                if (tintIndex >= 0) {
                    if (tints == null || tintIndex >= tints.length) return false;
                    tint = tints[tintIndex];
                }
                List<float[]> out = local.computeIfAbsent(texture, ignored -> new ArrayList<>());
                for (int i = 0; i < 4; i++) {
                    Vector3fc pos = quad.getPosition(i);
                    long packedUv = quad.getTexcoords(i);
                    float u = Float.intBitsToFloat((int) packedUv);
                    float v = Float.intBitsToFloat((int) (packedUv >>> 32));
                    org.joml.Vector4f p = new org.joml.Vector4f(pos.x(), pos.y(), pos.z(), 1f).mul(transform);
                    out.add(new float[]{
                            p.x(), p.y(), p.z(),
                            0f, 1f, 0f,
                            u, v,
                            ((tint >>> 24) & 255) / 255f,
                            ((tint >>> 16) & 255) / 255f,
                            ((tint >>> 8) & 255) / 255f,
                            (tint & 255) / 255f,
                            Float.intBitsToFloat(light)
                    });
                }
            }
            for (Map.Entry<Integer, List<float[]>> entry : local.entrySet()) {
                List<float[]> vertices = entry.getValue();
                if (vertices.size() % 4 != 0) return false;
                float[] triangulated = new float[(vertices.size() / 4) * 6 * FLOATS_PER_VERTEX];
                int dst = 0;
                for (int base = 0; base < vertices.size(); base += 4) {
                    int[] order = {base, base + 1, base + 2, base + 2, base + 3, base};
                    for (int source : order) {
                        float[] vertex = vertices.get(source);
                        System.arraycopy(vertex, 0, triangulated, dst, FLOATS_PER_VERTEX);
                        dst += FLOATS_PER_VERTEX;
                    }
                }
                queue(entry.getKey(), triangulated);
            }
            return true;
        } catch (Throwable t) {
            HeliumClient.LOGGER.debug("[Rentities] held-item capture rejected: {}", t.toString());
            return false;
        }
    }

    private static void queue(int texture, float[] vertices) {
        synchronized (QUEUED) {
            QUEUED.computeIfAbsent(texture, ignored -> new ArrayList<>()).add(vertices);
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
        int oldActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        int oldTexture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
        int oldBlendSrc = glGetInteger(GL_BLEND_SRC_RGB);
        int oldBlendDst = glGetInteger(GL_BLEND_DST_RGB);
        glActiveTexture(GL_TEXTURE1);
        int oldTexture1 = glGetInteger(GL_TEXTURE_BINDING_2D);
        glActiveTexture(GL_TEXTURE0);

        try {
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glUseProgram(shader.id);

            FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
            viewProjection.get(matrix);
            matrix.flip();
            glUniformMatrix4fv(uViewProjection, false, matrix);

            int lightTexId = EntityGlTextureResolver.resolveLightMapGlId(
                    MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager());
            boolean hasLightMap = lightTexId > 0 && glIsTexture(lightTexId);
            if (uLightMap >= 0) {
                glUniform1i(uLightMap, 1);
                glUniform1i(uHasLightMap, hasLightMap ? 1 : 0);
            }
            if (hasLightMap) {
                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_2D, lightTexId);
                glActiveTexture(GL_TEXTURE0);
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
                    glActiveTexture(GL_TEXTURE0);
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
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, oldTexture0);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, oldTexture1);
            glBlendFunc(oldBlendSrc, oldBlendDst);
            glDepthMask(oldDepthMask);
            if (oldCull) glEnable(GL_CULL_FACE); else glDisable(GL_CULL_FACE);
            if (oldDepth) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
            if (oldBlend) glEnable(GL_BLEND); else glDisable(GL_BLEND);
            glActiveTexture(oldActiveTexture);
            glBindVertexArray(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glUseProgram(0);
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
        uLightMap = shader.getUniformLocation("uLightMap");
        uHasLightMap = shader.getUniformLocation("uHasLightMap");
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        initialized = true;
    }

    static final class EquipmentMeshCapturingConsumer implements net.minecraft.client.render.VertexConsumer {
        private final List<float[]> captured = new ArrayList<>();
        private final int fallbackPackedLight;
        private float vx, vy, vz, vnx, vny, vnz, vu, vv;
        private int color = 0xFFFFFFFF;
        EquipmentMeshCapturingConsumer(int packedLight) { this.fallbackPackedLight = packedLight; }
        @Override public net.minecraft.client.render.VertexConsumer vertex(float x, float y, float z) { vx = x; vy = y; vz = z; return this; }
        @Override public net.minecraft.client.render.VertexConsumer color(int r, int g, int b, int a) { color = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255); return this; }
        @Override public net.minecraft.client.render.VertexConsumer color(int packedArgb) { color = packedArgb; return this; }
        @Override public net.minecraft.client.render.VertexConsumer texture(float u, float v) { vu = u; vv = v; return this; }
        @Override public net.minecraft.client.render.VertexConsumer overlay(int u, int v) { return this; }
        @Override public net.minecraft.client.render.VertexConsumer light(int u, int v) { return this; }
        @Override public net.minecraft.client.render.VertexConsumer normal(float nx, float ny, float nz) { vnx = nx; vny = ny; vnz = nz; capture(fallbackPackedLight); return this; }
        @Override public net.minecraft.client.render.VertexConsumer lineWidth(float width) { return this; }
        @Override public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) { vx=x; vy=y; vz=z; vu=u; vv=v; this.color=color; vnx=nx; vny=ny; vnz=nz; capture(light); }
        private void capture(int packedLight) {
            float a=((color>>>24)&255)/255f, r=((color>>>16)&255)/255f, g=((color>>>8)&255)/255f, b=(color&255)/255f;
            captured.add(new float[]{vx,vy,vz,vnx,vny,vnz,vu,vv,r,g,b,a,Float.intBitsToFloat(packedLight)});
        }
        float[] toTriangulatedArray() {
            if (captured.isEmpty() || captured.size()%4 != 0) return null;
            int quadCount=captured.size()/4;
            float[] result=new float[quadCount*6*FLOATS_PER_VERTEX];
            int dst=0;
            for(int q=0;q<quadCount;q++) {
                int base=q*4;
                int[] order={base,base+1,base+2,base+2,base+3,base};
                for(int source:order){ System.arraycopy(captured.get(source),0,result,dst,FLOATS_PER_VERTEX); dst+=FLOATS_PER_VERTEX; }
            }
            return result;
        }
    }
}
