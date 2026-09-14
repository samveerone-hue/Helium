package com.helium.math;

import net.minecraft.world.level.levelgen.MarsagliaPolarGaussian;
import net.minecraft.util.RandomSource;

/** Standard 128-layer Ziggurat normal generator. */
public class TableGaussianGenerator extends MarsagliaPolarGaussian {

    private static final int TABLE_SIZE = 128;
    private static final double R = 3.442619855899D;
    private static final double AREA = 9.91256303526217E-3D;
    private static final double INV_R = 1.0D / R;

    private static final double[] X = new double[TABLE_SIZE + 1];
    private static final double[] F = new double[TABLE_SIZE + 1];

    static {
        double tailHeight = Math.exp(-0.5D * R * R);
        X[0] = AREA / tailHeight;
        X[1] = R;
        F[0] = 1.0D;
        F[1] = tailHeight;

        double dn = R;
        for (int i = 2; i < TABLE_SIZE; i++) {
            dn = Math.sqrt(-2.0D * Math.log(
                    AREA / dn + Math.exp(-0.5D * dn * dn)));
            X[i] = dn;
            F[i] = Math.exp(-0.5D * dn * dn);
        }

        X[TABLE_SIZE] = 0.0D;
        F[TABLE_SIZE] = 1.0D;
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
            double x = this.randomSource.nextDouble() * X[i];

            if (x < X[i + 1]) {
                return this.randomSource.nextBoolean() ? x : -x;
            }

            if (i == 0) {
                double xx;
                double yy;
                do {
                    xx = -Math.log(this.randomSource.nextDouble()) * INV_R;
                    yy = -Math.log(this.randomSource.nextDouble());
                } while (yy + yy < xx * xx);
                double tail = R + xx;
                return this.randomSource.nextBoolean() ? tail : -tail;
            }

            if (F[i + 1] + (F[i] - F[i + 1]) * this.randomSource.nextDouble()
                    < Math.exp(-0.5D * x * x)) {
                return this.randomSource.nextBoolean() ? x : -x;
            }
        }
    }
}
