package com.helium.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.helium.HeliumClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class HeliumConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("helium.json");

    public boolean modEnabled = true;

    public boolean fastMath = true;
    public boolean glStateCache = false;
    public boolean memoryOptimizations = true;
    public boolean threadOptimizations = true;
    public boolean networkOptimizations = true;
    public boolean fastStartup = true;

    public boolean entityCulling = true;
    public int entityCullDistance = 64;
    public boolean blockEntityCulling = true;
    public int blockEntityCullDistance = 48;
    public boolean particleCulling = true;
    public int particleCullDistance = 32;
    public boolean particleLimiting = true;
    public int maxParticles = 1000;
    public boolean particlePriority = true;
    public boolean particleBatching = true;
    public boolean animationThrottling = true;

    public boolean smoothScrolling = true;

    public boolean windowStyle = true;
    public String windowMaterial = "TABBED";
    public String windowCorner = "ROUND";

    public boolean fastServerPing = true;
    public boolean preserveScrollOnRefresh = true;
    public boolean directConnectPreview = true;

    public boolean fpsOverlay = true;
    public boolean overlayShowFps = true;
    public boolean overlayShowFpsMinMaxAvg = false;
    public boolean overlayShowMemory = false;
    public boolean overlayShowParticles = false;
    public boolean overlayShowCoordinates = false;
    public boolean overlayShowBiome = false;
    public String overlayPosition = "TOP_LEFT";
    public int overlayTransparency = 60;
    public String overlayBackgroundColor = "#000000";
    public String overlayTextColor = "#FFFFFF";

    public boolean nativeMemory = true;
    public int nativeMemoryPoolMb = 64;
    public boolean renderPipelining = false;

    public boolean modelCache = true;
    public int modelCacheMaxMb = 64;
    public boolean reducedAllocations = true;
    public boolean simdMath = true;
    public boolean asyncLightUpdates = true;
    public boolean packetBatching = true;
    public boolean autoPauseOnIdle = false;
    public int idleTimeoutSeconds = 60;
    public int idleFpsLimit = 5;
    public boolean nvidiaOptimizations = true;
    public boolean amdOptimizations = true;
    public boolean intelOptimizations = true;
    public boolean adaptiveSync = false;
    public int displaySyncRefreshRate = -1;
    public boolean temporalReprojection = false;
    public boolean fullbright = false;
    public int fullbrightStrength = 10;
    public boolean fastAnimations = false;
    public boolean cachedEnumValues = false;
    public boolean fastWorldLoading = true;
    public boolean fastIpPing = true;

    public boolean suppressOpenGLErrors = true;
    public boolean fastFramebufferBlit = true;
    public boolean poseStackPooling = true;
    public boolean fastBambooLight = true;
    public boolean optimizedLightEngine = true;
    public boolean screenshotLeakFix = true;
    public boolean framebufferCleaner = true;
    public boolean instantLanguageChange = true;
    public boolean enableReflex = true;
    public long reflexOffsetNs = 0L;
    public boolean reflexDebug = false;
    public String leafCullingMode = "FAST";
    public int leafCullingDepth = 2;
    public float leafCullingRandomRejection = 0.2f;
    public boolean leafCullingMangroveRoots = false;
    public boolean particleLOD = false;
    public double particleLODDistance = 16.0;
    public double particleLODReduction = 0.3;
    public boolean reduceFpsWhenInactive = false;
    public int inactiveFpsLimit = 10;
    public boolean reduceRenderDistanceWhenInactive = false;
    public int inactiveRenderDistance = 4;

    public boolean hotbarOptimizer = false;
    public boolean hotbarMultiSwitch = false;
    public boolean smoothHotbar = true;

    public boolean forceSkinParts = true;

    public boolean asyncPackReload = true;

    public int menuFramerateLimit = 0;

    public boolean acceleratedText = false;

    public boolean shaderUniformCache = true;

    public boolean signTextCulling = true;
    public boolean rainCulling = true;
    public boolean beaconBeamCulling = true;
    public boolean paintingCulling = true;
    public boolean itemFrameCulling = true;
    public boolean itemFrameLOD = false;
    public int itemFrameLODRange = 128;

    public boolean objectDeduplication = true;

    public boolean glContextUpgrade = true;
    public boolean jomlFastMath = true;
    public boolean fastRandom = true;
    public boolean directStateAccess = true;
    public boolean renderbufferDepth = false;
    public boolean oneClickCrafting = false;

    public boolean devMode = false;


    public static HeliumConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                HeliumConfig cfg = GSON.fromJson(json, HeliumConfig.class);
                if (cfg != null) {
                    cfg.save();
                    return cfg;
                }
            } catch (IOException e) {
                HeliumClient.LOGGER.warn("failed to load config, using defaults", e);
            }
        }

        HeliumConfig cfg = new HeliumConfig();
        cfg.save();
        return cfg;
    }

    public String renderingsnapshot() {
        return entityCulling + "," + entityCullDistance + "," +
                blockEntityCulling + "," + blockEntityCullDistance + "," +
                particleCulling + "," + particleCullDistance + "," +
                particleLimiting + "," + maxParticles + "," +
                particlePriority + "," + particleBatching + "," +
                animationThrottling + "," + fastMath + "," +
                glStateCache + "," + fastAnimations + "," +
                cachedEnumValues + "," + modelCache + "," + modelCacheMaxMb + "," +
                leafCullingMode + "," + leafCullingDepth + "," +
                leafCullingMangroveRoots + "," +
                signTextCulling + "," + rainCulling + "," +
                beaconBeamCulling + "," + paintingCulling + "," +
                itemFrameCulling + "," + itemFrameLOD + "," + itemFrameLODRange + "," +
                renderPipelining + "," + fastFramebufferBlit + "," +
                poseStackPooling + "," + fastBambooLight + "," +
                optimizedLightEngine + "," + fullbright;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            HeliumClient.LOGGER.warn("failed to save config", e);
        }
    }

    public boolean exportToFile(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
            HeliumClient.LOGGER.info("config exported to {}", path);
            return true;
        } catch (IOException e) {
            HeliumClient.LOGGER.warn("failed to export config to {}", path, e);
            return false;
        }
    }

    public static HeliumConfig importFromFile(Path path) {
        if (!Files.exists(path)) {
            HeliumClient.LOGGER.warn("import file does not exist: {}", path);
            return null;
        }
        try {
            String json = Files.readString(path);
            HeliumConfig imported = GSON.fromJson(json, HeliumConfig.class);
            if (imported != null) {
                HeliumClient.LOGGER.info("config imported from {}", path);
                return imported;
            }
        } catch (IOException e) {
            HeliumClient.LOGGER.warn("failed to import config from {}", path, e);
        }
        return null;
    }

    public void copyFrom(HeliumConfig other) {
        if (other == null) return;
        this.modEnabled = other.modEnabled;
        this.fastMath = other.fastMath;
        this.glStateCache = other.glStateCache;
        this.memoryOptimizations = other.memoryOptimizations;
        this.threadOptimizations = other.threadOptimizations;
        this.networkOptimizations = other.networkOptimizations;
        this.fastStartup = other.fastStartup;
        this.entityCulling = other.entityCulling;
        this.entityCullDistance = other.entityCullDistance;
        this.blockEntityCulling = other.blockEntityCulling;
        this.blockEntityCullDistance = other.blockEntityCullDistance;
        this.particleCulling = other.particleCulling;
        this.particleCullDistance = other.particleCullDistance;
        this.particleLimiting = other.particleLimiting;
        this.maxParticles = other.maxParticles;
        this.particlePriority = other.particlePriority;
        this.particleBatching = other.particleBatching;
        this.animationThrottling = other.animationThrottling;
        this.fastServerPing = other.fastServerPing;
        this.preserveScrollOnRefresh = other.preserveScrollOnRefresh;
        this.directConnectPreview = other.directConnectPreview;
        this.fpsOverlay = other.fpsOverlay;
        this.overlayShowFps = other.overlayShowFps;
        this.overlayShowFpsMinMaxAvg = other.overlayShowFpsMinMaxAvg;
        this.overlayShowMemory = other.overlayShowMemory;
        this.overlayShowParticles = other.overlayShowParticles;
        this.overlayPosition = other.overlayPosition;
        this.overlayTransparency = other.overlayTransparency;
        this.overlayBackgroundColor = other.overlayBackgroundColor;
        this.overlayTextColor = other.overlayTextColor;
        this.nativeMemory = other.nativeMemory;
        this.nativeMemoryPoolMb = other.nativeMemoryPoolMb;
        this.renderPipelining = other.renderPipelining;
        this.modelCache = other.modelCache;
        this.modelCacheMaxMb = other.modelCacheMaxMb;
        this.reducedAllocations = other.reducedAllocations;
        this.simdMath = other.simdMath;
        this.asyncLightUpdates = other.asyncLightUpdates;
        this.packetBatching = other.packetBatching;
        this.autoPauseOnIdle = other.autoPauseOnIdle;
        this.idleTimeoutSeconds = other.idleTimeoutSeconds;
        this.idleFpsLimit = other.idleFpsLimit;
        this.nvidiaOptimizations = other.nvidiaOptimizations;
        this.amdOptimizations = other.amdOptimizations;
        this.intelOptimizations = other.intelOptimizations;
        this.adaptiveSync = other.adaptiveSync;
        this.displaySyncRefreshRate = other.displaySyncRefreshRate;
        this.temporalReprojection = other.temporalReprojection;
        this.fullbright = other.fullbright;
        this.fullbrightStrength = other.fullbrightStrength;
        this.fastAnimations = other.fastAnimations;
        this.cachedEnumValues = other.cachedEnumValues;
        this.fastWorldLoading = other.fastWorldLoading;
        this.fastIpPing = other.fastIpPing;
        this.overlayShowCoordinates = other.overlayShowCoordinates;
        this.overlayShowBiome = other.overlayShowBiome;
        this.devMode = other.devMode;
        this.smoothScrolling = other.smoothScrolling;
        this.windowStyle = other.windowStyle;
        this.windowMaterial = other.windowMaterial;
        this.windowCorner = other.windowCorner;
        this.suppressOpenGLErrors = other.suppressOpenGLErrors;
        this.fastFramebufferBlit = other.fastFramebufferBlit;
        this.poseStackPooling = other.poseStackPooling;
        this.fastBambooLight = other.fastBambooLight;
        this.optimizedLightEngine = other.optimizedLightEngine;
        this.screenshotLeakFix = other.screenshotLeakFix;
        this.framebufferCleaner = other.framebufferCleaner;
        this.instantLanguageChange = other.instantLanguageChange;
        this.enableReflex = other.enableReflex;
        this.reflexOffsetNs = other.reflexOffsetNs;
        this.reflexDebug = other.reflexDebug;
        this.leafCullingMode = other.leafCullingMode;
        this.leafCullingDepth = other.leafCullingDepth;
        this.leafCullingRandomRejection = other.leafCullingRandomRejection;
        this.leafCullingMangroveRoots = other.leafCullingMangroveRoots;
        this.particleLOD = other.particleLOD;
        this.particleLODDistance = other.particleLODDistance;
        this.particleLODReduction = other.particleLODReduction;
        this.reduceFpsWhenInactive = other.reduceFpsWhenInactive;
        this.inactiveFpsLimit = other.inactiveFpsLimit;
        this.reduceRenderDistanceWhenInactive = other.reduceRenderDistanceWhenInactive;
        this.inactiveRenderDistance = other.inactiveRenderDistance;
        this.hotbarOptimizer = other.hotbarOptimizer;
        this.hotbarMultiSwitch = other.hotbarMultiSwitch;
        this.smoothHotbar = other.smoothHotbar;
        this.forceSkinParts = other.forceSkinParts;
        this.asyncPackReload = other.asyncPackReload;
        this.menuFramerateLimit = other.menuFramerateLimit;
        this.acceleratedText = other.acceleratedText;
        this.shaderUniformCache = other.shaderUniformCache;
        this.signTextCulling = other.signTextCulling;
        this.rainCulling = other.rainCulling;
        this.beaconBeamCulling = other.beaconBeamCulling;
        this.paintingCulling = other.paintingCulling;
        this.itemFrameCulling = other.itemFrameCulling;
        this.itemFrameLOD = other.itemFrameLOD;
        this.itemFrameLODRange = other.itemFrameLODRange;
        this.objectDeduplication = other.objectDeduplication;
        this.glContextUpgrade = other.glContextUpgrade;
        this.jomlFastMath = other.jomlFastMath;
        this.fastRandom = other.fastRandom;
        this.directStateAccess = other.directStateAccess;
        this.renderbufferDepth = other.renderbufferDepth;
        this.oneClickCrafting = other.oneClickCrafting;
    }
}
