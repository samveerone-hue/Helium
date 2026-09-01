package com.helium.util;

import com.helium.HeliumClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.MathHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Queue;

/** Small compatibility resolver for optional legacy Helium code paths. */
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

    private static Method findMethod(Class<?> owner, String intermediaryName, String namedName, Class<?>... parameterTypes)
            throws ReflectiveOperationException {
        String[] candidates = {intermediaryName, namedName};
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) continue;
            try {
                Method method = owner.getDeclaredMethod(candidate, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Try the public/inherited view below.
            }
            try {
                Method method = owner.getMethod(candidate, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Try the next mapping name.
            }
        }
        throw new NoSuchMethodException(owner.getName() + "#" + intermediaryName + "/" + namedName);
    }

    private static MethodHandle staticHandle(Class<?> owner, String intermediaryName, String namedName, Class<?>... parameterTypes)
            throws ReflectiveOperationException {
        return MethodHandles.lookup().unreflect(findMethod(owner, intermediaryName, namedName, parameterTypes));
    }

    private static MethodHandle virtualHandle(Class<?> owner, String intermediaryName, String namedName, Class<?>... parameterTypes)
            throws ReflectiveOperationException {
        return MethodHandles.lookup().unreflect(findMethod(owner, intermediaryName, namedName, parameterTypes));
    }

    private static void resolvemath() {
        try {
            MethodHandle vanillaSin = staticHandle(MathHelper.class, "method_15374", "sin", double.class);
            MethodHandle vanillaCos = staticHandle(MathHelper.class, "method_15362", "cos", double.class);

            sindoublehandle = vanillaSin;
            cosdoublehandle = vanillaCos;
            sinfloathandle = vanillaSin.asType(MethodType.methodType(float.class, float.class));
            cosfloathandle = vanillaCos.asType(MethodType.methodType(float.class, float.class));
            hasfloatsincos = true;
            hasdoublesincos = true;
            HeliumClient.LOGGER.info("resolved 1.21.11 MathHelper sin/cos");
        } catch (Throwable t) {
            hasfloatsincos = false;
            hasdoublesincos = false;
            sinfloathandle = null;
            cosfloathandle = null;
            sindoublehandle = null;
            cosdoublehandle = null;
            HeliumClient.LOGGER.warn("failed to resolve MathHelper sin/cos ({})", t.getMessage());
        }
    }

    private static void resolveframebuffer() {
        try {
            blittoscreenhandle = virtualHandle(Framebuffer.class, "method_1237", "blitToScreen");
            hasblittoscreen = true;
            legacydrawhandle = null;
            fbofield = null;
            haslegacydraw = false;
            haslegacyfbo = false;
            HeliumClient.LOGGER.info("resolved 1.21.11 Framebuffer blit method");
        } catch (Throwable t) {
            hasblittoscreen = false;
            blittoscreenhandle = null;
            HeliumClient.LOGGER.warn("failed to resolve 1.21.11 Framebuffer API ({})", t.getMessage());
        }
    }

    private static void resolvescreenshot() {
        try {
            for (Method m : ScreenshotRecorder.class.getDeclaredMethods()) {
                if (m.getParameterCount() == 2 && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params[0] == Framebuffer.class && params[1] == java.util.function.Consumer.class) {
                        hastakescreenshot = true;
                        HeliumClient.LOGGER.info("detected modern ScreenshotRecorder API (takeScreenshot)");
                        break;
                    }
                }
            }
            if (!hastakescreenshot) HeliumClient.LOGGER.info("detected legacy ScreenshotRecorder API");
        } catch (Throwable t) {
            HeliumClient.LOGGER.warn("failed to resolve ScreenshotRecorder API ({})", t.getMessage());
        }
    }

    private static void resolvewindow() {
        hastogglefullscreen = true;
        haslogglerror = true;
        haslogonglerror = true;
    }

    private static void resolveminecraftclient() {
        try {
            getinactivitylimiterhandle = virtualHandle(
                    MinecraftClient.class,
                    "method_61964",
                    "getInactivityFpsLimiter");
            hasinactivitylimiter = true;
            HeliumClient.LOGGER.info("resolved 1.21.11 MinecraftClient inactivity FPS limiter");
        } catch (Throwable t) {
            hasinactivitylimiter = false;
            getinactivitylimiterhandle = null;
            HeliumClient.LOGGER.warn("failed to resolve 1.21.11 MinecraftClient API ({})", t.getMessage());
        }
    }

    private static void resolveparticle() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            for (Field f : ParticleManager.class.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(f.getType()) && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    particlesmapfield = f;
                    HeliumClient.LOGGER.info("detected ParticleManager particles map field");
                    break;
                }
            }
            try {
                for (Method m : ParticleManager.class.getDeclaredMethods()) {
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
                HeliumClient.LOGGER.info("detected legacy ParticleManager particle storage");
            }
        } catch (Throwable t) {
            HeliumClient.LOGGER.warn("failed to resolve ParticleManager API ({})", t.getMessage());
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
                MethodHandle setlimit = virtualHandle(
                        limiter.getClass(), "method_61938", "setMaxFps", int.class);
                setlimit.invoke(limiter, Math.max(1, limit));
            }
        } catch (Throwable ignored) {}
    }
}
