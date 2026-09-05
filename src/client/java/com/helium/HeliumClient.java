package com.helium;

import com.helium.config.HeliumConfig;
import com.helium.dedup.DeduplicationManager;
import com.helium.tweaks.AsyncPackReloader;
import com.helium.gpu.AdaptiveSyncManager;
import com.helium.gpu.AmdOptimizer;
import com.helium.gpu.GpuDetector;
import com.helium.gpu.IntelOptimizer;
import com.helium.gpu.NvidiaOptimizer;
import com.helium.idle.IdleManager;
import com.helium.lighting.AsyncLightEngine;
import com.helium.math.FastMath;
import com.helium.memory.AllocationReducer;
import com.helium.memory.BufferPool;
import com.helium.memory.NativeMemoryManager;
import com.helium.memory.ObjectPool;
import com.helium.network.FastIpPingOptimizer;
import com.helium.platform.DeviceDetector;
import com.helium.render.DevModeOptimizer;
import com.helium.render.EnumValueCache;
import com.helium.render.FastAnimationOptimizer;
import com.helium.render.FastWorldLoadingOptimizer;
import com.helium.render.GLStateCache;
import com.helium.render.HeliumBlockEntityCulling;
import com.helium.render.RenderPipeline;
import com.helium.render.TemporalReprojection;
import com.helium.feature.FullbrightManager;
import com.helium.threading.EventPoller;
import com.helium.threading.ThreadPriorityManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import com.helium.compat.CrossLoaderCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HeliumClient implements ClientModInitializer {

    public static final String MOD_ID = "helium";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static HeliumConfig config;
    private static KeyBinding fullbrightKey;
    private static KeyBinding reloadpackskey;

    private static boolean hasLithium = false;
    private static boolean hasIris = false;
    private static boolean hasImmediatelyFast = false;

    private static boolean fastMathFailed = false;
    private static boolean memoryOptsFailed = false;
    private static boolean glStateCacheFailed = false;
    private static boolean threadOptsFailed = false;
    private static boolean networkOptsFailed = false;
    private static boolean startupOptsFailed = false;
    private static boolean nativeMemoryFailed = false;
    private static boolean renderPipelineFailed = false;
    private static boolean modelCacheFailed = false;
    private static boolean allocationReducerFailed = false;
    private static boolean simdMathFailed = false;
    private static boolean asyncLightFailed = false;
    private static boolean packetBatcherFailed = false;
    private static boolean idleManagerFailed = false;
    private static boolean gpuDetectorFailed = false;
    private static boolean gpuOptsFailed = false;
    private static boolean adaptiveSyncFailed = false;
    private static boolean temporalReprojectionFailed = false;
    private static boolean fastAnimFailed = false;
    private static boolean enumCacheFailed = false;
    private static boolean fastWorldLoadFailed = false;
    private static boolean dedupFailed = false;
    private static boolean devModeOptFailed = false;
    private static boolean gpuInitDeferred = true;
    private static boolean isAndroid = false;

    @Override
    public void onInitializeClient() {
        long start = System.nanoTime();

        config = HeliumConfig.load();

        isAndroid = DeviceDetector.isAndroid();
        if (isAndroid) {
            LOGGER.warn("android detected - disabling gl state cache for compatibility");
            config.glStateCache = false;
            config.save();
        }

        if (!config.modEnabled) {
            LOGGER.info("helium is disabled via config");
            return;
        }

        detectCompatibleMods();

        initFeatureSafely("FastMath", () -> {
            if (config.fastMath) FastMath.init();
        }, () -> fastMathFailed = true);

        initFeatureSafely("MemoryOptimizations", () -> {
            if (config.memoryOptimizations) {
                ObjectPool.init(512);
                BufferPool.init(64);
            }
        }, () -> memoryOptsFailed = true);

        initFeatureSafely("GLStateCache", () -> {
            if (config.glStateCache) {
                glStateCacheFailed = true;
                LOGGER.warn("global GL state cache is disabled until renderer-owned invalidation is available");
            }
        }, () -> glStateCacheFailed = true);

        initFeatureSafely("ThreadOptimizations", () -> {
            if (config.threadOptimizations) {
                ThreadPriorityManager.init();
                EventPoller.init(1000);
            }
        }, () -> threadOptsFailed = true);

        initFeatureSafely("NativeMemory", () -> {
            if (config.nativeMemory) NativeMemoryManager.init(config.nativeMemoryPoolMb);
        }, () -> nativeMemoryFailed = true);

        initFeatureSafely("RenderPipeline", () -> {
            if (config.renderPipelining) RenderPipeline.init();
        }, () -> renderPipelineFailed = true);

        initFeatureSafely("BlockEntityCulling", () -> {
            if (config.blockEntityCulling && !HeliumBlockEntityCulling.isRegistered()) {
                HeliumBlockEntityCulling.register();
            }
        }, () -> LOGGER.warn("block entity culling fallback failed, will rely on mixin"));

        initFeatureSafely("AllocationReducer", () -> {
            if (config.reducedAllocations) AllocationReducer.init();
        }, () -> allocationReducerFailed = true);

        initFeatureSafely("IdleManager", () -> {
            IdleManager.init();
            CrossLoaderCompat.registertickevent(IdleManager::handleclienttick);
        }, () -> idleManagerFailed = true);

        initFeatureSafely("TemporalReprojection", () -> {
            if (config.temporalReprojection) TemporalReprojection.init();
        }, () -> temporalReprojectionFailed = true);

        initFeatureSafely("FastAnimations", () -> {
            if (config.fastAnimations) FastAnimationOptimizer.init();
        }, () -> fastAnimFailed = true);

        initFeatureSafely("EnumValueCache", () -> {
            if (config.cachedEnumValues) EnumValueCache.init();
        }, () -> enumCacheFailed = true);

        initFeatureSafely("FastWorldLoading", () -> {
            if (config.fastWorldLoading) FastWorldLoadingOptimizer.init();
        }, () -> fastWorldLoadFailed = true);

        initFeatureSafely("FastIpPing", () -> {
            if (config.fastIpPing) FastIpPingOptimizer.init();
        }, null);

        initFeatureSafely("ObjectDeduplication", () -> {
            if (config.objectDeduplication) DeduplicationManager.init();
        }, () -> dedupFailed = true);

        initFeatureSafely("JomlFastMath", () -> {
            if (config.jomlFastMath) {
                System.setProperty("joml.fastmath", "true");
                LOGGER.info("joml.fastmath system property set to true");
            }
        }, null);

        initFeatureSafely("DSA", () -> {
            if (config.directStateAccess) {
                LOGGER.info("dsa feature enabled - caps will be queried on render thread");
            }
        }, null);

        initFeatureSafely("OneClickCrafting", () -> {
            if (config.oneClickCrafting) com.helium.crafting.OneClickCraftingManager.init();
        }, null);

        fullbrightKey = CrossLoaderCompat.registerkeybinding(createKeyBinding(
                "helium.key.fullbright",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "helium.key.category"
        ));

        FullbrightManager.setStrength(config.fullbrightStrength);
        if (config.fullbright) {
            FullbrightManager.enable();
        }

        CrossLoaderCompat.registertickevent(() -> {
            if (fullbrightKey.wasPressed()) {
                FullbrightManager.toggle();
                config.fullbright = FullbrightManager.isEnabled();
                config.save();
            }
        });

        reloadpackskey = CrossLoaderCompat.registerkeybinding(createKeyBinding(
                "helium.key.reload_packs",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "helium.key.category"
        ));

        CrossLoaderCompat.registertickevent(() -> {
            if (reloadpackskey.wasPressed() && config.asyncPackReload) {
                AsyncPackReloader.reloadasync();
            }
            AsyncPackReloader.tick();
        });

        CrossLoaderCompat.registertickevent(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (gpuInitDeferred && client != null && client.getWindow() != null) {
                gpuInitDeferred = false;
                initDeferredGpuFeatures();
            }
        });

        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            if (config == null || !config.modEnabled || !config.entityGpuBatching) return;
            try {
                if (com.helium.rentities.RendererCapabilityState.current() == null) {
                    com.helium.rentities.RendererCapabilityState.probe();
                }
                if (com.helium.rentities.RendererCapabilityState.current() != null
                        && com.helium.rentities.RendererCapabilityState.current().gpuBatchingAllowed(config)
                        && com.helium.rentities.entities.EntityBatchRenderer.INSTANCE == null) {
                    new com.helium.rentities.entities.EntityBatchRenderer();
                }
                var renderer = com.helium.rentities.entities.EntityBatchRenderer.INSTANCE;
                if (renderer != null
                        && com.helium.rentities.RendererCapabilityState.current() != null
                        && com.helium.rentities.RendererCapabilityState.current().gpuBatchingAllowed(config)) {
                    var camera = context.camera().getPos();
                    com.helium.rentities.entities.EntityBatchRenderer.cameraX = camera.x;
                    com.helium.rentities.entities.EntityBatchRenderer.cameraY = camera.y;
                    com.helium.rentities.entities.EntityBatchRenderer.cameraZ = camera.z;
                    com.helium.rentities.entities.EntityBatchRenderer.beginWorldRender(
                            context.positionMatrix(), context.projectionMatrix());
                }
            } catch (Throwable t) {
                LOGGER.warn("Rentities entity batching preparation failed; using vanilla rendering", t);
            }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (config == null || !config.modEnabled || !config.entityGpuBatching) return;
            try {
                com.helium.rentities.entities.EntityBatchRenderer.flushBatch();
            } catch (Throwable t) {
                LOGGER.warn("Rentities entity batch flush failed; using vanilla rendering", t);
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> AsyncPackReloader.shutdown());

        long elapsed = (System.nanoTime() - start) / 1_000_000;
        LOGGER.info("initialized in {}ms", elapsed);
    }

    private void initDeferredGpuFeatures() {
        initFeatureSafely("GLCaps", () -> {
            com.helium.gpu.GBGL.initcaps();
        }, null);

        initFeatureSafely("RenderThreadPriority", () -> {
            if (config.threadOptimizations) ThreadPriorityManager.initRenderThread();
        }, null);

        initFeatureSafely("GpuDetector", () -> {
            GpuDetector.init();
        }, () -> gpuDetectorFailed = true);

        initFeatureSafely("GpuOptimizations", () -> {
            if (config.nvidiaOptimizations && GpuDetector.isNvidia()) NvidiaOptimizer.init();
            if (config.amdOptimizations && GpuDetector.isAmd()) AmdOptimizer.init();
            if (config.intelOptimizations && GpuDetector.isIntel()) IntelOptimizer.init();
        }, () -> gpuOptsFailed = true);

        initFeatureSafely("AdaptiveSync", () -> {
            if (config.adaptiveSync) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.getWindow() != null) {
                    AdaptiveSyncManager.init(mc.getWindow().getHandle());
                }
            }
        }, () -> adaptiveSyncFailed = true);

        initFeatureSafely("DevModeOptimizer", () -> {
            DevModeOptimizer.init();
            if (config.devMode) {
                DevModeOptimizer.activate();
            }
        }, () -> devModeOptFailed = true);

        LOGGER.info("deferred gpu features initialized");
    }

    private void detectCompatibleMods() {
        FabricLoader loader = FabricLoader.getInstance();
        hasLithium = loader.isModLoaded("lithium");
        hasIris = loader.isModLoaded("iris");
        hasImmediatelyFast = loader.isModLoaded("immediatelyfast");

        if (hasLithium) LOGGER.info("lithium detected - compatible");
        if (hasIris) LOGGER.info("iris detected - compatible");
        if (hasImmediatelyFast) LOGGER.info("immediatelyfast detected - disabling gl state cache");
    }

    public static boolean hasImmediatelyFast() { return hasImmediatelyFast; }
    public static boolean hasLithium() { return hasLithium; }
    public static boolean hasIris() { return hasIris; }

    public static HeliumConfig getConfig() {
        return config;
    }

    private void initFeatureSafely(String name, Runnable init, Runnable onFailure) {
        try {
            init.run();
        } catch (Throwable t) {
            LOGGER.error("{} failed to initialize, feature disabled", name, t);
            if (onFailure != null) onFailure.run();
        }
    }

    public static void setRenderPipelining(boolean enabled) {
        if (config == null) return;
        config.renderPipelining = enabled;

        if (enabled) {
            try {
                RenderPipeline.init();
                renderPipelineFailed = false;
            } catch (Throwable t) {
                renderPipelineFailed = true;
                LOGGER.error("RenderPipeline failed to initialize from live config toggle", t);
            }
        } else {
            com.helium.render.AsyncChunkMeshing.clear();
            com.helium.render.RenderBatch.clear();
            RenderPipeline.shutdown();
        }
    }

    public static boolean isFastMathAvailable() { return !fastMathFailed; }
    public static boolean isMemoryOptsAvailable() { return !memoryOptsFailed; }
    public static boolean isGlStateCacheAvailable() { return !glStateCacheFailed; }
    public static boolean isThreadOptsAvailable() { return !threadOptsFailed; }
    public static boolean isNetworkOptsAvailable() { return false; }
    public static boolean isStartupOptsAvailable() { return false; }
    public static boolean isNativeMemoryAvailable() { return !nativeMemoryFailed; }
    public static boolean isRenderPipelineAvailable() { return !renderPipelineFailed; }
    public static boolean isModelCacheAvailable() { return false; }
    public static boolean isAllocationReducerAvailable() { return !allocationReducerFailed; }
    public static boolean isSimdMathAvailable() { return false; }
    public static boolean isAsyncLightAvailable() { return false; }
    public static boolean isPacketBatcherAvailable() { return false; }
    public static boolean isIdleManagerAvailable() { return !idleManagerFailed; }
    public static boolean isGpuDetectorAvailable() { return !gpuDetectorFailed; }
    public static boolean isGpuOptsAvailable() { return !gpuOptsFailed; }
    public static boolean isAdaptiveSyncAvailable() { return !adaptiveSyncFailed; }
    public static boolean isTemporalReprojectionAvailable() { return !temporalReprojectionFailed; }
    public static boolean isAndroid() { return DeviceDetector.isAndroid(); }

    private static KeyBinding.Category heliumkeycategory = null;

    private static KeyBinding createKeyBinding(String id, InputUtil.Type type, int code, String category) {
        try {
            if (heliumkeycategory == null) {
                heliumkeycategory = KeyBinding.Category.create(
                        com.helium.util.VersionCompat.createIdentifier(MOD_ID, "keys"));
            }
            return new KeyBinding(id, type, code, heliumkeycategory);
        } catch (NoClassDefFoundError | NoSuchMethodError e1) {
            try {
                java.lang.reflect.Constructor<?> ctor = KeyBinding.class.getDeclaredConstructor(
                        String.class, InputUtil.Type.class, int.class, String.class);
                ctor.setAccessible(true);
                return (KeyBinding) ctor.newInstance(id, type, code, category);
            } catch (Throwable e2) {
                LOGGER.warn("keybinding compat fallback failed, using MISC category");
                try {
                    return new KeyBinding(id, code, KeyBinding.Category.MISC);
                } catch (NoClassDefFoundError | NoSuchMethodError e3) {
                    java.lang.reflect.Constructor<?> simpleCtor;
                    try {
                        simpleCtor = KeyBinding.class.getDeclaredConstructor(String.class, int.class, String.class);
                        simpleCtor.setAccessible(true);
                        return (KeyBinding) simpleCtor.newInstance(id, code, category);
                    } catch (Throwable e4) {
                        throw new RuntimeException("cannot create keybinding on this minecraft version", e4);
                    }
                }
            }
        }
    }
}
