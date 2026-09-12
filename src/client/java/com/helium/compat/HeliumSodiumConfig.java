package com.helium.compat;

import com.helium.HeliumClient;
import com.helium.compute.GpuComputeConfig;
import com.helium.config.ExperimentalConfig;
import com.helium.config.HeliumConfig;
import com.helium.config.HeliumSharedOptions;
import com.helium.config.HeliumSharedOptions.*;
import com.helium.util.VersionCompat;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.*;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class HeliumSodiumConfig implements ConfigEntryPoint {
    private static final String NAMESPACE = "helium";
    private static final OptionImpact[] IMPACTS = {OptionImpact.LOW, OptionImpact.MEDIUM, OptionImpact.HIGH, OptionImpact.VARIES};

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        try { registerConfigInternal(builder); }
        catch (Throwable t) { HeliumClient.LOGGER.warn("failed to register helium config in sodium", t); }
    }

    private void registerConfigInternal(ConfigBuilder builder) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null) return;
        GpuComputeConfig gpu = GpuComputeConfig.load();
        ExperimentalConfig experimental = ExperimentalConfig.load();
        StorageEventHandler storage = config::save;

        ModOptionsBuilder mod = builder.registerModOptions(NAMESPACE);
        mod.setName("Helium");
        mod.setIcon(VersionCompat.createIdentifier(NAMESPACE, "textures/icon-only.png"));

        OptionPageBuilder core = builder.createOptionPage();
        core.setName(Text.literal("Helium Core"));
        OptionGroupBuilder coreGroup = builder.createOptionGroup();
        coreGroup.setName(Text.literal("General"));
        addBooleanDirect(builder, coreGroup, storage, "helium_mod_enabled", "Enable Helium", () -> config.modEnabled, true, v -> config.modEnabled = v, "Enable or disable Helium.", OptionImpact.MEDIUM, () -> true);
        core.addOptionGroup(coreGroup);
        mod.addPage(core);

        for (OptPage page : HeliumSharedOptions.pages(config)) {
            OptionPageBuilder sodiumPage = builder.createOptionPage();
            sodiumPage.setName(Text.translatable(page.key()));
            boolean reload = page.key().equals("helium.page.rendering");
            for (OptGroup group : page.groups()) {
                OptionGroupBuilder sodiumGroup = builder.createOptionGroup();
                sodiumGroup.setName(Text.translatable(group.key()));
                for (Opt opt : group.options()) addoption(builder, sodiumGroup, storage, opt, reload);
                sodiumPage.addOptionGroup(sodiumGroup);
            }
            mod.addPage(sodiumPage);
        }

        OptionPageBuilder experimentalPage = builder.createOptionPage();
        experimentalPage.setName(Text.literal("Experimental"));
        OptionGroupBuilder experimentalGroup = builder.createOptionGroup();
        experimentalGroup.setName(Text.literal("Experimental Performance"));
        addBooleanDirect(builder, experimentalGroup, experimental::save, "experimental_fast_startup", "Fast Startup", () -> experimental.fastStartup, false,
                v -> experimental.fastStartup = v, "Preloads Helium hot classes in parallel without running static initializers.", OptionImpact.MEDIUM, () -> true);
        addBooleanDirect(builder, experimentalGroup, experimental::save, "experimental_model_cache", "Model Cache", () -> experimental.modelCache, false,
                v -> experimental.modelCache = v, "Adds a bounded front-cache to BlockState model lookups and clears it on model reload.", OptionImpact.MEDIUM, () -> true);
        addIntegerDirect(builder, experimentalGroup, experimental::save, "experimental_model_cache_mb", "Model Cache Size (MB)", () -> experimental.modelCacheMaxMb, 64, 16, 512, 16,
                v -> experimental.modelCacheMaxMb = v, "Maximum memory budget for the experimental block-model front-cache.", OptionImpact.LOW, false);
        addBooleanDirect(builder, experimentalGroup, experimental::save, "experimental_simd_math", "SIMD Math", () -> experimental.simdMath, false,
                v -> experimental.simdMath = v, "Use the Java Vector API for Helium batch math kernels when available.", OptionImpact.MEDIUM, () -> true);
        addBooleanDirect(builder, experimentalGroup, experimental::save, "experimental_async_light", "Async Light Updates", () -> experimental.asyncLightUpdates, false,
                v -> experimental.asyncLightUpdates = v, "Prepare and coalesce light-update work off-thread; vanilla propagation remains on its owning thread.", OptionImpact.HIGH, () -> true);
        addIntegerDirect(builder, experimentalGroup, experimental::save, "experimental_async_light_batch", "Async Light Batch", () -> experimental.asyncLightMaxPerTick, 64, 8, 256, 8,
                v -> experimental.asyncLightMaxPerTick = v, "Maximum prepared light-update entries handled by the background worker per batch.", OptionImpact.LOW, false);
        addBooleanDirect(builder, experimentalGroup, experimental::save, "experimental_network_optimizations", "Network Optimizations", () -> experimental.networkOptimizations, false,
                v -> experimental.networkOptimizations = v, "Experimental network buffer reuse and maintenance; protocol semantics are unchanged.", OptionImpact.MEDIUM, () -> true);
        addBooleanDirect(builder, experimentalGroup, experimental::save, "experimental_gl_state_cache", "GL State Cache", () -> experimental.glStateCache, false,
                v -> experimental.glStateCache = v, "Experimental GlStateManager state cache. Disabled automatically with ImmediatelyFast.", OptionImpact.HIGH, () -> !HeliumClient.hasImmediatelyFast());
        addBooleanDirect(builder, experimentalGroup, experimental::save, "experimental_packet_batching", "Packet Batching", () -> experimental.packetBatching, false,
                v -> experimental.packetBatching = v, "Coalesces outgoing Netty flushes once per connection tick and can add up to one tick of latency.", OptionImpact.HIGH, () -> true);
        addIntegerDirect(builder, experimentalGroup, experimental::save, "experimental_packet_batch_ticks", "Packet Batch Interval", () -> experimental.packetBatchTicks, 1, 1, 2, 1,
                v -> experimental.packetBatchTicks = v, "Current runtime is capped at one connection tick.", OptionImpact.LOW, false);
        experimentalPage.addOptionGroup(experimentalGroup);
        mod.addPage(experimentalPage);

        OptionPageBuilder compute = builder.createOptionPage();
        compute.setName(Text.literal("Helium Extras"));
        OptionGroupBuilder computeGroup = builder.createOptionGroup();
        computeGroup.setName(Text.literal("GPU Compute / OpenCL"));
        addBooleanDirect(builder, computeGroup, gpu::save, "gpu_compute_enabled", "Enable GPU Compute", () -> gpu.enabled, false, v -> gpu.enabled = v, "Enable the optional OpenCL compute backend.", OptionImpact.HIGH, () -> true);
        addBooleanDirect(builder, computeGroup, gpu::save, "gpu_line_of_sight", "GPU Line of Sight", () -> gpu.lineOfSight, false, v -> gpu.lineOfSight = v, "Use cached asynchronous GPU line-of-sight tests.", OptionImpact.HIGH, () -> gpu.enabled);
        addBooleanDirect(builder, computeGroup, gpu::save, "gpu_pathfinding", "GPU Pathfinding", () -> gpu.pathfinding, false, v -> gpu.pathfinding = v, "Enable the experimental flow-field GPU pathfinding kernel.", OptionImpact.HIGH, () -> gpu.enabled);
        addIntegerDirect(builder, computeGroup, gpu::save, "gpu_grid_size", "GPU Grid Size", () -> gpu.gridSize, 32, 16, 48, 1, v -> gpu.gridSize = v, "World snapshot edge length.", OptionImpact.MEDIUM, false);
        addIntegerDirect(builder, computeGroup, gpu::save, "gpu_refresh_ticks", "GPU Refresh Ticks", () -> gpu.refreshTicks, 2, 1, 10, 1, v -> gpu.refreshTicks = v, "Client ticks for cached LOS reuse.", OptionImpact.MEDIUM, false);
        addIntegerDirect(builder, computeGroup, gpu::save, "gpu_max_batch", "GPU Max Batch", () -> gpu.maxBatch, 1, 1, 8, 1, v -> gpu.maxBatch = v, "Maximum requests in one GPU snapshot.", OptionImpact.MEDIUM, false);
        compute.addOptionGroup(computeGroup);
        mod.addPage(compute);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addoption(ConfigBuilder builder, OptionGroupBuilder group, StorageEventHandler storage, Opt opt, boolean reload) {
        if (opt instanceof BoolOpt b) {
            String id = b.key().replace("helium.option.", "").replace('.', '_');
            BooleanOptionBuilder o = builder.createBooleanOption(VersionCompat.createIdentifier(NAMESPACE, id));
            o.setName(Text.translatable(b.key())).setTooltip(Text.translatable(b.key() + ".tooltip"));
            o.setImpact(IMPACTS[Math.min(b.impact(), IMPACTS.length - 1)]).setDefaultValue(b.def()).setStorageHandler(storage);
            boolean enabled = b.enabled().get();
            o.setEnabled(enabled);
            o.setBinding(enabled ? v -> b.set().accept(v) : v -> {}, enabled ? b.get() : () -> b.def());
            if (reload) o.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD);
            group.addOption(o);
        } else if (opt instanceof IntOpt i) {
            String id = i.key().replace("helium.option.", "").replace('.', '_');
            IntegerOptionBuilder o = builder.createIntegerOption(VersionCompat.createIdentifier(NAMESPACE, id));
            o.setName(Text.translatable(i.key())).setTooltip(Text.translatable(i.key() + ".tooltip"));
            o.setImpact(IMPACTS[Math.min(i.impact(), IMPACTS.length - 1)]).setDefaultValue(i.def()).setRange(i.min(), i.max(), i.step()).setStorageHandler(storage);
            o.setBinding(v -> i.set().accept(v), i.get());
            if (reload) o.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD);
            group.addOption(o);
        } else if (opt instanceof EnumOpt e) {
            String id = e.key().replace("helium.option.", "").replace('.', '_');
            EnumOptionBuilder o = builder.createEnumOption(VersionCompat.createIdentifier(NAMESPACE, id), e.clazz());
            o.setName(Text.translatable(e.key())).setTooltip(Text.translatable(e.key() + ".tooltip"));
            o.setImpact(IMPACTS[Math.min(e.impact(), IMPACTS.length - 1)]).setDefaultValue(e.def()).setStorageHandler(storage);
            boolean enabled = (Boolean) e.enabled().get();
            o.setEnabled(enabled);
            o.setBinding(enabled ? v -> e.set().accept(v) : v -> {}, enabled ? e.get() : () -> e.def());
            if (reload) o.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD);
            group.addOption(o);
        }
    }

    private void addBooleanDirect(ConfigBuilder b, OptionGroupBuilder g, StorageEventHandler s, String id, String name, Supplier<Boolean> getter,
                                  boolean def, Consumer<Boolean> setter, String tooltip, OptionImpact impact, Supplier<Boolean> enabled) {
        BooleanOptionBuilder o = b.createBooleanOption(VersionCompat.createIdentifier(NAMESPACE, id));
        o.setName(Text.literal(name)).setTooltip(Text.literal(tooltip)).setImpact(impact).setDefaultValue(def).setStorageHandler(s);
        boolean active = enabled.get();
        o.setEnabled(active).setBinding(active ? setter : v -> {}, active ? getter : () -> def);
        g.addOption(o);
    }

    private void addIntegerDirect(ConfigBuilder b, OptionGroupBuilder g, StorageEventHandler s, String id, String name, Supplier<Integer> getter,
                                  int def, int min, int max, int step, Consumer<Integer> setter, String tooltip, OptionImpact impact, boolean reload) {
        IntegerOptionBuilder o = b.createIntegerOption(VersionCompat.createIdentifier(NAMESPACE, id));
        o.setName(Text.literal(name)).setTooltip(Text.literal(tooltip)).setImpact(impact).setDefaultValue(def).setRange(min, max, step).setStorageHandler(s).setBinding(setter, getter);
        if (reload) o.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD);
        g.addOption(o);
    }
}
