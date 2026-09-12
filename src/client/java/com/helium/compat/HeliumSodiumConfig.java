package com.helium.compat;

import com.helium.HeliumClient;
import com.helium.compute.GpuComputeConfig;
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
    private static final OptionImpact[] IMPACTS = {
            OptionImpact.LOW, OptionImpact.MEDIUM, OptionImpact.HIGH, OptionImpact.VARIES
    };

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        try {
            registerConfigInternal(builder);
        } catch (Throwable t) {
            HeliumClient.LOGGER.warn("failed to register helium config in sodium - sodium api may be incompatible", t);
        }
    }

    private void registerConfigInternal(ConfigBuilder builder) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null) return;

        GpuComputeConfig gpuCompute = GpuComputeConfig.load();
        StorageEventHandler storage = config::save;

        ModOptionsBuilder mod = builder.registerModOptions(NAMESPACE);
        mod.setName("Helium");
        mod.setIcon(VersionCompat.createIdentifier(NAMESPACE, "textures/icon-only.png"));

        OptionPageBuilder corePage = builder.createOptionPage();
        corePage.setName(Text.literal("Helium Core"));
        OptionGroupBuilder coreGroup = builder.createOptionGroup();
        coreGroup.setName(Text.literal("General"));
        addBooleanDirect(builder, coreGroup, storage,
                "helium_mod_enabled", "Enable Helium", () -> config.modEnabled, true,
                v -> config.modEnabled = v,
                "Enable or disable Helium. Restart Minecraft after changing this setting.", OptionImpact.MEDIUM, () -> true, false);
        addBooleanDirect(builder, coreGroup, storage,
                "helium_dev_mode", "Developer Mode", () -> config.devMode, false,
                v -> config.devMode = v,
                "Enable developer-mode optimizations and diagnostics. Restart Minecraft after changing this setting.", OptionImpact.VARIES, () -> true, false);
        corePage.addOptionGroup(coreGroup);
        mod.addPage(corePage);

        List<OptPage> pages = HeliumSharedOptions.pages(config);
        for (OptPage page : pages) {
            OptionPageBuilder sodiumPage = builder.createOptionPage();
            sodiumPage.setName(Text.translatable(page.key()));
            boolean needsReload = page.key().equals("helium.page.rendering");

            for (OptGroup group : page.groups()) {
                OptionGroupBuilder sodiumGroup = builder.createOptionGroup();
                sodiumGroup.setName(Text.translatable(group.key()));
                for (Opt opt : group.options()) {
                    addoption(builder, sodiumGroup, storage, opt, needsReload);
                }
                sodiumPage.addOptionGroup(sodiumGroup);
            }
            mod.addPage(sodiumPage);
        }

        OptionPageBuilder extraPage = builder.createOptionPage();
        extraPage.setName(Text.literal("Helium Extras"));

        OptionGroupBuilder extraGroup = builder.createOptionGroup();
        extraGroup.setName(Text.literal("Additional settings"));
        addIntegerDirect(builder, extraGroup, storage,
                "leaf_random_rejection", "Leaf Random Rejection",
                () -> (int) Math.round(config.leafCullingRandomRejection * 100.0f), 20, 0, 100, 5,
                v -> config.leafCullingRandomRejection = v / 100.0f,
                "Percentage rejection used by RANDOM leaf culling.", OptionImpact.MEDIUM, false);
        addBooleanDirect(builder, extraGroup, storage,
                "reflex_debug", "Reflex Debug", () -> config.reflexDebug, false,
                v -> config.reflexDebug = v,
                "Enable NVIDIA Reflex diagnostic logging.", OptionImpact.LOW, () -> true, false);
        extraPage.addOptionGroup(extraGroup);

        OptionGroupBuilder computeGroup = builder.createOptionGroup();
        computeGroup.setName(Text.literal("GPU Compute / OpenCL"));
        addBooleanDirect(builder, computeGroup, () -> gpuCompute.save(),
                "gpu_compute_enabled", "Enable GPU Compute", () -> gpuCompute.enabled, false,
                v -> gpuCompute.enabled = v,
                "Enable the optional OpenCL compute backend. Requires a usable OpenCL driver/device.", OptionImpact.HIGH, () -> true, false);
        addBooleanDirect(builder, computeGroup, () -> gpuCompute.save(),
                "gpu_line_of_sight", "GPU Line of Sight", () -> gpuCompute.lineOfSight, false,
                v -> gpuCompute.lineOfSight = v,
                "Use OpenCL for cached entity line-of-sight tests. Falls back safely when unavailable.", OptionImpact.HIGH, () -> gpuCompute.enabled, false);
        addBooleanDirect(builder, computeGroup, () -> gpuCompute.save(),
                "gpu_pathfinding", "GPU Pathfinding", () -> gpuCompute.pathfinding, false,
                v -> gpuCompute.pathfinding = v,
                "Enable the OpenCL flow-field pathfinding kernel. This does not replace Minecraft navigation.", OptionImpact.HIGH, () -> gpuCompute.enabled, false);
        addIntegerDirect(builder, computeGroup, () -> gpuCompute.save(),
                "gpu_grid_size", "GPU Grid Size", () -> gpuCompute.gridSize, 32, 16, 48, 1,
                v -> gpuCompute.gridSize = v,
                "World-snapshot cube edge length used by GPU compute.", OptionImpact.MEDIUM, false);
        addIntegerDirect(builder, computeGroup, () -> gpuCompute.save(),
                "gpu_refresh_ticks", "GPU Refresh Ticks", () -> gpuCompute.refreshTicks, 2, 1, 10, 1,
                v -> gpuCompute.refreshTicks = v,
                "Client ticks for reusing cached GPU line-of-sight results.", OptionImpact.MEDIUM, false);
        addIntegerDirect(builder, computeGroup, () -> gpuCompute.save(),
                "gpu_max_batch", "GPU Max Batch", () -> gpuCompute.maxBatch, 1, 1, 8, 1,
                v -> gpuCompute.maxBatch = v,
                "Maximum line-of-sight requests submitted in one GPU snapshot.", OptionImpact.MEDIUM, false);
        extraPage.addOptionGroup(computeGroup);
        mod.addPage(extraPage);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addoption(ConfigBuilder builder, OptionGroupBuilder group, StorageEventHandler storage,
                            Opt opt, boolean needsReload) {
        if (opt instanceof BoolOpt b) {
            String id = b.key().replace("helium.option.", "").replace(".", "_");
            BooleanOptionBuilder o = builder.createBooleanOption(VersionCompat.createIdentifier(NAMESPACE, id));
            o.setName(Text.translatable(b.key()));
            o.setTooltip(Text.translatable(b.key() + ".tooltip"));
            o.setImpact(IMPACTS[Math.min(b.impact(), IMPACTS.length - 1)]);
            o.setDefaultValue(b.def());
            o.setStorageHandler(storage);
            o.setEnabled(b.enabled().get());
            if (needsReload) o.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD);
            if (!b.enabled().get()) {
                o.setBinding(v -> {}, () -> b.def());
            } else {
                o.setBinding(v -> b.set().accept(v), b.get());
            }
            group.addOption(o);
        } else if (opt instanceof IntOpt i) {
            String id = i.key().replace("helium.option.", "").replace(".", "_");
            IntegerOptionBuilder o = builder.createIntegerOption(VersionCompat.createIdentifier(NAMESPACE, id));
            o.setName(Text.translatable(i.key()));
            o.setTooltip(Text.translatable(i.key() + ".tooltip"));
            o.setImpact(IMPACTS[Math.min(i.impact(), IMPACTS.length - 1)]);
            o.setDefaultValue(i.def());
            o.setRange(i.min(), i.max(), i.step());
            o.setStorageHandler(storage);
            o.setBinding(v -> i.set().accept(v), i.get());
            if (needsReload) o.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD);
            if (i.key().contains("display_sync")) {
                o.setValueFormatter(v -> {
                    String fmt = HeliumSharedOptions.formatdisplaysync(v);
                    return fmt.startsWith("helium.") ? Text.translatable(fmt) : Text.of(fmt);
                });
            } else if (i.key().contains("menu_framerate")) {
                o.setValueFormatter(v -> {
                    String fmt = HeliumSharedOptions.formatmenuframerate(v);
                    return fmt.startsWith("helium.") ? Text.translatable(fmt) : Text.of(fmt);
                });
            } else if (i.suffix() != null) {
                o.setValueFormatter(v -> Text.translatable(i.suffix(), v));
            } else {
                o.setValueFormatter(v -> Text.of(String.valueOf(v)));
            }
            group.addOption(o);
        } else if (opt instanceof EnumOpt e) {
            String id = e.key().replace("helium.option.", "").replace(".", "_");
            EnumOptionBuilder o = builder.createEnumOption(VersionCompat.createIdentifier(NAMESPACE, id), e.clazz());
            o.setName(Text.translatable(e.key()));
            o.setTooltip(Text.translatable(e.key() + ".tooltip"));
            o.setImpact(IMPACTS[Math.min(e.impact(), IMPACTS.length - 1)]);
            o.setDefaultValue(e.def());
            o.setElementNameProvider(v -> {
                String enumid = v instanceof Enum<?> en ? en.name().toLowerCase() : v.toString().toLowerCase();
                if (v instanceof com.helium.platform.DwmEnums.WindowMaterial m) enumid = m.id;
                if (v instanceof com.helium.platform.DwmEnums.WindowCorner c) enumid = c.id;
                return Text.translatable(e.namePrefix() + enumid);
            });
            o.setStorageHandler(storage);
            if (needsReload) o.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD);
            boolean enabled = (Boolean) e.enabled().get();
            o.setEnabled(enabled);
            o.setBinding(enabled ? v -> e.set().accept(v) : v -> {}, enabled ? e.get() : () -> e.def());
            group.addOption(o);
        }
    }

    private void addBooleanDirect(ConfigBuilder builder, OptionGroupBuilder group, StorageEventHandler storage,
                                  String id, String name, Supplier<Boolean> getter, boolean def,
                                  Consumer<Boolean> setter, String tooltip, OptionImpact impact,
                                  Supplier<Boolean> enabled, boolean ignored) {
        BooleanOptionBuilder o = builder.createBooleanOption(VersionCompat.createIdentifier(NAMESPACE, id));
        o.setName(Text.literal(name));
        o.setTooltip(Text.literal(tooltip));
        o.setImpact(impact);
        o.setDefaultValue(def);
        o.setStorageHandler(storage);
        boolean active = enabled.get();
        o.setEnabled(active);
        o.setBinding(active ? setter : v -> {}, active ? getter : () -> def);
        group.addOption(o);
    }

    private void addIntegerDirect(ConfigBuilder builder, OptionGroupBuilder group, StorageEventHandler storage,
                                  String id, String name, Supplier<Integer> getter, int def,
                                  int min, int max, int step, Consumer<Integer> setter,
                                  String tooltip, OptionImpact impact, boolean needsReload) {
        IntegerOptionBuilder o = builder.createIntegerOption(VersionCompat.createIdentifier(NAMESPACE, id));
        o.setName(Text.literal(name));
        o.setTooltip(Text.literal(tooltip));
        o.setImpact(impact);
        o.setDefaultValue(def);
        o.setRange(min, max, step);
        o.setStorageHandler(storage);
        o.setBinding(setter, getter);
        if (needsReload) o.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD);
        group.addOption(o);
    }
}
