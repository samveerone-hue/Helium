package com.helium.math;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.MarsagliaPolarGaussian;
import net.minecraft.util.RandomSource;

public class TableGaussianGenerator extends MarsagliaPolarGaussian {

    private static final int TABLE_SIZE = 256;
    private static final float R = 3.442619855899F;
    private static final float INV_R = 0.29047645161474317F;

    private static final float[] X = new float[TABLE_SIZE + 1];
    private static final float[] Y = new float[TABLE_SIZE + 1];

    static {
        float f = GBFMath.fastexp(-0.5F * R * R);
        X[0] = R / f;
        X[1] = R;
        Y[0] = 0.0F;
        Y[1] = f;

        for (int i = 2; i <= TABLE_SIZE; i++) {
            X[i] = Mth.sqrt(-2.0F * GBFMath.fastlog(GBFMath.fastexp(-0.5F * X[i - 1] * X[i - 1]) + Y[i - 1] / X[i - 1]));
            Y[i] = GBFMath.fastexp(-0.5F * X[i] * X[i]);
        }
    }

    public TableGaussianGenerator(RandomSource rand) {
        super(rand);
    }

    @Override
    public void reset() {}

    @Override
    public double nextGaussian() {
        while (true) {
            int i = this.randomSource.nextInt(TABLE_SIZE);
            long j = this.randomSource.nextInt() & 0xFFFFFFFFL;

            double xVal = j * (X[i] / 4294967296.0);

            if (Math.abs(xVal) < X[i + 1]) {
                return xVal;
            }

            if (i == 0) {
                double xx, yy;
                do {
                    xx = -GBFMath.fastlog((float) this.randomSource.nextDouble()) * INV_R;
                    yy = -GBFMath.fastlog((float) this.randomSource.nextDouble());
                } while (yy + yy < xx * xx);
                return (this.randomSource.nextBoolean() ? R + xx : -R - xx);
            }

            if (Y[i + 1] + (Y[i] - Y[i + 1]) * this.randomSource.nextDouble() < GBFMath.fastexp((float) (-0.5 * xVal * xVal))) {
                return this.randomSource.nextBoolean() ? xVal : -xVal;
            }
        }
    }
}
