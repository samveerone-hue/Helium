package com.helium.rentities.entities;

/** Pure decision rule reserved for runtime-free Rentities decision tests. */
public final class BatchabilityDecision {
    private BatchabilityDecision() {}

    public static boolean allowResolved(boolean resolved, boolean gpuBatchable) {
        return resolved && gpuBatchable;
    }
}
