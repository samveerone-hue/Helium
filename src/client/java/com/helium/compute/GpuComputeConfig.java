package com.helium.compute;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GpuComputeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("helium-gpu-compute.json");

    /** Master switch for the optional OpenCL co-processor. Disabled by default. */
    public boolean enabled = false;
    /** Offload entity line-of-sight tests to OpenCL. Disabled by default. */
    public boolean lineOfSight = false;
    /** Enable the flow-field OpenCL kernel. This currently exposes the compute API only; it does not replace vanilla navigation. */
    public boolean pathfinding = false;
    /** Requested cube edge length for world snapshots. The runtime expands it when needed and rejects oversized rays safely. */
    public int gridSize = 32;
    /** Number of client ticks a cached line-of-sight result may be reused. */
    public int refreshTicks = 2;
    /** Maximum number of LOS requests packed into one GPU snapshot. One is the safest default. */
    public int maxBatch = 1;

    private GpuComputeConfig() {}

    public static GpuComputeConfig load() {
        GpuComputeConfig cfg = null;
        if (Files.exists(PATH)) {
            try {
                cfg = GSON.fromJson(Files.readString(PATH), GpuComputeConfig.class);
            } catch (IOException | RuntimeException ignored) {}
        }
        if (cfg == null) cfg = new GpuComputeConfig();
        cfg.sanitize();
        cfg.save();
        return cfg;
    }

    private void sanitize() {
        // Keep configuration inside the range exposed by the GUI and the safe runtime limits.
        gridSize = Math.max(16, Math.min(48, gridSize));
        refreshTicks = Math.max(1, Math.min(10, refreshTicks));
        maxBatch = Math.max(1, Math.min(8, maxBatch));
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException ignored) {}
    }
}
