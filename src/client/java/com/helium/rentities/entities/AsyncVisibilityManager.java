package com.helium.rentities.entities;

import net.minecraft.entity.Entity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Optional batching prefilter. The calculation is intentionally synchronous because it is only
 * a squared-distance comparison on coordinates already available on the render thread. The
 * "async" part of the original feature name refers to reusing the decision between refreshes.
 */
public final class AsyncVisibilityManager {
    private static final class Decision {
        long frame;
        boolean visible;
    }

    private final Map<Entity, Decision> decisions = new WeakHashMap<>();
    private long frame;

    public void beginFrame(long frame) {
        this.frame = frame;
        if ((frame & 63L) == 0L) {
            decisions.entrySet().removeIf(entry -> frame - entry.getValue().frame > 120L);
        }
    }

    public boolean shouldBatch(Entity entity, double cameraX, double cameraY, double cameraZ,
                               boolean enabled, int refreshFrames, int maxAgeFrames, double maxDistance) {
        if (!enabled || entity == null || maxDistance <= 0.0D) return true;

        int refresh = Math.max(1, refreshFrames);
        int maxAge = Math.max(1, maxAgeFrames);
        Decision cached = decisions.get(entity);
        long age = cached == null ? Long.MAX_VALUE : frame - cached.frame;

        // Always calculate a decision for a new entity. Afterwards, refresh on the configured
        // cadence, but never allow an old answer to survive beyond maxAgeFrames.
        if (cached == null || age >= refresh || age >= maxAge) {
            double dx = entity.getX() - cameraX;
            double dy = entity.getY() - cameraY;
            double dz = entity.getZ() - cameraZ;
            boolean visible = dx * dx + dy * dy + dz * dz <= maxDistance * maxDistance;

            if (cached == null) {
                cached = new Decision();
                decisions.put(entity, cached);
            }
            cached.frame = frame;
            cached.visible = visible;
            return visible;
        }

        return cached.visible;
    }

    public void shutdown() {
        decisions.clear();
    }
}
