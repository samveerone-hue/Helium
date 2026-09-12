package com.helium.math;

import com.helium.HeliumClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Optional Java Vector API backend used by Helium's batch math helpers. */
public final class SimdMath {
    private static volatile boolean initialized;
    private static volatile boolean vectorApiAvailable;

    private static Class<?> speciesClass;
    private static Class<?> vectorClass;
    private static Object species;
    private static Method speciesLength;
    private static Method fromArray;
    private static Method mul;
    private static Method intoArray;
    private static Method reduceLanes;
    private static Object addOperator;

    private SimdMath() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            vectorClass = Class.forName("jdk.incubator.vector.FloatVector");
            speciesClass = Class.forName("jdk.incubator.vector.VectorSpecies");
            Class<?> operatorsClass = Class.forName("jdk.incubator.vector.VectorOperators");
            Field speciesField = vectorClass.getField("SPECIES_PREFERRED");
            species = speciesField.get(null);
            speciesLength = speciesClass.getMethod("length");
            fromArray = vectorClass.getMethod("fromArray", speciesClass, float[].class, int.class);
            mul = vectorClass.getMethod("mul", vectorClass);
            intoArray = vectorClass.getMethod("intoArray", float[].class, int.class);
            reduceLanes = vectorClass.getMethod("reduceLanes", Class.forName("jdk.incubator.vector.VectorOperators$Associative"));
            addOperator = operatorsClass.getField("ADD").get(null);
            vectorApiAvailable = true;
            HeliumClient.LOGGER.info("simd math initialized - Vector API batch backend available");
        } catch (Throwable t) {
            vectorApiAvailable = false;
            HeliumClient.LOGGER.info("simd math initialized - Vector API unavailable, scalar fallback active");
        }
    }

    public static boolean isInitialized() { return initialized; }
    public static boolean isVectorApiAvailable() { return vectorApiAvailable; }

    public static void batchTransformPositions(float[] positions, float offsetX, float offsetY, float offsetZ, int count) {
        int limit = Math.min(Math.max(0, count * 3), positions.length);
        for (int i = 0; i < limit; i += 3) {
            positions[i] += offsetX;
            positions[i + 1] += offsetY;
            positions[i + 2] += offsetZ;
        }
    }

    public static void batchNormalize(float[] vectors, int count) {
        int limit = Math.min(Math.max(0, count * 3), vectors.length);
        for (int i = 0; i < limit; i += 3) {
            float x = vectors[i];
            float y = vectors[i + 1];
            float z = vectors[i + 2];
            float lenSq = x * x + y * y + z * z;
            if (lenSq > 1e-8f) {
                float invLen = FastMath.isInitialized() ? (float) FastMath.inverseSqrt(lenSq) : (float) (1.0 / Math.sqrt(lenSq));
                vectors[i] = x * invLen;
                vectors[i + 1] = y * invLen;
                vectors[i + 2] = z * invLen;
            }
        }
    }

    public static void batchMultiply(float[] a, float[] b, float[] result, int count) {
        int limit = Math.min(Math.max(0, count), Math.min(a.length, Math.min(b.length, result.length)));
        if (!vectorApiAvailable || limit < vectorLength() * 2) {
            scalarMultiply(a, b, result, limit);
            return;
        }
        try {
            int width = vectorLength();
            int i = 0;
            for (; i + width <= limit; i += width) {
                Object va = fromArray.invoke(null, species, a, i);
                Object vb = fromArray.invoke(null, species, b, i);
                Object vr = mul.invoke(va, vb);
                intoArray.invoke(vr, result, i);
            }
            scalarMultiplyOffset(a, b, result, i, limit);
        } catch (Throwable t) {
            scalarMultiply(a, b, result, limit);
        }
    }

    public static float batchDot(float[] a, float[] b, int count) {
        int limit = Math.min(Math.max(0, count), Math.min(a.length, b.length));
        if (!vectorApiAvailable || limit < vectorLength() * 2) return scalarDot(a, b, limit);
        try {
            int width = vectorLength();
            float sum = 0f;
            int i = 0;
            for (; i + width <= limit; i += width) {
                Object va = fromArray.invoke(null, species, a, i);
                Object vb = fromArray.invoke(null, species, b, i);
                Object vr = mul.invoke(va, vb);
                sum += ((Number) reduceLanes.invoke(vr, addOperator)).floatValue();
            }
            return sum + scalarDotOffset(a, b, i, limit);
        } catch (Throwable t) {
            return scalarDot(a, b, limit);
        }
    }

    private static int vectorLength() {
        if (!vectorApiAvailable) return 0;
        try { return ((Number) speciesLength.invoke(species)).intValue(); }
        catch (Throwable t) { return 0; }
    }

    private static void scalarMultiply(float[] a, float[] b, float[] result, int limit) {
        scalarMultiplyOffset(a, b, result, 0, limit);
    }

    private static void scalarMultiplyOffset(float[] a, float[] b, float[] result, int from, int limit) {
        for (int i = from; i < limit; i++) result[i] = a[i] * b[i];
    }

    private static float scalarDot(float[] a, float[] b, int limit) {
        return scalarDotOffset(a, b, 0, limit);
    }

    private static float scalarDotOffset(float[] a, float[] b, int from, int limit) {
        float sum = 0f;
        for (int i = from; i < limit; i++) sum += a[i] * b[i];
        return sum;
    }
}
