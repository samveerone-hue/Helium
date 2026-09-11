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

    public boolean enabled = false;
    public boolean lineOfSight = false;
    public boolean pathfinding = false;
    public int gridSize = 32;
    public int refreshTicks = 2;
    // Safe default: one ray per world snapshot. Batching multiple rays against an
    // anchor-centered snapshot can otherwise evaluate distant rays against incomplete data.
    public int maxBatch = 1;

    private GpuComputeConfig() {}

    public static GpuComputeConfig load() {
        if (Files.exists(PATH)) {
            try {
                GpuComputeConfig cfg = GSON.fromJson(Files.readString(PATH), GpuComputeConfig.class);
                if (cfg != null) { cfg.save(); return cfg; }
            } catch (IOException ignored) {}
        }
        GpuComputeConfig cfg = new GpuComputeConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException ignored) {}
    }
}
