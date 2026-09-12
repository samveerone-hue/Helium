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
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Sodium-safe Helium settings page.  Sodium 0.8.x validates integer/enum
 * controls during registration; the previous all-types adapter could abort
 * after the first integer option and leave only the master Helium toggle.
 * This entrypoint intentionally exposes feature switches only. Numeric and
 * enum tuning remains available through Helium's full ModMenu screen.
 */
public final class HeliumSodiumBooleanConfig implements ConfigEntryPoint {
    private static final String NAMESPACE = "helium";
    private static final OptionImpact[] IMPACTS = {
            OptionImpact.LOW,
            OptionImpact.MEDIUM,
            OptionImpact.HIGH,
            OptionImpact.VARIES
    };

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        try {
            HeliumConfig config = HeliumClient.getConfig();
            if (config == null) return;

            ModOptionsBuilder mod = builder.registerModOptions(NAMESPACE);
            mod.setName("Helium");
            mod.setIcon(VersionCompat.createIdentifier(NAMESPACE, "textures/icon-only.png"));

            OptionPageBuilder core = builder.createOptionPage();
            core.setName(Text.literal("Helium Core"));
            OptionGroupBuilder coreGroup = builder.createOptionGroup();
            coreGroup.setName(Text.literal("General"));
            addBoolean(builder, coreGroup, config::save, "helium_mod_enabled", "Enable Helium",
                    () -> config.modEnabled, true, v -> config.modEnabled = v,
                    "Enable or disable Helium.", OptionImpact.MEDIUM, () -> true, false);
            core.addOptionGroup(coreGroup);
            mod.addPage(core);

            for (OptPage page : HeliumSharedOptions.pages(config)) {
                OptionPageBuilder sodiumPage = builder.createOptionPage();
                sodiumPage.setName(Text.translatable(page.key()));
                boolean reload = page.key().equals("helium.page.rendering");

                for (OptGroup sourceGroup : page.groups()) {
                    OptionGroupBuilder group = builder.createOptionGroup();
                    group.setName(Text.translatable(sourceGroup.key()));
                    for (Opt opt : sourceGroup.options()) {
                        if (opt instanceof BoolOpt b) {
                            boolean enabled = b.enabled().get();
                            addBoolean(builder, group, config::save,
                                    b.key().replace("helium.option.", "").replace('.', '_'),
                                    b.key(),
                                    enabled ? b.get() : () -> b.def(),
                                    b.def(),
                                    enabled ? b.set() : v -> {},
                                    b.key() + ".tooltip",
                                    IMPACTS[Math.min(b.impact(), IMPACTS.length - 1)],
                                    b.enabled(),
                                    reload);
                        }
                    }
                    sodiumPage.addOptionGroup(group);
                }
                mod.addPage(sodiumPage);
            }

            OptionPageBuilder experimentalPage = builder.createOptionPage();
            experimentalPage.setName(Text.literal("Experimental"));
            OptionGroupBuilder experimentalGroup = builder.createOptionGroup();
            experimentalGroup.setName(Text.literal("Experimental Performance"));
            ExperimentalConfig experimental = ExperimentalConfig.load();
            addBoolean(builder, experimentalGroup, experimental::save, "experimental_fast_startup", "Fast Startup",
                    () -> experimental.fastStartup, false, v -> experimental.fastStartup = v,
                    "Preloads Helium hot classes in parallel without running static initializers.", OptionImpact.MEDIUM, () -> true, false);
            addBoolean(builder, experimentalGroup, experimental::save, "experimental_model_cache", "Model Cache",
                    () -> experimental.modelCache, false, v -> experimental.modelCache = v,
                    "Adds a bounded front-cache to BlockState model lookups and clears it on model reload.", OptionImpact.MEDIUM, () -> true, false);
            addBoolean(builder, experimentalGroup, experimental::save, "experimental_simd_math", "SIMD Math",
                    () -> experimental.simdMath, false, v -> experimental.simdMath = v,
                    "Use the Java Vector API for Helium batch math kernels when available.", OptionImpact.MEDIUM, () -> true, false);
            addBoolean(builder, experimentalGroup, experimental::save, "experimental_async_light", "Async Light Updates",
                    () -> experimental.asyncLightUpdates, false, v -> experimental.asyncLightUpdates = v,
                    "Prepare and coalesce light-update work off-thread; vanilla propagation remains on its owning thread.", OptionImpact.HIGH, () -> true, false);
            addBoolean(builder, experimentalGroup, experimental::save, "experimental_network_optimizations", "Network Optimizations",
                    () -> experimental.networkOptimizations, false, v -> experimental.networkOptimizations = v,
                    "Experimental network buffer reuse and maintenance; protocol semantics are unchanged.", OptionImpact.MEDIUM, () -> true, false);
            addBoolean(builder, experimentalGroup, experimental::save, "experimental_gl_state_cache", "GL State Cache",
                    () -> experimental.glStateCache, false, v -> experimental.glStateCache = v,
                    "Experimental GL state cache. Disabled automatically with ImmediatelyFast.", OptionImpact.HIGH,
                    () -> !HeliumClient.hasImmediatelyFast(), false);
            addBoolean(builder, experimentalGroup, experimental::save, "experimental_packet_batching", "Packet Batching",
                    () -> experimental.packetBatching, false, v -> experimental.packetBatching = v,
                    "Coalesces outgoing Netty flushes once per connection tick and can add up to one tick of latency.", OptionImpact.HIGH, () -> true, false);
            experimentalPage.addOptionGroup(experimentalGroup);
            mod.addPage(experimentalPage);

            OptionPageBuilder computePage = builder.createOptionPage();
            computePage.setName(Text.literal("Helium Extras"));
            OptionGroupBuilder computeGroup = builder.createOptionGroup();
            computeGroup.setName(Text.literal("GPU Compute / OpenCL"));
            GpuComputeConfig gpu = GpuComputeConfig.load();
            addBoolean(builder, computeGroup, gpu::save, "gpu_compute_enabled", "Enable GPU Compute",
                    () -> gpu.enabled, false, v -> gpu.enabled = v,
                    "Enable the optional OpenCL compute backend.", OptionImpact.HIGH, () -> true, false);
            addBoolean(builder, computeGroup, gpu::save, "gpu_line_of_sight", "GPU Line of Sight",
                    () -> gpu.lineOfSight, false, v -> gpu.lineOfSight = v,
                    "Use cached asynchronous GPU line-of-sight tests.", OptionImpact.HIGH, () -> gpu.enabled, false);
            addBoolean(builder, computeGroup, gpu::save, "gpu_pathfinding", "GPU Pathfinding",
                    () -> gpu.pathfinding, false, v -> gpu.pathfinding = v,
                    "Enable the experimental flow-field GPU pathfinding kernel.", OptionImpact.HIGH, () -> gpu.enabled, false);
            computePage.addOptionGroup(computeGroup);
            mod.addPage(computePage);
        } catch (Throwable t) {
            HeliumClient.LOGGER.warn("failed to register Helium feature toggles in Sodium", t);
        }
    }

    private static void addBoolean(ConfigBuilder builder, OptionGroupBuilder group, StorageEventHandler storage,
                                   String id, String name, Supplier<Boolean> getter, boolean def,
                                   Consumer<Boolean> setter, String tooltip, OptionImpact impact,
                                   Supplier<Boolean> enabled, boolean reload) {
        BooleanOptionBuilder option = builder.createBooleanOption(VersionCompat.createIdentifier(NAMESPACE, id));
        option.setName(name.startsWith("helium.option.") ? Text.translatable(name) : Text.literal(name));
        option.setTooltip(tooltip.contains("helium.option.")
                ? Text.translatable(tooltip)
                : Text.literal(tooltip));
        option.setImpact(impact).setDefaultValue(def).setStorageHandler(storage);
        boolean active = enabled.get();
        option.setEnabled(active);
        option.setBinding(active ? setter : v -> {}, active ? getter : () -> def);
        if (reload) option.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD);
        group.addOption(option);
    }
}
