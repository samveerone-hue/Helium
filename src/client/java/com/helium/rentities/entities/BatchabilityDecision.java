package com.helium.rentities.entities;

/** Pure decision rule used to keep unresolved GPU batchability on the vanilla path. */
public final class BatchabilityDecision {
    private BatchabilityDecision() {}

    public static boolean allowResolved(boolean resolved, boolean gpuBatchable) {
        return resolved && gpuBatchable;
    }
}
