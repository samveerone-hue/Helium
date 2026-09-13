package com.helium.rentities.entities;

import net.minecraft.entity.Entity;

/**
 * @deprecated The visibility decision path is synchronous and render-thread-only. New code
 * should use {@link VisibilityDecisionCache}; this class remains only as a source-compatibility
 * facade for older callers whose references may not be discoverable by GitHub code search.
 */
@Deprecated
public final class AsyncVisibilityManager {
    private final VisibilityDecisionCache delegate = new VisibilityDecisionCache();

    public void beginFrame(long frame) {
        delegate.beginFrame(frame);
    }

    public boolean shouldBatch(Entity entity, double cameraX, double cameraY, double cameraZ,
                               boolean enabled, int refreshFrames, int maxAgeFrames, double maxDistance) {
        return delegate.shouldBatch(entity, cameraX, cameraY, cameraZ, enabled,
                refreshFrames, maxAgeFrames, maxDistance);
    }

    public void shutdown() {
        delegate.shutdown();
    }
}
