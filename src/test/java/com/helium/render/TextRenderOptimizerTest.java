package com.helium.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TextRenderOptimizerTest {
    @Test
    void cacheEvictsOldEntriesWithoutFlushingRecentGlyphs() {
        TextRenderOptimizer.invalidate();

        Object oldest = new Object();
        Object recent = new Object();
        TextRenderOptimizer.cache(1L, oldest);
        TextRenderOptimizer.cache(2L, recent);
        for (long key = 3L; key <= 1025L; key++) {
            TextRenderOptimizer.cache(key, new Object());
        }

        assertNull(TextRenderOptimizer.getcached(1L));
        assertSame(recent, TextRenderOptimizer.getcached(2L));
        assertNotNull(TextRenderOptimizer.getcached(1025L));

        TextRenderOptimizer.invalidate();
    }
}