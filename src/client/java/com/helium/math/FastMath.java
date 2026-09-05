package com.helium.math;

import com.helium.compat.ExternalModCompat;

public final class FastMath {

    private static float[] sinTable;
    private static float[] cosTable;
    private static int precision;
    private static double precisionFactor;
    private static boolean initialized = false;

    private FastMath() {}

    public static void init() {
        if (!ExternalModCompat.shouldUseHeliumFastMath()) return;
        init(65536);
    }

    public static void init(int lutSize) {
        if (!ExternalModCompat.shouldUseHeliumFastMath()) return;
        if (lutSize < 1024 || (lutSize & (lutSize - 1)) != 0) {
            throw new IllegalArgumentException("lutSize must be a power of two >= 1024");
        }
        precision = lutSize;
        precisionFactor = lutSize / (Math.PI * 2.0);
        sinTable = new float[lutSize];
        cosTable = new float[lutSize];

        for (int i = 0; i < lutSize; i++) {
            double angle = (double) i * Math.PI * 2.0 / lutSize;
            sinTable[i] = (float) Math.sin(angle);
            cosTable[i] = (float) Math.cos(angle);
        }

        initialized = true;
    }

    public static float sin(double radians) {
        if (!initialized) return (float) Math.sin(radians);
        int index = angleIndex(radians);
        return sinTable[index];
    }

    public static float cos(double radians) {
        if (!initialized) return (float) Math.cos(radians);
        int index = angleIndex(radians);
        return cosTable[index];
    }

    private static int angleIndex(double radians) {
        int index = (int) (radians * precisionFactor) & (precision - 1);
        return index;
    }

    // Based on Oxidizium's parity-tested approximation. This stays allocation-free and avoids Math.atan2.
    public static double atan2(double y, double x) {
        double absX = Math.abs(x);
        double absY = Math.abs(y);
        if (absX < 1.0e-8 && absY < 1.0e-8) return 0.0;

        double max = Math.max(absX, absY);
        double min = Math.min(absX, absY);
        double a = min / max;
        double s = a * a;
        double r = ((-0.0464964749 * s + 0.15931422) * s - 0.327622764) * s * a + a;

        if (absY > absX) r = Math.PI * 0.5 - r;
        if (x < 0.0) r = Math.PI - r;
        if (y < 0.0) r = -r;
        return r;
    }

    // Based on Oxidizium's double-precision fast inverse square root.
    public static double inverseSqrt(double x) {
        if (x <= 0.0) return x == 0.0 ? Double.POSITIVE_INFINITY : Double.NaN;

        double halfX = 0.5 * x;
        long i = Double.doubleToRawLongBits(x);
        i = 0x5FE6EB50C7B537A9L - (i >> 1);
        x = Double.longBitsToDouble(i);
        x *= (1.5 - halfX * x * x);
        x *= (1.5 - halfX * x * x);
        return x;
    }

    // Integer helpers mirror Oxidizium's branch-light implementations.
    public static int min(int a, int b) {
        return a <= b ? a : b;
    }

    public static int max(int a, int b) {
        return a >= b ? a : b;
    }

    public static int abs(int a) {
        int mask = a >> 31;
        return (a ^ mask) - mask;
    }

    public static int floor(float value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    public static int floor(double value) {
        long i = (long) value;
        if (i < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (i > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        int result = (int) i;
        return value < result ? result - 1 : result;
    }

    public static long lfloor(double value) {
        long i = (long) value;
        return value < i ? i - 1 : i;
    }

    public static int ceil(float value) {
        int i = (int) value;
        return value > i ? i + 1 : i;
    }

    public static int ceil(double value) {
        long i = (long) value;
        if (i < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (i > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        int result = (int) i;
        return value > result ? result + 1 : result;
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static long clamp(long value, long min, long max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static int absMax(int a, int b) {
        return Math.max(Math.abs(a), Math.abs(b));
    }

    public static float absMax(float a, float b) {
        return Math.max(Math.abs(a), Math.abs(b));
    }

    public static double absMax(double a, double b) {
        return Math.max(Math.abs(a), Math.abs(b));
    }

    public static int floorDiv(int dividend, int divisor) {
        int result = dividend / divisor;
        if ((dividend ^ divisor) < 0 && dividend % divisor != 0) result--;
        return result;
    }

    public static boolean approxEqual(float a, float b) {
        return Math.abs(b - a) <= 1.0e-5F;
    }

    public static boolean approxEqual(double a, double b) {
        return Math.abs(b - a) <= 1.0e-5D;
    }

    public static int positiveModulo(int dividend, int divisor) {
        int mod = dividend % divisor;
        return mod < 0 ? mod + Math.abs(divisor) : mod;
    }

    public static float positiveModulo(float dividend, float divisor) {
        return dividend - floor(dividend / divisor) * divisor;
    }

    public static double positiveModulo(double dividend, double divisor) {
        return dividend - Math.floor(dividend / divisor) * divisor;
    }

    public static boolean isMultipleOf(int a, int b) {
        return b != 0 && a % b == 0;
    }

    public static int wrapDegrees(int degrees) {
        int wrapped = degrees % 360;
        if (wrapped >= 180) wrapped -= 360;
        if (wrapped < -180) wrapped += 360;
        return wrapped;
    }

    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    public static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0D;
        if (wrapped >= 180.0D) wrapped -= 360.0D;
        if (wrapped < -180.0D) wrapped += 360.0D;
        return wrapped;
    }

    public static float degreesDifference(float start, float end) {
        return wrapDegrees(end - start);
    }

    public static float degreesDifferenceAbs(float first, float second) {
        return Math.abs(degreesDifference(first, second));
    }

    public static float rotateIfNecessary(float value, float mean, float delta) {
        float diff = degreesDifference(mean, value);
        return mean + clamp(diff, -delta, delta);
    }

    public static float approach(float from, float to, float step) {
        if (from < to) return Math.min(from + step, to);
        return Math.max(from - step, to);
    }

    public static float approachDegrees(float from, float to, float step) {
        float diff = degreesDifference(from, to);
        if (diff > step) diff = step;
        if (diff < -step) diff = -step;
        return from + diff;
    }

    public static int smallestEncompassingPowerOfTwo(int value) {
        return value <= 1 ? 1 : Integer.highestOneBit(value - 1) << 1;
    }

    public static boolean isPowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }

    public static int ceilLog2(int value) {
        if (value <= 1) return 0;
        return 32 - Integer.numberOfLeadingZeros(value - 1);
    }

    public static int floorLog2(int value) {
        if (value <= 0) return 0;
        return 31 - Integer.numberOfLeadingZeros(value);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void batchSin(float[] input, float[] output) {
        if (!initialized || input == null || output == null) return;
        int len = Math.min(input.length, output.length);
        for (int i = 0; i < len; i++) output[i] = sin(input[i]);
    }

    public static void batchCos(float[] input, float[] output) {
        if (!initialized || input == null || output == null) return;
        int len = Math.min(input.length, output.length);
        for (int i = 0; i < len; i++) output[i] = cos(input[i]);
    }

    public static void batchSinCos(float[] angles, float[] sinOut, float[] cosOut) {
        if (!initialized || angles == null || sinOut == null || cosOut == null) return;
        int len = Math.min(angles.length, Math.min(sinOut.length, cosOut.length));
        for (int i = 0; i < len; i++) {
            int index = angleIndex(angles[i]);
            sinOut[i] = sinTable[index];
            cosOut[i] = cosTable[index];
        }
    }

    public static void batchTransformPositions(float[] positions, float[] matrix4x4, float[] output) {
        if (positions == null || matrix4x4 == null || output == null) return;
        if (matrix4x4.length < 16) return;

        int vertexCount = positions.length / 3;
        int maxVertices = Math.min(vertexCount, output.length / 3);
        for (int i = 0; i < maxVertices; i++) {
            int idx = i * 3;
            float x = positions[idx];
            float y = positions[idx + 1];
            float z = positions[idx + 2];

            output[idx] = matrix4x4[0] * x + matrix4x4[4] * y + matrix4x4[8] * z + matrix4x4[12];
            output[idx + 1] = matrix4x4[1] * x + matrix4x4[5] * y + matrix4x4[9] * z + matrix4x4[13];
            output[idx + 2] = matrix4x4[2] * x + matrix4x4[6] * y + matrix4x4[10] * z + matrix4x4[14];
        }
    }

    public static void batchNormalize(float[] vectors, float[] output) {
        if (vectors == null || output == null) return;
        int vectorCount = Math.min(vectors.length, output.length) / 3;

        for (int i = 0; i < vectorCount; i++) {
            int idx = i * 3;
            float x = vectors[idx];
            float y = vectors[idx + 1];
            float z = vectors[idx + 2];

            float invLen = (float) inverseSqrt(x * x + y * y + z * z);
            output[idx] = x * invLen;
            output[idx + 1] = y * invLen;
            output[idx + 2] = z * invLen;
        }
    }

    public static void batchDot(float[] a, float[] b, float[] output) {
        if (a == null || b == null || output == null) return;
        int vectorCount = Math.min(a.length, b.length) / 3;
        vectorCount = Math.min(vectorCount, output.length);

        for (int i = 0; i < vectorCount; i++) {
            int idx = i * 3;
            output[i] = a[idx] * b[idx] + a[idx + 1] * b[idx + 1] + a[idx + 2] * b[idx + 2];
        }
    }

    public static void batchLerp(float[] a, float[] b, float t, float[] output) {
        if (a == null || b == null || output == null) return;
        int len = Math.min(a.length, Math.min(b.length, output.length));
        float oneMinusT = 1.0f - t;

        for (int i = 0; i < len; i++) output[i] = a[i] * oneMinusT + b[i] * t;
    }
}
