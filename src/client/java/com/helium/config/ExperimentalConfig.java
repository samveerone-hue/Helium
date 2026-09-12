package com.helium.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Real, opt-in experimental features which are intentionally separate from the stable schema. */
public final class ExperimentalConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("helium-experimental.json");

    public boolean networkOptimizations = false;
    public boolean glStateCache = false;
    public boolean packetBatching = false;

    public int packetBatchTicks = 1;

    private static volatile ExperimentalConfig INSTANCE;

    private ExperimentalConfig() {}

    public static synchronized ExperimentalConfig load() {
        if (INSTANCE != null) return INSTANCE;
        ExperimentalConfig cfg = new ExperimentalConfig();
        if (Files.exists(PATH)) {
            try {
                ExperimentalConfig loaded = GSON.fromJson(Files.readString(PATH), ExperimentalConfig.class);
                if (loaded != null) cfg = loaded;
            } catch (IOException ignored) {
            }
        }
        cfg.packetBatchTicks = Math.max(1, Math.min(2, cfg.packetBatchTicks));
        INSTANCE = cfg;
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException ignored) {
        }
    }
}
