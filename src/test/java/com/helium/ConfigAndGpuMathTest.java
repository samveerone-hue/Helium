package com.helium;

import com.helium.compute.GpuComputeMath;
import com.helium.config.ConfigClamps;
import com.helium.rentities.entities.BatchabilityDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigAndGpuMathTest {
    @Test
    void heliumConfigClampsRejectOutOfRangeValues() {
        assertEquals(16, ConfigClamps.entityCullDistance(-1));
        assertEquals(128, ConfigClamps.entityCullDistance(999));
        assertEquals(16, ConfigClamps.blockEntityCullDistance(-1));
        assertEquals(96, ConfigClamps.blockEntityCullDistance(999));
        assertEquals(8, ConfigClamps.particleCullDistance(-1));
        assertEquals(64, ConfigClamps.particleCullDistance(999));
        assertEquals(100, ConfigClamps.maxParticles(-1));
        assertEquals(5000, ConfigClamps.maxParticles(999999));
        assertEquals(0, ConfigClamps.overlayTransparency(-1));
        assertEquals(100, ConfigClamps.overlayTransparency(999));
        assertEquals(16, ConfigClamps.nativeMemoryPoolMb(-1));
        assertEquals(256, ConfigClamps.nativeMemoryPoolMb(999));
        assertEquals(1, ConfigClamps.chunkScheduleMaxPerTick(-1));
        assertEquals(64, ConfigClamps.chunkScheduleMaxPerTick(999));
        assertEquals(10, ConfigClamps.idleTimeoutSeconds(-1));
        assertEquals(300, ConfigClamps.idleTimeoutSeconds(999));
        assertEquals(1, ConfigClamps.idleFpsLimit(-1));
        assertEquals(30, ConfigClamps.idleFpsLimit(999));
        assertEquals(0, ConfigClamps.fullbrightStrength(-1));
        assertEquals(10, ConfigClamps.fullbrightStrength(999));
        assertEquals(1, ConfigClamps.leafCullingDepth(-1));
        assertEquals(4, ConfigClamps.leafCullingDepth(999));
        assertEquals(0.0f, ConfigClamps.leafCullingRandomRejection(-1.0f));
        assertEquals(1.0f, ConfigClamps.leafCullingRandomRejection(2.0f));
        assertEquals(4.0, ConfigClamps.particleLODDistance(-1.0));
        assertEquals(64.0, ConfigClamps.particleLODDistance(999.0));
        assertEquals(0.0, ConfigClamps.particleLODReduction(-1.0));
        assertEquals(1.0, ConfigClamps.particleLODReduction(2.0));
        assertEquals(32, ConfigClamps.itemFrameLODRange(-1));
        assertEquals(256, ConfigClamps.itemFrameLODRange(999));
    }

    @Test
    void experimentalAndGpuComputeClampsRejectOutOfRangeValues() {
        assertEquals(1, ConfigClamps.experimentalPacketBatchTicks(-1));
        assertEquals(2, ConfigClamps.experimentalPacketBatchTicks(999));
        assertEquals(16, ConfigClamps.experimentalModelCacheMaxMb(-1));
        assertEquals(512, ConfigClamps.experimentalModelCacheMaxMb(999));
        assertEquals(8, ConfigClamps.experimentalAsyncLightMaxPerTick(-1));
        assertEquals(256, ConfigClamps.experimentalAsyncLightMaxPerTick(999));
        assertEquals(16, ConfigClamps.gpuGridSize(-1));
        assertEquals(48, ConfigClamps.gpuGridSize(999));
        assertEquals(1, ConfigClamps.gpuRefreshTicks(-1));
        assertEquals(10, ConfigClamps.gpuRefreshTicks(999));
        assertEquals(1, ConfigClamps.gpuMaxBatch(-1));
        assertEquals(8, ConfigClamps.gpuMaxBatch(999));
    }

    @Test
    void asyncRentitiesDecisionFailsClosedUntilResolved() {
        assertFalse(BatchabilityDecision.allowResolved(false, true));
        assertFalse(BatchabilityDecision.allowResolved(false, false));
        assertFalse(BatchabilityDecision.allowResolved(true, false));
        assertTrue(BatchabilityDecision.allowResolved(true, true));
    }

    @Test
    void gpuBatchGridIncludesRayEndpointsAndRecenters() {
        float[] rays = {
                10.25f, 20.25f, 30.25f, 12.75f, 21.75f, 32.75f,
                11.50f, 20.50f, 31.50f, 13.00f, 22.00f, 33.00f
        };

        GpuComputeMath.Grid grid = GpuComputeMath.computeGrid(rays, 16);

        assertNotNull(grid);
        assertEquals(16, grid.size());
        assertTrue(grid.minX() <= 10 && grid.maxX() >= 13);
        assertTrue(grid.minY() <= 20 && grid.maxY() >= 22);
        assertTrue(grid.minZ() <= 30 && grid.maxZ() >= 33);
        assertEquals(grid.size() - 1, grid.maxX() - grid.minX());
        assertEquals(grid.size() - 1, grid.maxY() - grid.minY());
        assertEquals(grid.size() - 1, grid.maxZ() - grid.minZ());
    }

    @Test
    void gpuBatchGridBailsOutWhenRequiredSizeExceeds64() {
        float[] rays = {0, 0, 0, 63.1f, 0, 0};
        assertNull(GpuComputeMath.computeGrid(rays, 16));
    }

    @Test
    void gpuBatchGridRejectsMalformedInput() {
        assertNull(GpuComputeMath.computeGrid(null, 32));
        assertNull(GpuComputeMath.computeGrid(new float[5], 32));
    }
}
