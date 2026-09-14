package com.helium.compute;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.helium.config.ConfigClamps;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GpuComputeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("helium-gpu-compute.json");
    public boolean enabled = false;
    public boolean lineOfSight = false;
    /** Reserved until a real navigation consumer exists; kept fail-closed. */
    public boolean pathfinding = false;
    public int gridSize = 32;
    public int refreshTicks = 2;
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

    void sanitize() {
        pathfinding = false;
        gridSize = ConfigClamps.gpuGridSize(gridSize);
        refreshTicks = ConfigClamps.gpuRefreshTicks(refreshTicks);
        maxBatch = ConfigClamps.gpuMaxBatch(maxBatch);
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException ignored) {}
    }
}
