package com.helium.config;

import com.helium.HeliumClient;
import com.helium.compute.GpuComputeConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class HeliumConfigScreen {
    private static final Path EXPORT_PATH = FabricLoader.getInstance().getConfigDir().resolve("helium-export.json");

    private HeliumConfigScreen() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Screen create(Screen parent) {
        HeliumConfig config = HeliumClient.getConfig();
        HeliumConfig defaults = new HeliumConfig();
        GpuComputeConfig gpuCompute = GpuComputeConfig.load();
        ExperimentalConfig experimental = ExperimentalConfig.load();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("helium.name"))
                .setSavingRunnable(() -> {
                    config.save();
                    gpuCompute.save();
                    experimental.save();
                    if (HeliumSharedOptions.consumedirty()) {
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client != null) client.execute(() -> {
                            if (client.worldRenderer != null) client.worldRenderer.reload();
                        });
                    }
                });

        ConfigEntryBuilder eb = builder.entryBuilder();
        for (OptPage page : HeliumSharedOptions.pages(config)) {
            ConfigCategory cat = builder.getOrCreateCategory(Text.translatable(page.key()));
            if (page.key().equals("helium.page.general")) {
                cat.addEntry(eb.startBooleanToggle(Text.translatable("helium.config.enable"), config.modEnabled)
                        .setDefaultValue(defaults.modEnabled)
                        .setTooltip(Text.translatable("helium.config.enable.description"))
                        .setSaveConsumer(v -> config.modEnabled = v).build());
            }
            for (OptGroup group : page.groups()) {
                List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> entries = new ArrayList<>();
                for (Opt opt : group.options()) addsharedentry(eb, entries, opt);
                cat.addEntry(eb.startSubCategory(Text.translatable(group.key()), entries).setExpanded(true).build());
            }
            if (page.key().equals("helium.page.advanced")) {
                List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> gpu = new ArrayList<>();
                gpu.add(eb.startBooleanToggle(Text.literal("Enable GPU Compute"), gpuCompute.enabled).setDefaultValue(false)
                        .setTooltip(Text.literal("Enable the optional OpenCL compute backend. Requires a usable OpenCL device."))
                        .setSaveConsumer(v -> gpuCompute.enabled = v).build());
                gpu.add(eb.startBooleanToggle(Text.literal("GPU Line-of-Sight"), gpuCompute.lineOfSight).setDefaultValue(false)
                        .setTooltip(Text.literal("Asynchronously test cached entity line-of-sight against a sampled block grid."))
                        .setSaveConsumer(v -> gpuCompute.lineOfSight = v).build());
                gpu.add(eb.startBooleanToggle(Text.literal("GPU Pathfinding"), gpuCompute.pathfinding).setDefaultValue(false)
                        .setTooltip(Text.literal("Enable the experimental flow-field GPU pathfinding kernel. It does not replace Minecraft navigation."))
                        .setSaveConsumer(v -> gpuCompute.pathfinding = v).build());
                gpu.add(eb.startIntSlider(Text.literal("GPU Grid Size"), gpuCompute.gridSize, 16, 48).setDefaultValue(32)
                        .setSaveConsumer(v -> gpuCompute.gridSize = v).build());
                gpu.add(eb.startIntSlider(Text.literal("GPU Refresh Ticks"), gpuCompute.refreshTicks, 1, 10).setDefaultValue(2)
                        .setSaveConsumer(v -> gpuCompute.refreshTicks = v).build());
                gpu.add(eb.startIntSlider(Text.literal("GPU Max Batch"), gpuCompute.maxBatch, 1, 8).setDefaultValue(1)
                        .setSaveConsumer(v -> gpuCompute.maxBatch = v).build());
                cat.addEntry(eb.startSubCategory(Text.literal("GPU Compute"), gpu).setExpanded(false).build());
            }
        }

        ConfigCategory experimentalCat = builder.getOrCreateCategory(Text.literal("Experimental"));
        List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> network = new ArrayList<>();
        network.add(eb.startBooleanToggle(Text.literal("Network Optimizations"), experimental.networkOptimizations)
                .setDefaultValue(false)
                .setTooltip(Text.literal("Experimental network buffer reuse and maintenance. This does not alter protocol semantics."))
                .setSaveConsumer(v -> experimental.networkOptimizations = v).build());
        network.add(eb.startBooleanToggle(Text.literal("GL State Cache"), experimental.glStateCache)
                .setDefaultValue(false)
                .setTooltip(Text.literal("Experimental GlStateManager state-cache layer. Automatically disabled when ImmediatelyFast is detected."))
                .setSaveConsumer(v -> experimental.glStateCache = v).build());
        network.add(eb.startBooleanToggle(Text.literal("Packet Batching"), experimental.packetBatching)
                .setDefaultValue(false)
                .setTooltip(Text.literal("Coalesce outgoing Netty flushes once per connection tick. Can add up to one tick of network latency; disabled by default."))
                .setSaveConsumer(v -> experimental.packetBatching = v).build());
        network.add(eb.startIntSlider(Text.literal("Packet Batch Interval"), experimental.packetBatchTicks, 1, 2)
                .setDefaultValue(1)
                .setTooltip(Text.literal("Reserved experimental batching interval. Current implementation is capped at one connection tick."))
                .setSaveConsumer(v -> experimental.packetBatchTicks = v).build());
        experimentalCat.addEntry(eb.startSubCategory(Text.literal("Experimental Performance"), network).setExpanded(false).build());

        return builder.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addsharedentry(ConfigEntryBuilder eb, List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> entries, Opt opt) {
        if (opt instanceof BoolOpt b) {
            entries.add(eb.startBooleanToggle(Text.translatable(b.key()), b.get().get())
                    .setDefaultValue(b.def()).setTooltip(Text.translatable(b.key() + ".tooltip"))
                    .setSaveConsumer(v -> b.set().accept(v)).build());
        } else if (opt instanceof IntOpt i) {
            entries.add(eb.startIntSlider(Text.translatable(i.key()), i.get().get(), i.min(), i.max())
                    .setDefaultValue(i.def())
                    .setTooltip(Text.translatable(i.key() + ".tooltip"))
                    .setSaveConsumer(v -> i.set().accept(v)).build());
        } else if (opt instanceof EnumOpt e) {
            entries.add(eb.startEnumSelector(Text.translatable(e.key()), e.clazz(), (Enum) e.get().get())
                    .setDefaultValue((Enum) e.def()).setTooltip(Text.translatable(e.key() + ".tooltip"))
                    .setSaveConsumer(v -> ((java.util.function.Consumer) e.set()).accept(v)).build());
        }
    }
}
