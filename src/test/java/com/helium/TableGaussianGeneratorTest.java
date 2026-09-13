package com.helium;

import com.helium.math.TableGaussianGenerator;
import net.minecraft.util.math.random.LocalRandom;
import net.minecraft.util.math.random.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableGaussianGeneratorTest {
    private static final int SAMPLES = 100_000;
    private static final double MEAN_TOLERANCE = 0.03;
    private static final double VARIANCE_TOLERANCE = 0.08;

    @Test
    @Timeout(5)
    void zigguratMatchesVanillaGaussianDistribution() {
        Stats table = sampleTableGaussian(0x51A7E);
        Stats vanilla = sampleVanillaGaussian(0x51A7E);

        // Both generators must independently remain consistent with the same
        // standard-normal distribution. Their PRNG consumption differs, so
        // comparing sample-by-sample or requiring matching sample variances
        // would make the regression test spuriously seed-dependent.
        assertTrue(Math.abs(table.mean()) < MEAN_TOLERANCE, "table mean drift: " + table.mean());
        assertTrue(Math.abs(vanilla.mean()) < MEAN_TOLERANCE, "vanilla mean drift: " + vanilla.mean());
        assertTrue(Math.abs(table.variance() - 1.0) < VARIANCE_TOLERANCE, "table variance drift: " + table.variance());
        assertTrue(Math.abs(vanilla.variance() - 1.0) < VARIANCE_TOLERANCE, "vanilla variance drift: " + vanilla.variance());
        assertEquals(SAMPLES, table.count());
        assertEquals(SAMPLES, vanilla.count());
    }

    private static Stats sampleTableGaussian(long seed) {
        Random random = new LocalRandom(seed);
        TableGaussianGenerator generator = new TableGaussianGenerator(random);
        Stats stats = new Stats();
        for (int i = 0; i < SAMPLES; i++) stats.add(generator.next());
        return stats;
    }

    private static Stats sampleVanillaGaussian(long seed) {
        Random random = new LocalRandom(seed);
        Stats stats = new Stats();
        for (int i = 0; i < SAMPLES; i++) stats.add(random.nextGaussian());
        return stats;
    }

    private static final class Stats {
        private int count;
        private double mean;
        private double m2;

        void add(double value) {
            assertTrue(Double.isFinite(value), "non-finite Gaussian sample");
            count++;
            double delta = value - mean;
            mean += delta / count;
            double delta2 = value - mean;
            m2 += delta * delta2;
        }

        int count() { return count; }
        double mean() { return mean; }
        double variance() { return m2 / (count - 1); }
    }
}
