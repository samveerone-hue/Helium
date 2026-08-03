package com.helium.util;

import com.helium.HeliumClient;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.Screenshot;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.util.Mth;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Queue;

public final class VersionMethodResolver {

    private static volatile boolean initialized = false;

    private static boolean hasfloatsincos = false;
    private static boolean hasdoublesincos = false;
    private static boolean hasblittoscreen = false;
    private static boolean haslegacydraw = false;
    private static boolean haslegacyfbo = false;
    private static boolean hastakescreenshot = false;
    private static boolean hastogglefullscreen = false;
    private static boolean haslogglerror = false;
    private static boolean haslogonglerror = false;
    private static boolean hasinactivitylimiter = false;
    private static boolean hasmodernparticlerenderer = false;

    private static MethodHandle sinfloathandle = null;
    private static MethodHandle cosfloathandle = null;
    private static MethodHandle sindoublehandle = null;
    private static MethodHandle cosdoublehandle = null;
    private static MethodHandle blittoscreenhandle = null;
    private static MethodHandle legacydrawhandle = null;
    private static MethodHandle getinactivitylimiterhandle = null;
    private static MethodHandle getparticlesfromrenderer = null;

    private static Field fbofield = null;
    private static Field particlesmapfield = null;

    private VersionMethodResolver() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        resolvemath();
        resolveframebuffer();
        resolvescreenshot();
        resolvewindow();
        resolveminecraftclient();
        resolveparticle();
    }

    private static void resolvemath() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            int sincount = 0;
            int coscount = 0;

            for (Method m : Mth.class.getDeclaredMethods()) {
                if (m.getParameterCount() != 1) continue;
                if (m.getReturnType() != float.class) continue;
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;

                Class<?> paramType = m.getParameterTypes()[0];
                if (paramType != float.class && paramType != double.class) continue;

                m.setAccessible(true);
                MethodHandle handle = lookup.unreflect(m);

                if (paramType == float.class) {
                    if (sinfloathandle == null) {
                        sinfloathandle = handle;
                        sincount++;
                    } else if (cosfloathandle == null) {
                        cosfloathandle = handle;
                        coscount++;
                    }
                    hasfloatsincos = true;
                } else if (paramType == double.class) {
                    if (sindoublehandle == null) {
                        sindoublehandle = handle;
                        sincount++;
                    } else if (cosdoublehandle == null) {
                        cosdoublehandle = handle;
                        coscount++;
                    }
                    hasdoublesincos = true;
                }
            }

            if (hasfloatsincos) HeliumClient.LOGGER.info("detected legacy Mth API (float sin/cos)");
            if (hasdoublesincos) HeliumClient.LOGGER.info("detected modern Mth API (double sin/cos)");
            if (!hasfloatsincos && !hasdoublesincos) HeliumClient.LOGGER.warn("no Mth sin/cos found");
        } catch (Throwable t) {
            HeliumClient.LOGGER.warn("failed to resolve Mth API ({})", t.getMessage());
        }
    }

    private static void resolveframebuffer() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            for (Method m : RenderTarget.class.getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == void.class
                        && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    if (blittoscreenhandle == null) {
                        try {
                            m.setAccessible(true);
                            blittoscreenhandle = lookup.unreflect(m);
                            hasblittoscreen = true;
                        } catch (Throwable ignored) {}
                    }
                }

                if (m.getParameterCount() == 3 && m.getReturnType() == void.class
                        && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params[0] == int.class && params[1] == int.class && params[2] == boolean.class) {
                        m.setAccessible(true);
                        legacydrawhandle = lookup.unreflect(m);
                        haslegacydraw = true;
                    }
                }
            }

            for (Field f : RenderTarget.class.getDeclaredFields()) {
                if (f.getType() == int.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    String fname = f.getName().toLowerCase();
                    if (fname.equals("fbo") || fname.equals("field_1042")) {
                        f.setAccessible(true);
                        fbofield = f;
                        haslegacyfbo = true;
                        break;
                    }
                }
            }

            if (hasblittoscreen) HeliumClient.LOGGER.info("detected RenderTarget blitToScreen");
            if (haslegacydraw) HeliumClient.LOGGER.info("detected RenderTarget legacy draw");
            if (haslegacyfbo) HeliumClient.LOGGER.info("detected RenderTarget fbo field");
        } catch (Throwable t) {
            HeliumClient.LOGGER.warn("failed to resolve RenderTarget API ({})", t.getMessage());
        }
    }

    private static void resolvescreenshot() {
        try {
            for (Method m : Screenshot.class.getDeclaredMethods()) {
                if (m.getParameterCount() == 2 && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params[0] == RenderTarget.class && params[1] == java.util.function.Consumer.class) {
                        hastakescreenshot = true;
                        HeliumClient.LOGGER.info("detected modern Screenshot API (takeScreenshot)");
                        break;
                    }
                }
            }
            if (!hastakescreenshot) {
                HeliumClient.LOGGER.info("detected legacy Screenshot API");
            }
        } catch (Throwable t) {
            HeliumClient.LOGGER.warn("failed to resolve Screenshot API ({})", t.getMessage());
        }
    }

    private static void resolvewindow() {
        try {
            hastogglefullscreen = true;
            haslogglerror = true;
            haslogonglerror = true;
        } catch (Throwable t) {
            HeliumClient.LOGGER.warn("failed to resolve Window API ({})", t.getMessage());
        }
    }

    private static void resolveminecraftclient() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            for (Method m : Minecraft.class.getDeclaredMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;

                Class<?> retType = m.getReturnType();
                if (retType.getSimpleName().contains("InactivityFpsLimiter") ||
                        retType.getName().contains("class_9919")) {
                    m.setAccessible(true);
                    getinactivitylimiterhandle = lookup.unreflect(m);
                    hasinactivitylimiter = true;
                    HeliumClient.LOGGER.info("detected modern Minecraft API (InactivityFpsLimiter)");
                    break;
                }
            }

            if (!hasinactivitylimiter) {
                HeliumClient.LOGGER.info("detected legacy Minecraft API");
            }
        } catch (Throwable t) {
            HeliumClient.LOGGER.warn("failed to resolve Minecraft API ({})", t.getMessage());
        }
    }

    private static void resolveparticle() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            for (Field f : ParticleEngine.class.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(f.getType()) && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    particlesmapfield = f;
                    HeliumClient.LOGGER.info("detected ParticleEngine particles map field");
                    break;
                }
            }

            try {
                for (Method m : ParticleEngine.class.getDeclaredMethods()) {
                    if (m.getParameterCount() == 1 && m.getReturnType() != void.class
                            && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                        Class<?> retType = m.getReturnType();
                        if (!retType.isPrimitive() && retType != Object.class) {
                            for (Method rm : retType.getDeclaredMethods()) {
                                if (rm.getParameterCount() == 0 && Queue.class.isAssignableFrom(rm.getReturnType())) {
                                    rm.setAccessible(true);
                                    getparticlesfromrenderer = lookup.unreflect(rm);
                                    hasmodernparticlerenderer = true;
                                    HeliumClient.LOGGER.info("detected modern ParticleRenderer API");
                                    break;
                                }
                            }
                            if (hasmodernparticlerenderer) break;
                        }
                    }
                }
            } catch (Throwable ignored) {}

            if (!hasmodernparticlerenderer) {
                HeliumClient.LOGGER.info("detected legacy ParticleEngine particle storage");
            }
        } catch (Throwable t) {
            HeliumClient.LOGGER.warn("failed to resolve ParticleEngine API ({})", t.getMessage());
        }
    }

    public static boolean hasfloatsincos() { init(); return hasfloatsincos; }
    public static boolean hasdoublesincos() { init(); return hasdoublesincos; }
    public static boolean hasblittoscreen() { init(); return hasblittoscreen; }
    public static boolean haslegacydraw() { init(); return haslegacydraw; }
    public static boolean haslegacyfbo() { init(); return haslegacyfbo; }
    public static boolean hastakescreenshot() { init(); return hastakescreenshot; }
    public static boolean hastogglefullscreen() { init(); return hastogglefullscreen; }
    public static boolean haslogglerror() { init(); return haslogglerror; }
    public static boolean haslogonglerror() { init(); return haslogonglerror; }
    public static boolean hasinactivitylimiter() { init(); return hasinactivitylimiter; }
    public static boolean hasmodernparticlerenderer() { init(); return hasmodernparticlerenderer; }

    public static MethodHandle sinfloathandle() { init(); return sinfloathandle; }
    public static MethodHandle cosfloathandle() { init(); return cosfloathandle; }
    public static MethodHandle sindoublehandle() { init(); return sindoublehandle; }
    public static MethodHandle cosdoublehandle() { init(); return cosdoublehandle; }
    public static MethodHandle blittoscreenhandle() { init(); return blittoscreenhandle; }
    public static MethodHandle legacydrawhandle() { init(); return legacydrawhandle; }
    public static MethodHandle getinactivitylimiterhandle() { init(); return getinactivitylimiterhandle; }
    public static MethodHandle getparticlesfromrenderer() { init(); return getparticlesfromrenderer; }

    public static Field fbofield() { init(); return fbofield; }
    public static Field particlesmapfield() { init(); return particlesmapfield; }

    public static void applyinactivefpslimit(Object mcclient, int limit) {
        init();
        if (!hasinactivitylimiter || getinactivitylimiterhandle == null) return;

        try {
            Object limiter = getinactivitylimiterhandle.invoke(mcclient);
            if (limiter != null) {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                try {
                    MethodHandle setlimit = lookup.findVirtual(limiter.getClass(), "setLimit",
                            MethodType.methodType(void.class, int.class));
                    setlimit.invoke(limiter, Math.max(1, limit));
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }
}
