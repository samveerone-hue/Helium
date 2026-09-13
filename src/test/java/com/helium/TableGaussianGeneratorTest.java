package com.helium;

import com.helium.math.TableGaussianGenerator;
import net.minecraft.util.math.random.LocalRandom;
import net.minecraft.util.math.random.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableGaussianGeneratorTest {
    private static final long[] SEEDS = {
            0x51A7EL, 0x12345678L, 0xCAFEBABEL, 0xDEADBEEFL, 0x13579BDFL
    };
    private static final int SAMPLES_PER_SEED = 20_000;
    private static final int SAMPLES = SEEDS.length * SAMPLES_PER_SEED;
    private static final double MEAN_TOLERANCE = 0.025;
    private static final double VARIANCE_TOLERANCE = 0.06;
    private static final double CROSS_MEAN_TOLERANCE = 0.05;
    private static final double CROSS_VARIANCE_TOLERANCE = 0.12;

    @Test
    @Timeout(10)
    void zigguratMatchesVanillaGaussianDistribution() {
        Stats table = new Stats();
        Stats vanilla = new Stats();

        for (long seed : SEEDS) {
            sampleTableGaussian(seed, table);
            sampleVanillaGaussian(seed, vanilla);
        }

        // Aggregate independent seeds so the regression is not coupled to one
        // deterministic PRNG trajectory. Both implementations must stay close
        // to N(0,1), and their aggregate statistics should remain reasonably close.
        assertTrue(Math.abs(table.mean()) < MEAN_TOLERANCE, "table mean drift: " + table.mean());
        assertTrue(Math.abs(vanilla.mean()) < MEAN_TOLERANCE, "vanilla mean drift: " + vanilla.mean());
        assertTrue(Math.abs(table.variance() - 1.0) < VARIANCE_TOLERANCE, "table variance drift: " + table.variance());
        assertTrue(Math.abs(vanilla.variance() - 1.0) < VARIANCE_TOLERANCE, "vanilla variance drift: " + vanilla.variance());
        assertTrue(Math.abs(table.mean() - vanilla.mean()) < CROSS_MEAN_TOLERANCE,
                "mean mismatch: table=" + table.mean() + " vanilla=" + vanilla.mean());
        assertTrue(Math.abs(table.variance() - vanilla.variance()) < CROSS_VARIANCE_TOLERANCE,
                "variance mismatch: table=" + table.variance() + " vanilla=" + vanilla.variance());
        assertEquals(SAMPLES, table.count());
        assertEquals(SAMPLES, vanilla.count());
    }

    private static void sampleTableGaussian(long seed, Stats stats) {
        Random random = new LocalRandom(seed);
        TableGaussianGenerator generator = new TableGaussianGenerator(random);
        for (int i = 0; i < SAMPLES_PER_SEED; i++) stats.add(generator.next());
    }

    private static void sampleVanillaGaussian(long seed, Stats stats) {
        Random random = new LocalRandom(seed);
        for (int i = 0; i < SAMPLES_PER_SEED; i++) stats.add(random.nextGaussian());
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
