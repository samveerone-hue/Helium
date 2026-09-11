package com.helium.rentities.entities;

import net.minecraft.entity.Entity;

/**
 * Optional batching prefilter. The operation is intentionally synchronous: it only performs
 * a squared-distance comparison on coordinates already available on the render thread.
 * Unknown/disabled state fails open.
 */
public final class AsyncVisibilityManager {
    private long frame;

    public void beginFrame(long frame) {
        this.frame = frame;
    }

    public boolean shouldBatch(Entity entity, double cameraX, double cameraY, double cameraZ,
                               boolean enabled, int refreshFrames, int maxAgeFrames, double maxDistance) {
        if (!enabled || entity == null || maxDistance <= 0.0D) return true;

        // Keep the old cadence controls as cheap frame gates, but do not create a task/future
        // for a calculation that is only a few floating-point operations.
        if (refreshFrames > 1 && (frame % refreshFrames) != 0L) return true;

        double dx = entity.getX() - cameraX;
        double dy = entity.getY() - cameraY;
        double dz = entity.getZ() - cameraZ;
        return dx * dx + dy * dy + dz * dz <= maxDistance * maxDistance;
    }

    public void shutdown() {
        // No background resources.
    }
}
