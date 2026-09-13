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

    @Test
    @Timeout(5)
    void zigguratMatchesVanillaGaussianDistribution() {
        Stats table = sampleTableGaussian(0x51A7E); 
        Stats vanilla = sampleVanillaGaussian(0x51A7E);

        assertTrue(Math.abs(table.mean()) < 0.02, "table mean drift: " + table.mean());
        assertTrue(Math.abs(vanilla.mean()) < 0.02, "vanilla mean drift: " + vanilla.mean());
        assertTrue(Math.abs(table.variance() - 1.0) < 0.04, "table variance drift: " + table.variance());
        assertTrue(Math.abs(vanilla.variance() - 1.0) < 0.04, "vanilla variance drift: " + vanilla.variance());
        assertTrue(Math.abs(table.variance() - vanilla.variance()) < 0.04,
                "variance mismatch: table=" + table.variance() + " vanilla=" + vanilla.variance());
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
