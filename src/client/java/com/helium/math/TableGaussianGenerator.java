package com.helium.math;

import net.minecraft.util.math.random.GaussianGenerator;
import net.minecraft.util.math.random.Random;

/**
 * Ziggurat Gaussian generator.
 *
 * Uses the standard 128-layer construction with an exact recurrence so the table
 * remains finite and samples a standard normal distribution. The fast-random config
 * remains fail-closed until the full path is runtime-validated.
 */
public class TableGaussianGenerator extends GaussianGenerator {

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

    public TableGaussianGenerator(Random rand) {
        super(rand);
    }

    @Override
    public void reset() {}

    @Override
    public double next() {
        while (true) {
            int i = this.baseRandom.nextInt(TABLE_SIZE);
            double x = this.baseRandom.nextDouble() * X[i];

            if (x < X[i + 1]) {
                return this.baseRandom.nextBoolean() ? x : -x;
            }

            if (i == 0) {
                double xx;
                double yy;
                do {
                    xx = -Math.log(this.baseRandom.nextDouble()) * INV_R;
                    yy = -Math.log(this.baseRandom.nextDouble());
                } while (yy + yy < xx * xx);

                double tail = R + xx;
                return this.baseRandom.nextBoolean() ? tail : -tail;
            }

            if (F[i + 1] + (F[i] - F[i + 1]) * this.baseRandom.nextDouble()
                    < Math.exp(-0.5D * x * x)) {
                return this.baseRandom.nextBoolean() ? x : -x;
            }
        }
    }
}
