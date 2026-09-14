package com.helium.compute;

/** Pure world-snapshot geometry used by the GPU LOS batching path. */
public final class GpuComputeMath {
    private GpuComputeMath() {}

    public record Grid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int size) {}

    /**
     * Computes a centered cube that contains every ray endpoint plus a one-block sampling margin.
     * Returns null when the requested snapshot would exceed the hard 64-block safety limit.
     */
    public static Grid computeGrid(float[] rays, int requestedSize) {
        if (rays == null || rays.length == 0 || (rays.length % 6) != 0) return null;

        float minFx = Float.POSITIVE_INFINITY, minFy = Float.POSITIVE_INFINITY, minFz = Float.POSITIVE_INFINITY;
        float maxFx = Float.NEGATIVE_INFINITY, maxFy = Float.NEGATIVE_INFINITY, maxFz = Float.NEGATIVE_INFINITY;
        for (int base = 0; base < rays.length; base += 6) {
            minFx = Math.min(minFx, Math.min(rays[base], rays[base + 3]));
            minFy = Math.min(minFy, Math.min(rays[base + 1], rays[base + 4]));
            minFz = Math.min(minFz, Math.min(rays[base + 2], rays[base + 5]));
            maxFx = Math.max(maxFx, Math.max(rays[base], rays[base + 3]));
            maxFy = Math.max(maxFy, Math.max(rays[base + 1], rays[base + 4]));
            maxFz = Math.max(maxFz, Math.max(rays[base + 2], rays[base + 5]));
        }

        int minX = (int) Math.floor(minFx) - 1;
        int minY = (int) Math.floor(minFy) - 1;
        int minZ = (int) Math.floor(minFz) - 1;
        int maxX = (int) Math.floor(maxFx) + 1;
        int maxY = (int) Math.floor(maxFy) + 1;
        int maxZ = (int) Math.floor(maxFz) + 1;
        int requiredX = maxX - minX + 1;
        int requiredY = maxY - minY + 1;
        int requiredZ = maxZ - minZ + 1;
        int safeRequestedSize = Math.max(16, Math.min(48, requestedSize));
        int size = Math.max(safeRequestedSize, Math.max(requiredX, Math.max(requiredY, requiredZ)));
        if (size > 64) return null;

        int centerX = (minX + maxX) / 2;
        int centerY = (minY + maxY) / 2;
        int centerZ = (minZ + maxZ) / 2;
        minX = centerX - size / 2;
        minY = centerY - size / 2;
        minZ = centerZ - size / 2;
        maxX = minX + size - 1;
        maxY = minY + size - 1;
        maxZ = minZ + size - 1;

        if (maxX < (int) Math.ceil(maxFx) || maxY < (int) Math.ceil(maxFy) || maxZ < (int) Math.ceil(maxFz)
                || minX > (int) Math.floor(minFx) || minY > (int) Math.floor(minFy) || minZ > (int) Math.floor(minFz)) {
            return null;
        }
        return new Grid(minX, minY, minZ, maxX, maxY, maxZ, size);
    }
}
