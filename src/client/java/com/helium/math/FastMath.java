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
        if (!ExternalModCompat.shouldUseHeliumFastMath()) {
            return;
        }
        init(65536);
    }

    public static void init(int lutSize) {
        if (!ExternalModCompat.shouldUseHeliumFastMath()) {
            return;
        }
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
        int index = angleIndex(radians);
        return sinTable[index];
    }

    public static float cos(double radians) {
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
        long bits = Double.doubleToRawLongBits(x);
        bits = 0x5FE6EB50C7B537A9L - (bits >> 1);
        x = Double.longBitsToDouble(bits);
        x *= 1.5 - halfX * x * x;
        x *= 1.5 - halfX * x * x;
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
        return Math.max(min, Math.min(max, value));
    }

    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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

    public static int floorDiv(int a, int b) {
        return Math.floorDiv(a, b);
    }

    public static boolean approximatelyEquals(float a, float b) {
        return Math.abs(b - a) < 1.0E-5F;
    }

    public static boolean approximatelyEquals(double a, double b) {
        return Math.abs(b - a) < 1.0E-5D;
    }

    public static int positiveModulo(int value, int divisor) {
        int result = value % divisor;
        if (result < 0) result += Math.abs(divisor);
        return result;
    }

    public static float positiveModulo(float value, float divisor) {
        float result = value % divisor;
        if (result < 0.0F) result += Math.abs(divisor);
        return result;
    }

    public static double positiveModulo(double value, double divisor) {
        double result = value % divisor;
        if (result < 0.0D) result += Math.abs(divisor);
        return result;
    }

    public static boolean isMultipleOf(int value, int divisor) {
        return divisor != 0 && value % divisor == 0;
    }

    public static int wrapDegrees(int degrees) {
        return wrapDegrees(degrees, 180);
    }

    public static int wrapDegrees(int degrees, int limit) {
        int range = limit * 2;
        int result = degrees % range;
        if (result >= limit) result -= range;
        if (result < -limit) result += range;
        return result;
    }

    public static float wrapDegrees(float degrees) {
        degrees %= 360.0F;
        if (degrees >= 180.0F) degrees -= 360.0F;
        if (degrees < -180.0F) degrees += 360.0F;
        return degrees;
    }

    public static double wrapDegrees(double degrees) {
        degrees %= 360.0D;
        if (degrees >= 180.0D) degrees -= 360.0D;
        if (degrees < -180.0D) degrees += 360.0D;
        return degrees;
    }

    public static float degreesDifference(float from, float to) {
        return wrapDegrees(to - from);
    }

    public static float degreesDifferenceAbs(float from, float to) {
        return Math.abs(degreesDifference(from, to));
    }

    public static float rotateIfNecessary(float current, float target, float maxDelta) {
        float delta = wrapDegrees(target - current);
        if (delta > maxDelta) delta = maxDelta;
        if (delta < -maxDelta) delta = -maxDelta;
        return current + delta;
    }

    public static float approach(float current, float target, float delta) {
        if (current < target) return Math.min(current + delta, target);
        return Math.max(current - delta, target);
    }

    public static float approachDegrees(float current, float target, float delta) {
        return current + clamp(wrapDegrees(target - current), -delta, delta);
    }

    public static int smallestEncompassingPowerOfTwo(int value) {
        int result = 1;
        while (result < value) result <<= 1;
        return result;
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
}
