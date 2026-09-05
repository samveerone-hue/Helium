package com.helium.rentities.entities;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

import com.helium.HeliumClient;
import net.minecraft.client.MinecraftClient;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves OpenGL texture names from Minecraft 1.21.11 texture identifiers.
 *
 * 1.21.11 no longer exposes the old TextureManager.bindTexture path. In particular,
 * The old TextureManager bind/destroy reflection path is deliberately not used.
 *
 * The 1.21.11 path is:
 *   TextureManager.getTexture (method_4619)
 *       -> AbstractTexture.getGlTexture (method_68004)
 *       -> concrete GlTexture integer handle
 *
 * We resolve the GL name without mutating Minecraft's currently bound texture.
 */
public final class EntityGlTextureResolver {

    private static volatile Object textureManager;
    private static volatile Method getTexMethod;
    private static volatile MethodHandle glTextureGetter;
    private static volatile MethodHandle glTextureIdGetter;

    private static final Map<String, Integer> GL_ID_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Object> GL_TEXTURE_OBJECT_CACHE = new ConcurrentHashMap<>();

    /**
     * Resolves the GL id of Minecraft's own lightmap texture (LightTexture), which
     * isn't looked up via TextureManager like entity/block textures are. Tries
     * resolving LightTexture itself as an AbstractTexture first, and if that yields
     * nothing, scans its fields for whichever one is actually texture-like (by
     * checking the field's declared type for the same getGlTexture/method_68004 shape
     * resolveGpuTexture already knows how to read) and resolves through that instead.
     */
    public static int resolveLightMapGlId(Object lightTexture) {
        if (lightTexture == null) return 0;
        int direct = resolveGlIdFromTextureObject(lightTexture);
        if (direct > 0) return direct;

        Object inner = findAbstractTextureLikeField(lightTexture);
        return inner != null ? resolveGlIdFromTextureObject(inner) : 0;
    }

    private static Object findAbstractTextureLikeField(Object obj) {
        for (Class<?> cls = obj.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (Field f : cls.getDeclaredFields()) {
                Class<?> ft = f.getType();
                if (ft.isPrimitive() || ft == String.class) continue;
                boolean looksLikeTexture = ft.getSimpleName().toLowerCase().contains("texture");
                if (!looksLikeTexture) {
                    for (Method m : ft.getMethods()) {
                        if (m.getParameterCount() == 0
                                && (m.getName().equals("method_68004") || m.getName().equals("getGlTexture"))) {
                            looksLikeTexture = true;
                            break;
                        }
                    }
                }
                if (!looksLikeTexture) continue;
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val != null) return val;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private EntityGlTextureResolver() {}

    /**
     * Resolves a GL texture id from an arbitrary object already known to be (or wrap)
     * an AbstractTexture -- i.e. anything with a getGlTexture()/method_68004() no-arg
     * method returning a GpuTexture. Reuses the same reflective chain resolveGlId(loc)
     * uses internally, just skipping the TextureManager lookup step for callers that
     * already have the texture object in hand (e.g. LightTexture, which isn't looked
     * up by ResourceLocation).
     */
    public static int resolveGlIdFromTextureObject(Object abstractTextureLike) {
        if (abstractTextureLike == null) return 0;
        if (!RenderSystem.isOnRenderThread()) return 0;
        try {
            Object gpuTex = resolveGpuTexture(abstractTextureLike);
            if (gpuTex == null) return 0;
            int id = readGpuTexId(gpuTex);
            return (id > 0 && GL11.glIsTexture(id)) ? id : 0;
        } catch (Throwable t) {
            if (false) {
                HeliumClient.LOGGER.warn(
                        "[Rentities] GL id resolution failed for {}: {}",
                        abstractTextureLike, t.toString());
            }
            return 0;
        }
    }

        private static Object resolveGpuTextureObject(Object loc) {
        try {
            if (textureManager == null || getTexMethod == null) return null;
            return getTexMethod.invoke(textureManager, loc);
        } catch (Throwable ignored) {
            return null;
        }
    }

public static int resolveGlId(Object loc) {
        if (loc == null) return 0;
        // OpenGL texture queries are only valid while the client render context is current.
        if (!RenderSystem.isOnRenderThread()) return 0;

        String key = String.valueOf(loc);
        Integer cached = GL_ID_CACHE.get(key);
        Object cachedTexture = GL_TEXTURE_OBJECT_CACHE.get(key);
        if (cached != null && cachedTexture != null && cached > 0 && GL11.glIsTexture(cached)) {
            Object currentTexture = resolveGpuTextureObject(loc);
            if (currentTexture != null && currentTexture == cachedTexture) {
                return cached;
            }
        }

try {
            ensureMethods(loc);
            if (textureManager == null || getTexMethod == null) return 0;

            Object texObj = getTexMethod.invoke(textureManager, loc);
            if (texObj == null) return 0;

            Object gpuTex = resolveGpuTexture(texObj);
            if (gpuTex == null) return 0;

            int glId = readGpuTexId(gpuTex);
            if (glId > 0 && org.lwjgl.opengl.GL11.glIsTexture(glId)) {
                GL_ID_CACHE.put(key, glId);
                Object currentTexture = resolveGpuTextureObject(loc);
                if (currentTexture != null) {
                    GL_TEXTURE_OBJECT_CACHE.put(key, currentTexture);
                }
                return glId;
            }
        } catch (Throwable t) {
            if (false) {
                HeliumClient.LOGGER.warn(
                        "[Rentities] Texture ID resolution failed for {}: {}",
                        loc, t.toString());
            }
        }
        return 0;
    }

    public static void invalidateCache() {
        GL_ID_CACHE.clear();
        GL_TEXTURE_OBJECT_CACHE.clear();
        textureManager = null;
        getTexMethod = null;
        glTextureGetter = null;
        glTextureIdGetter = null;
    }

    private static synchronized void ensureMethods(Object loc) {
        if (textureManager != null && getTexMethod != null) return;

        Minecraft mc = MinecraftClient.getInstance();
        if (mc == null) return;

        Object tm = mc.getTextureManager();
        if (tm == null) return;

        textureManager = tm;

        // Minecraft 1.21.11: TextureManager#getTexture -> intermediary method_4619.
        Class<?> locClass = loc.getClass();
        for (Method m : tm.getClass().getMethods()) {
            if (m.getParameterCount() != 1) continue;
            if (!m.getParameterTypes()[0].isAssignableFrom(locClass)) continue;

            String name = m.getName();
            if (name.equals("method_4619") || name.equals("getTexture")) {
                getTexMethod = m;
                return;
            }
        }
    }

    private static synchronized Object resolveGpuTexture(Object texObj) throws Throwable {
        if (glTextureGetter != null) {
            return glTextureGetter.invoke(texObj);
        }

        // Minecraft 1.21.11 AbstractTexture#getGlTexture is method_68004.
        for (Method m : texObj.getClass().getMethods()) {
            if (m.getParameterCount() != 0) continue;
            if (m.getReturnType().getName().equals("com.mojang.blaze3d.textures.GpuTexture")
                    || m.getName().equals("method_68004")
                    || m.getName().equals("getGlTexture")) {
                MethodHandle mh = MethodHandles.privateLookupIn(m.getDeclaringClass(), MethodHandles.lookup()).unreflect(m);
                glTextureGetter = mh.asType(
                        mh.type().changeReturnType(Object.class));
                return glTextureGetter.invoke(texObj);
            }
        }

        // Defensive fallback for wrappers that don't expose the inherited getter publicly.
        Class<?> cls = texObj.getClass();
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField("field_56974");
                f.setAccessible(true);
                return f.get(texObj);
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            }
        }
        return null;
    }

    private static synchronized int readGpuTexId(Object gpuTex) throws Throwable {
        if (glTextureIdGetter != null) {
            return (int) glTextureIdGetter.invoke(gpuTex);
        }

        Class<?> cls = gpuTex.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType() != int.class) continue;
                String n = f.getName();
                // Prefer the actual GL handle field when mappings expose it by name.
                if (!n.equals("id") && !n.equals("glId") && !n.equals("texture")
                        && !n.equals("textureId") && !n.equals("handle")
                        && !n.equals("field_64522")) {
                    continue;
                }
                f.setAccessible(true);
                int id = f.getInt(gpuTex);
                if (id > 0) {
                    glTextureIdGetter = MethodHandles.privateLookupIn(f.getDeclaringClass(), MethodHandles.lookup()).unreflectGetter(f);
                    return id;
                }
            }
            cls = cls.getSuperclass();
        }

        // Last-resort compatibility path: GlTexture has a single primary int handle
        // in the OpenGL backend. Do not select arbitrary non-positive fields.
        cls = gpuTex.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType() != int.class) continue;
                f.setAccessible(true);
                int id = f.getInt(gpuTex);
                if (id > 0 && org.lwjgl.opengl.GL11.glIsTexture(id)) {
                    glTextureIdGetter = MethodHandles.privateLookupIn(f.getDeclaringClass(), MethodHandles.lookup()).unreflectGetter(f);
                    return id;
                }
            }
            cls = cls.getSuperclass();
        }

        return 0;
    }
}
