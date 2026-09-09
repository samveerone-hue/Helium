package com.helium.gpu;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.platform.RenderBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

@Environment(EnvType.CLIENT)
public final class GBGL {

    private static volatile boolean gl45available = false;
    private static volatile boolean dsaavailable = false;
    private static volatile boolean bufferstorageavailable = false;
    private static volatile boolean capsinitialized = false;

    private GBGL() {}

    public static void initcaps() {
        if (capsinitialized) return;
        RenderBackend.detect();
        if (!RenderBackend.isOpenGL()) {
            capsinitialized = true;
            return;
        }
        try {
            GLCapabilities caps = GL.getCapabilities();
            gl45available = caps.OpenGL45;
            dsaavailable = caps.GL_ARB_direct_state_access || gl45available;
            bufferstorageavailable = caps.GL_ARB_buffer_storage || gl45available;
            capsinitialized = true;
            HeliumClient.LOGGER.info("gl caps: GL45={}, DSA={}, bufferStorage={}", gl45available, dsaavailable, bufferstorageavailable);
        } catch (Throwable t) {
            capsinitialized = true;
            HeliumClient.LOGGER.warn("[helium] failed to query GL capabilities", t);
        }
    }

    public static boolean isgl45() { return gl45available; }
    public static boolean isdsa() { return dsaavailable; }
    public static boolean isbufferstorage() { return bufferstorageavailable; }

    public static boolean isdsaenabled() {
        HeliumConfig config = HeliumClient.getConfig();
        return RenderBackend.isOpenGL() && config != null && config.directStateAccess && dsaavailable;
    }

    public static int createvao() {
        return GL45.glCreateVertexArrays();
    }

    public static void addvao2vbo(int vao, int binding, int vbo, long offset, int stride) {
        RenderSystem.assertOnRenderThread();
        GL45.glVertexArrayVertexBuffer(vao, binding, vbo, offset, stride);
    }

    public static void enablevaoattrib(int vao, int index) {
        RenderSystem.assertOnRenderThread();
        GL45.glEnableVertexArrayAttrib(vao, index);
    }

    public static void vaoformat(int vao, int attrib, int size, int type, boolean norm, int relativeOffset) {
        RenderSystem.assertOnRenderThread();
        GL45.glVertexArrayAttribFormat(vao, attrib, size, type, norm, relativeOffset);
    }

    public static void vaoformati(int vao, int attrib, int size, int type, int relativeOffset) {
        RenderSystem.assertOnRenderThread();
        GL45.glVertexArrayAttribIFormat(vao, attrib, size, type, relativeOffset);
    }

    public static void bindvaoattrib(int vao, int index, int bindIndex) {
        RenderSystem.assertOnRenderThread();
        GL45.glVertexArrayAttribBinding(vao, index, bindIndex);
    }

    public static int createvbo() {
        return GL45.glCreateBuffers();
    }

    public static void namedbufferdata(int id, @Nullable ByteBuffer buffer, int usage) {
        RenderSystem.assertOnRenderThread();
        if (buffer == null) {
            GL45.glNamedBufferData(id, MemoryUtil.NULL, usage);
        } else {
            GL45.glNamedBufferData(id, buffer, usage);
        }
    }

    public static void addebo2vao(int vao, int ebo) {
        RenderSystem.assertOnRenderThread();
        GL45.glVertexArrayElementBuffer(vao, ebo);
    }

    public static int makefbo() {
        RenderSystem.assertOnRenderThread();
        if (isdsaenabled()) {
            return GL45.glCreateFramebuffers();
        } else {
            return GL30.glGenFramebuffers();
        }
    }

    public static void namedframebuffertexture(int fbo, int attachment, int texture, int level) {
        RenderSystem.assertOnRenderThread();
        GL45.glNamedFramebufferTexture(fbo, attachment, texture, level);
    }

    public static void namedframebuffertexture(int fbo, int attachment, int texture) {
        namedframebuffertexture(fbo, attachment, texture, 0);
    }

    public static void unbindframebuffer() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    public static int make2dtexture() {
        RenderSystem.assertOnRenderThread();
        if (isdsaenabled()) {
            return GL45.glCreateTextures(GL11.GL_TEXTURE_2D);
        } else {
            int id = GL11.glGenTextures();
            return id;
        }
    }

    public static void textureparameter(int texture, int name, int param) {
        RenderSystem.assertOnRenderThread();
        GL45.glTextureParameteri(texture, name, param);
    }

    public static void texturestorage(int texture, int format, int w, int h) {
        RenderSystem.assertOnRenderThread();
        GL45.glTextureStorage2D(texture, 1, format, w, h);
    }

    public static int createrbo() {
        RenderSystem.assertOnRenderThread();
        return GL45.glCreateRenderbuffers();
    }

    public static int genrbo() {
        RenderSystem.assertOnRenderThread();
        return GL30.glGenRenderbuffers();
    }

    public static void bindrbo(int id) {
        RenderSystem.assertOnRenderThread();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, id);
    }

    public static void storagerbo(int w, int h) {
        RenderSystem.assertOnRenderThread();
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, w, h);
    }

    public static void storagenamedrbo(int rbo, int w, int h) {
        RenderSystem.assertOnRenderThread();
        GL45.glNamedRenderbufferStorage(rbo, GL14.GL_DEPTH_COMPONENT24, w, h);
    }

    public static void framebufferrbo(int rbo) {
        RenderSystem.assertOnRenderThread();
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, rbo);
    }

    public static void framebuffernamedrbo(int fbo, int rbo) {
        RenderSystem.assertOnRenderThread();
        GL45.glNamedFramebufferRenderbuffer(fbo, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, rbo);
    }

    public static void deleterbo(int rbo) {
        RenderSystem.assertOnRenderThread();
        GL30.glDeleteRenderbuffers(rbo);
    }
}
