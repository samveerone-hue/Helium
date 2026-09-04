package com.helium.config;

import com.helium.HeliumClient;
import com.helium.platform.DeviceDetector;
import com.helium.render.DisplaySyncOptimizer;
import com.helium.render.FastWorldLoadingOptimizer;
import com.helium.feature.FullbrightManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class HeliumSharedOptions {

    private static volatile boolean renderingdirty = false;

    public static boolean consumedirty() {
        boolean was = renderingdirty;
        renderingdirty = false;
        return was;
    }

    private HeliumSharedOptions() {}

    public static final int IMPACT_LOW = 0;
    public static final int IMPACT_MEDIUM = 1;
    public static final int IMPACT_HIGH = 2;
    public static final int IMPACT_VARIES = 3;

    public static final int[] DISPLAY_SYNC_HZ = {0, 60, 75, 120, 144, 165, 240, 360, 500, -1};
    public static final int[] MENU_FPS_VALUES = {0, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250, 260};

    public interface Opt {}

    public record BoolOpt(String key, boolean def, Supplier<Boolean> get, Consumer<Boolean> set,
                          int impact, Supplier<Boolean> enabled) implements Opt {
        public BoolOpt(String key, boolean def, Supplier<Boolean> get, Consumer<Boolean> set, int impact) {
            this(key, def, get, set, impact, () -> true);
        }
    }

    public record IntOpt(String key, int def, int min, int max, int step, String suffix,
                         Supplier<Integer> get, Consumer<Integer> set, int impact) implements Opt {}

    public record EnumOpt<E extends Enum<E>>(String key, E def, Class<E> clazz, String namePrefix,
                                              Supplier<E> get, Consumer<E> set, int impact,
                                              Supplier<Boolean> enabled) implements Opt {
        public EnumOpt(String key, E def, Class<E> clazz, String namePrefix,
                       Supplier<E> get, Consumer<E> set, int impact) {
            this(key, def, clazz, namePrefix, get, set, impact, () -> true);
        }
    }

    public record OptGroup(String key, List<Opt> options) {}
    public record OptPage(String key, List<OptGroup> groups) {}

    public static List<OptPage> pages(HeliumConfig c) {
        return List.of(
                renderingPage(c),
                generalPage(c),
                tweaksPage(c),
                overlayPage(c),
                advancedPage(c)
        );
    }

    //--// rendering page

    private static OptPage renderingPage(HeliumConfig c) {
        List<OptGroup> groups = new ArrayList<>();

        groups.add(new OptGroup("helium.group.entity_culling", List.of(
                new BoolOpt("helium.option.entity_culling", true, () -> c.entityCulling, v -> { c.entityCulling = v; renderingdirty = true; }, IMPACT_MEDIUM),
                new IntOpt("helium.option.entity_cull_distance", 64, 16, 128, 8, "helium.suffix.blocks",
                        () -> c.entityCullDistance, v -> { c.entityCullDistance = v; renderingdirty = true; }, IMPACT_MEDIUM)
        )));

        groups.add(new OptGroup("helium.group.block_entity_culling", List.of(
                new BoolOpt("helium.option.block_entity_culling", true, () -> c.blockEntityCulling, v -> { c.blockEntityCulling = v; renderingdirty = true; }, IMPACT_HIGH),
                new IntOpt("helium.option.block_entity_cull_distance", 48, 16, 96, 8, "helium.suffix.blocks",
                        () -> c.blockEntityCullDistance, v -> { c.blockEntityCullDistance = v; renderingdirty = true; }, IMPACT_HIGH)
        )));

        groups.add(new OptGroup("helium.group.particle_culling", List.of(
                new BoolOpt("helium.option.particle_culling", true, () -> c.particleCulling, v -> { c.particleCulling = v; renderingdirty = true; }, IMPACT_MEDIUM),
                new IntOpt("helium.option.particle_cull_distance", 32, 8, 64, 4, "helium.suffix.blocks",
                        () -> c.particleCullDistance, v -> { c.particleCullDistance = v; renderingdirty = true; }, IMPACT_MEDIUM)
        )));

        groups.add(new OptGroup("helium.group.particle_optimization", List.of(
                new BoolOpt("helium.option.particle_limiting", true, () -> c.particleLimiting, v -> { c.particleLimiting = v; renderingdirty = true; }, IMPACT_HIGH),
                new IntOpt("helium.option.max_particles", 1000, 100, 5000, 100, null,
                        () -> c.maxParticles, v -> { c.maxParticles = v; renderingdirty = true; }, IMPACT_HIGH),
                new BoolOpt("helium.option.particle_priority", true, () -> c.particlePriority, v -> { c.particlePriority = v; renderingdirty = true; }, IMPACT_LOW),
                new BoolOpt("helium.option.particle_batching", false, () -> c.particleBatching, v -> { c.particleBatching = v; renderingdirty = true; }, IMPACT_LOW),
                new BoolOpt("helium.option.particle_lod", false, () -> c.particleLOD, v -> { c.particleLOD = v; renderingdirty = true; }, IMPACT_MEDIUM),
                new IntOpt("helium.option.particle_lod_distance", 16, 4, 64, 4, "helium.suffix.blocks", () -> (int) Math.round(c.particleLODDistance), v -> { c.particleLODDistance = v; renderingdirty = true; }, IMPACT_MEDIUM),
                new IntOpt("helium.option.particle_lod_reduction", 30, 0, 100, 5, "helium.suffix.percent", () -> (int) Math.round(c.particleLODReduction * 100.0), v -> { c.particleLODReduction = v / 100.0; renderingdirty = true; }, IMPACT_MEDIUM)
        )));

        groups.add(new OptGroup("helium.group.render_pipeline", List.of(("helium.option.particle_lod", false, () -> c.particleLOD, v -> { c.particleLOD = v; renderingdirty = true; }, IMPACT_MEDIUM)
                , new IntOpt("helium.option.particle_lod_distance", 16, 4, 64, 4, "helium.suffix.blocks", () -> (int) Math.round(c.particleLODDistance), v -> { c.particleLODDistance = v; renderingdirty = true; }, IMPACT_MEDIUM)
                , new IntOpt("helium.option.particle_lod_reduction", 30, 0, 100, 5, "helium.suffix.percent", () -> (int) Math.round(c.particleLODReduction * 100.0), v -> { c.particleLODReduction = v / 100.0; renderingdirty = true; }, IMPACT_MEDIUM)
        )));

        groups.add(new OptGroup("helium.group.render_pipeline", List.of(
                new BoolOpt("helium.option.animation_throttling", true, () -> c.animationThrottling, v -> { c.animationThrottling = v; renderingdirty = true; }, IMPACT_LOW),
                new BoolOpt("helium.option.fast_math", false, () -> c.fastMath, v -> { c.fastMath = v; renderingdirty = true; }, IMPACT_LOW),
                new BoolOpt("helium.option.gl_state_cache", false, () -> c.glStateCache, v -> { c.glStateCache = v; renderingdirty = true; }, IMPACT_VARIES,
                        () -> !HeliumClient.isAndroid()),
                new BoolOpt("helium.option.fast_animations", false, () -> c.fastAnimations, v -> { c.fastAnimations = v; renderingdirty = true; }, IMPACT_MEDIUM),
                new BoolOpt("helium.option.cached_enum_values", false, () -> c.cachedEnumValues, v -> { c.cachedEnumValues = v; renderingdirty = true; }, IMPACT_MEDIUM),
                new BoolOpt("helium.option.accelerated_text", false, () -> c.acceleratedText, v -> c.acceleratedText = v, IMPACT_MEDIUM),
                new BoolOpt("helium.option.shader_uniform_cache", false, () -> c.shaderUniformCache, v -> c.shaderUniformCache = v, IMPACT_MEDIUM)
        )));

        groups.add(new OptGroup("helium.group.caching", List.of(
                new BoolOpt("helium.option.model_cache", false, () -> c.modelCache, v -> { c.modelCache = v; renderingdirty = true; }, IMPACT_MEDIUM),
                new IntOpt("helium.option.model_cache_size", 64, 16, 256, 16, "helium.suffix.mb",
                        () -> c.modelCacheMaxMb, v -> { c.modelCacheMaxMb = v; renderingdirty = true; }, IMPACT_MEDIUM)
        )));

        List<Opt> cullingopts = new ArrayList<>();
        cullingopts.add(new EnumOpt<>(
                "helium.option.leaf_mode",
                com.helium.render.LeafCullingEngine.CullingMode.FAST,
                com.helium.render.LeafCullingEngine.CullingMode.class,
                "helium.option.leaf_mode.",
                () -> {
                    try { return com.helium.render.LeafCullingEngine.CullingMode.valueOf(c.leafCullingMode.toUpperCase()); }
                    catch (Exception e) { return com.helium.render.LeafCullingEngine.CullingMode.FAST; }
                },
                v -> { c.leafCullingMode = v.name(); renderingdirty = true; },
                IMPACT_MEDIUM
        ));
        cullingopts.add(new IntOpt("helium.option.leaf_cull_depth", 2, 1, 4, 1, null,
                () -> c.leafCullingDepth, v -> { c.leafCullingDepth = v; renderingdirty = true; }, IMPACT_MEDIUM));
        cullingopts.add(new BoolOpt("helium.option.mangrove_roots_culling", false,
                () -> c.leafCullingMangroveRoots, v -> { c.leafCullingMangroveRoots = v; renderingdirty = true; }, IMPACT_LOW));
        cullingopts.add(new BoolOpt("helium.option.sign_text_culling", true,
                () -> c.signTextCulling, v -> { c.signTextCulling = v; renderingdirty = true; }, IMPACT_MEDIUM));
        cullingopts.add(new BoolOpt("helium.option.rain_culling", true,
                () -> c.rainCulling, v -> { c.rainCulling = v; renderingdirty = true; }, IMPACT_MEDIUM));
        cullingopts.add(new BoolOpt("helium.option.beacon_beam_culling", true,
                () -> c.beaconBeamCulling, v -> { c.beaconBeamCulling = v; renderingdirty = true; }, IMPACT_LOW));
        cullingopts.add(new BoolOpt("helium.option.painting_culling", true,
                () -> c.paintingCulling, v -> { c.paintingCulling = v; renderingdirty = true; }, IMPACT_LOW));
        cullingopts.add(new BoolOpt("helium.option.item_frame_culling", true,
                () -> c.itemFrameCulling, v -> { c.itemFrameCulling = v; renderingdirty = true; }, IMPACT_MEDIUM));
        cullingopts.add(new BoolOpt("helium.option.item_frame_lod", false,
                () -> c.itemFrameLOD, v -> { c.itemFrameLOD = v; renderingdirty = true; }, IMPACT_MEDIUM));
        cullingopts.add(new IntOpt("helium.option.item_frame_lod_range", 128, 32, 256, 16, "helium.suffix.blocks",
                () -> c.itemFrameLODRange, v -> { c.itemFrameLODRange = v; renderingdirty = true; }, IMPACT_MEDIUM));
        groups.add(new OptGroup("helium.group.culling", cullingopts));

        return new OptPage("helium.page.rendering", groups);
    }

    //--// general page

    private static OptPage generalPage(HeliumConfig c) {
        List<OptGroup> groups = new ArrayList<>();

        groups.add(new OptGroup("helium.group.engine", List.of(
                new BoolOpt("helium.option.memory_optimizations", true, () -> c.memoryOptimizations, v -> c.memoryOptimizations = v, IMPACT_LOW),
                new BoolOpt("helium.option.thread_optimizations", true, () -> c.threadOptimizations, v -> c.threadOptimizations = v, IMPACT_LOW),
                new BoolOpt("helium.option.fast_startup", false, () -> c.fastStartup, v -> c.fastStartup = v, IMPACT_LOW),
                new BoolOpt("helium.option.fast_world_loading", true, () -> c.fastWorldLoading, v -> {
                    c.fastWorldLoading = v;
                    if (v && FastWorldLoadingOptimizer.isInitialized()) FastWorldLoadingOptimizer.enable();
                    else FastWorldLoadingOptimizer.disable();
                }, IMPACT_LOW),
                new BoolOpt("helium.option.reduced_allocations", true, () -> c.reducedAllocations, v -> c.reducedAllocations = v, IMPACT_LOW),
                new BoolOpt("helium.option.network_optimizations", false, () -> c.networkOptimizations, v -> c.networkOptimizations = v, IMPACT_LOW),
                new BoolOpt("helium.option.object_deduplication", true, () -> c.objectDeduplication, v -> c.objectDeduplication = v, IMPACT_MEDIUM)
        )));

        groups.add(new OptGroup("helium.group.visual", List.of(
                new BoolOpt("helium.option.fullbright", false, () -> c.fullbright, v -> {
                    c.fullbright = v;
                    FullbrightManager.setEnabled(v);
                }, IMPACT_LOW),
                new IntOpt("helium.option.fullbright_strength", 10, 0, 10, 1, null,
                        () -> c.fullbrightStrength, v -> {
                            c.fullbrightStrength = v;
                            FullbrightManager.setStrength(v);
                        }, IMPACT_LOW),
                new BoolOpt("helium.option.smooth_scrolling", true, () -> c.smoothScrolling, v -> c.smoothScrolling = v, IMPACT_LOW)
        )));

        List<Opt> windowOpts = new ArrayList<>();
        Supplier<Boolean> winEnabled = DeviceDetector::isWindows;
        windowOpts.add(new BoolOpt("helium.option.window_style", true, () -> c.windowStyle, v -> {
            c.windowStyle = v;
            applywindowstyle();
        }, IMPACT_LOW, winEnabled));
        windowOpts.add(new EnumOpt<>(
                "helium.option.window_material",
                com.helium.platform.DwmEnums.WindowMaterial.TABBED,
                com.helium.platform.DwmEnums.WindowMaterial.class,
                "helium.option.window_material.",
                () -> com.helium.platform.DwmEnums.WindowMaterial.fromString(c.windowMaterial),
                v -> { c.windowMaterial = v.name(); applywindowstyle(); },
                IMPACT_LOW, winEnabled
        ));
        windowOpts.add(new EnumOpt<>(
                "helium.option.window_corner",
                com.helium.platform.DwmEnums.WindowCorner.ROUND,
                com.helium.platform.DwmEnums.WindowCorner.class,
                "helium.option.window_corner.",
                () -> com.helium.platform.DwmEnums.WindowCorner.fromString(c.windowCorner),
                v -> { c.windowCorner = v.name(); applywindowstyle(); },
                IMPACT_LOW, winEnabled
        ));
        groups.add(new OptGroup("helium.group.window", windowOpts));

        return new OptPage("helium.page.general", groups);
    }

    //--// tweaks page

    private static OptPage tweaksPage(HeliumConfig c) {
        List<OptGroup> groups = new ArrayList<>();

        groups.add(new OptGroup("helium.group.qol", List.of(
                new BoolOpt("helium.option.fast_server_ping", true, () -> c.fastServerPing, v -> c.fastServerPing = v, IMPACT_LOW),
                new BoolOpt("helium.option.fast_ip_ping", true, () -> c.fastIpPing, v -> c.fastIpPing = v, IMPACT_LOW),
                new BoolOpt("helium.option.preserve_scroll", true, () -> c.preserveScrollOnRefresh, v -> c.preserveScrollOnRefresh = v, IMPACT_LOW),
                new BoolOpt("helium.option.direct_connect_preview", true, () -> c.directConnectPreview, v -> c.directConnectPreview = v, IMPACT_LOW)
        )));

        groups.add(new OptGroup("helium.group.hotbar", List.of(
                new BoolOpt("helium.option.hotbar_optimizer", true, () -> c.hotbarOptimizer, v -> c.hotbarOptimizer = v, IMPACT_LOW),
                new BoolOpt("helium.option.hotbar_multi_switch", false, () -> c.hotbarMultiSwitch, v -> c.hotbarMultiSwitch = v, IMPACT_LOW),
                new BoolOpt("helium.option.smooth_hotbar", true, () -> c.smoothHotbar, v -> c.smoothHotbar = v, IMPACT_LOW)
        )));

        groups.add(new OptGroup("helium.group.tweaks", List.of(
                new BoolOpt("helium.option.force_skin_parts", true, () -> c.forceSkinParts, v -> c.forceSkinParts = v, IMPACT_LOW),
                new BoolOpt("helium.option.async_pack_reload", true, () -> c.asyncPackReload, v -> c.asyncPackReload = v, IMPACT_LOW),
                new IntOpt("helium.option.menu_framerate_limit", 0, 0, 21, 1, null,
                        () -> {
                            int val = c.menuFramerateLimit;
                            for (int i = 0; i < MENU_FPS_VALUES.length; i++) {
                                if (MENU_FPS_VALUES[i] == val) return i;
                            }
                            return 0;
                        },
                        v -> c.menuFramerateLimit = MENU_FPS_VALUES[Math.min(v, MENU_FPS_VALUES.length - 1)],
                        IMPACT_LOW)
        )));

        return new OptPage("helium.page.tweaks", groups);
    }

    //--// overlay page

    private static OptPage overlayPage(HeliumConfig c) {
        List<OptGroup> groups = new ArrayList<>();

        groups.add(new OptGroup("helium.group.fps_overlay", List.of(
                new BoolOpt("helium.option.fps_overlay", true, () -> c.fpsOverlay, v -> c.fpsOverlay = v, IMPACT_LOW),
                new IntOpt("helium.option.overlay_transparency", 60, 0, 100, 10, "helium.suffix.percent",
                        () -> c.overlayTransparency, v -> c.overlayTransparency = v, IMPACT_LOW)
        )));

        groups.add(new OptGroup("helium.group.overlay_content", List.of(
                new BoolOpt("helium.option.show_fps", true, () -> c.overlayShowFps, v -> c.overlayShowFps = v, IMPACT_LOW),
                new BoolOpt("helium.option.show_fps_stats", false, () -> c.overlayShowFpsMinMaxAvg, v -> c.overlayShowFpsMinMaxAvg = v, IMPACT_LOW),
                new BoolOpt("helium.option.show_memory", false, () -> c.overlayShowMemory, v -> c.overlayShowMemory = v, IMPACT_LOW),
                new BoolOpt("helium.option.show_particles", false, () -> c.overlayShowParticles, v -> c.overlayShowParticles = v, IMPACT_LOW),
                new BoolOpt("helium.option.show_coordinates", false, () -> c.overlayShowCoordinates, v -> c.overlayShowCoordinates = v, IMPACT_LOW),
                new BoolOpt("helium.option.show_biome", false, () -> c.overlayShowBiome, v -> c.overlayShowBiome = v, IMPACT_LOW)
        )));

        return new OptPage("helium.page.overlay", groups);
    }

    //--// advanced page

    private static OptPage advancedPage(HeliumConfig c) {
        List<OptGroup> groups = new ArrayList<>();

        groups.add(new OptGroup("helium.group.experimental", List.of(
                new BoolOpt("helium.option.native_memory", true, () -> c.nativeMemory, v -> c.nativeMemory = v, IMPACT_MEDIUM),
                new IntOpt("helium.option.native_memory_pool_size", 64, 16, 256, 16, "helium.suffix.mb",
                        () -> c.nativeMemoryPoolMb, v -> c.nativeMemoryPoolMb = v, IMPACT_MEDIUM),
                new BoolOpt("helium.option.render_pipelining", false, () -> c.renderPipelining, v -> c.renderPipelining = v, IMPACT_HIGH),
                new BoolOpt("helium.option.simd_math", false, () -> c.simdMath, v -> c.simdMath = v, IMPACT_MEDIUM),
                new BoolOpt("helium.option.async_light_updates", false, () -> c.asyncLightUpdates, v -> c.asyncLightUpdates = v, IMPACT_MEDIUM),
                new BoolOpt("helium.option.packet_batching", false, () -> c.packetBatching, v -> c.packetBatching = v, IMPACT_LOW),
                new BoolOpt("helium.option.temporal_reprojection", false, () -> c.temporalReprojection, v -> c.temporalReprojection = v, IMPACT_HIGH),
                new BoolOpt("helium.option.joml_fast_math", false, () -> c.jomlFastMath, v -> c.jomlFastMath = v, IMPACT_HIGH),
                new BoolOpt("helium.option.gl_context_upgrade", true, () -> c.glContextUpgrade, v -> c.glContextUpgrade = v, IMPACT_HIGH),
                new BoolOpt("helium.option.fast_random", false, () -> c.fastRandom, v -> c.fastRandom = v, IMPACT_MEDIUM),
                new BoolOpt("helium.option.direct_state_access", true, () -> c.directStateAccess, v -> c.directStateAccess = v, IMPACT_MEDIUM),
                new BoolOpt("helium.option.renderbuffer_depth", false, () -> c.renderbufferDepth, v -> c.renderbufferDepth = v, IMPACT_LOW)
        )));

        groups.add(new OptGroup("helium.group.crafting", List.of(
                new BoolOpt("helium.option.one_click_crafting", true, () -> c.oneClickCrafting, v -> c.oneClickCrafting = v, IMPACT_LOW)
        )));

        groups.add(new OptGroup("helium.group.gpu_specific", List.of(
                new BoolOpt("helium.option.nvidia_optimizations", true, () -> c.nvidiaOptimizations, v -> c.nvidiaOptimizations = v, IMPACT_MEDIUM,
                        () -> !com.helium.gpu.GpuDetector.isInitialized() || com.helium.gpu.GpuDetector.isNvidia()),
                new BoolOpt("helium.option.amd_optimizations", true, () -> c.amdOptimizations, v -> c.amdOptimizations = v, IMPACT_MEDIUM,
                        () -> !com.helium.gpu.GpuDetector.isInitialized() || com.helium.gpu.GpuDetector.isAmd()),
                new BoolOpt("helium.option.intel_optimizations", true, () -> c.intelOptimizations, v -> c.intelOptimizations = v, IMPACT_MEDIUM,
                        () -> !com.helium.gpu.GpuDetector.isInitialized() || com.helium.gpu.GpuDetector.isIntel()),
                new BoolOpt("helium.option.adaptive_sync", false, () -> c.adaptiveSync, v -> c.adaptiveSync = v, IMPACT_LOW),
                new IntOpt("helium.option.display_sync_optimization", 9, 0, 9, 1, null,
                        () -> {
                            int val = c.displaySyncRefreshRate;
                            for (int i = 0; i < DISPLAY_SYNC_HZ.length; i++) {
                                if (DISPLAY_SYNC_HZ[i] == val) return i;
                            }
                            return 9;
                        },
                        v -> {
                            c.displaySyncRefreshRate = DISPLAY_SYNC_HZ[Math.min(v, DISPLAY_SYNC_HZ.length - 1)];
                            DisplaySyncOptimizer.reset();
                        }, IMPACT_VARIES)
        )));

        groups.add(new OptGroup("helium.group.performance", List.of(
                new BoolOpt("helium.option.reflex", true, () -> c.enableReflex, v -> c.enableReflex = v, IMPACT_HIGH),
                new BoolOpt("helium.option.fast_blit", false, () -> c.fastFramebufferBlit, v -> c.fastFramebufferBlit = v, IMPACT_MEDIUM),
                new BoolOpt("helium.option.suppress_gl_errors", true, () -> c.suppressOpenGLErrors, v -> c.suppressOpenGLErrors = v, IMPACT_LOW),
                new BoolOpt("helium.option.pose_pooling", true, () -> c.poseStackPooling, v -> c.poseStackPooling = v, IMPACT_MEDIUM),
                new BoolOpt("helium.option.fast_bamboo", false, () -> c.fastBambooLight, v -> c.fastBambooLight = v, IMPACT_LOW),
                new BoolOpt("helium.option.optimized_light", true, () -> c.optimizedLightEngine, v -> c.optimizedLightEngine = v, IMPACT_MEDIUM)
        )));

        groups.add(new OptGroup("helium.group.idle", List.of(
                new BoolOpt("helium.option.inactive_fps", true, () -> c.reduceFpsWhenInactive, v -> c.reduceFpsWhenInactive = v, IMPACT_LOW),
                new IntOpt("helium.option.inactive_fps_limit", 10, 1, 60, 1, "helium.suffix.fps",
                        () -> c.inactiveFpsLimit, v -> c.inactiveFpsLimit = v, IMPACT_LOW)
        )));

        groups.add(new OptGroup("helium.group.misc", List.of(
                new BoolOpt("helium.option.instant_lang", true, () -> c.instantLanguageChange, v -> c.instantLanguageChange = v, IMPACT_LOW)
        )));

        return new OptPage("helium.page.advanced", groups);
    }

    private static void applywindowstyle() {
        try {
            com.helium.platform.DwmApi.applyWindowStyle(false,
                    net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle());
        } catch (Throwable ignored) {}
    }

    public static String formatmenuframerate(int index) {
        int fps = MENU_FPS_VALUES[Math.min(index, MENU_FPS_VALUES.length - 1)];
        if (fps <= 0) return "helium.option.menu_framerate_limit.off";
        return fps + " FPS";
    }

    public static String formatdisplaysync(int index) {
        int hz = DISPLAY_SYNC_HZ[Math.min(index, DISPLAY_SYNC_HZ.length - 1)];
        if (hz == 0) return "helium.option.display_sync_optimization.off";
        if (hz == -1) return "helium.option.display_sync_optimization.auto";
        return hz + " Hz";
    }
}
