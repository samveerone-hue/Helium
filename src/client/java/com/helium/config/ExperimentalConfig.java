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
    private static final Path PATH = configPath();

    private static Path configPath() {
        try {
            FabricLoader loader = FabricLoader.getInstance();
            if (loader != null) {
                return loader.getConfigDir().resolve("helium-experimental.json");
            }
        } catch (Throwable ignored) {
            // fall through to the safe fallback below
        }
        return Path.of(System.getProperty("user.home", "."), ".helium", "helium-experimental.json");
    }

    public boolean glStateCache = false;
    public boolean packetBatching = false;
    public boolean fastStartup = false;
    public boolean modelCache = false;
    public boolean simdMath = false;

    public int packetBatchTicks = 1;
    public int modelCacheMaxMb = 64;

    private static volatile ExperimentalConfig INSTANCE;

    private ExperimentalConfig() {}

    public static ExperimentalConfig get() {
        ExperimentalConfig cached = INSTANCE;
        if (cached != null) return cached;
        return load();
    }

    public static void invalidate() {
        synchronized (ExperimentalConfig.class) {
            INSTANCE = null;
        }
    }

    public static ExperimentalConfig load() {
        ExperimentalConfig cached = INSTANCE;
        if (cached != null) return cached;
        synchronized (ExperimentalConfig.class) {
            cached = INSTANCE;
            if (cached != null) return cached;

            ExperimentalConfig cfg = new ExperimentalConfig();
            if (Files.exists(PATH)) {
                try {
                    ExperimentalConfig loaded = GSON.fromJson(Files.readString(PATH), ExperimentalConfig.class);
                    if (loaded != null) cfg = loaded;
                } catch (IOException | RuntimeException ignored) {
                }
            }
            cfg.sanitize();
            INSTANCE = cfg;
            cfg.save();
            return cfg;
        }
    }

    void sanitize() {
        packetBatchTicks = ConfigClamps.experimentalPacketBatchTicks(packetBatchTicks);
        modelCacheMaxMb = ConfigClamps.experimentalModelCacheMaxMb(modelCacheMaxMb);
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
            synchronized (ExperimentalConfig.class) {
                INSTANCE = this;
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }
}
