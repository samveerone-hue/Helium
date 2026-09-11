package com.helium.config;

import com.helium.HeliumClient;
import com.helium.compute.GpuComputeConfig;
import com.helium.idle.IdleManager;
import com.helium.config.HeliumSharedOptions.*;
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
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class HeliumConfigScreen {

    private static final Path EXPORT_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("helium-export.json");

    private HeliumConfigScreen() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Screen create(Screen parent) {
        HeliumConfig config = HeliumClient.getConfig();
        HeliumConfig defaults = new HeliumConfig();
        GpuComputeConfig gpuCompute = GpuComputeConfig.load();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("helium.name"))
                .setSavingRunnable(() -> {
                    config.save();
                    gpuCompute.save();
                    if (HeliumSharedOptions.consumedirty()) {
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client != null) {
                            HeliumClient.LOGGER.info("rendering config changed, scheduling chunk reload");
                            client.execute(() -> {
                                if (client.worldRenderer != null) {
                                    client.worldRenderer.reload();
                                }
                            });
                        }
                    }
                });

        ConfigEntryBuilder eb = builder.entryBuilder();
        List<OptPage> sharedpages = HeliumSharedOptions.pages(config);

        for (OptPage page : sharedpages) {
            ConfigCategory cat = builder.getOrCreateCategory(Text.translatable(page.key()));

            if (page.key().equals("helium.page.general")) {
                cat.addEntry(eb.startBooleanToggle(Text.translatable("helium.config.enable"), config.modEnabled)
                        .setDefaultValue(defaults.modEnabled)
                        .setTooltip(Text.translatable("helium.config.enable.description"))
                        .setSaveConsumer(v -> config.modEnabled = v)
                        .build());
            }

            for (OptGroup group : page.groups()) {
                List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> groupentries = new ArrayList<>();

                for (Opt opt : group.options()) {
                    addsharedentry(eb, groupentries, opt);
                }

                SubCategoryListEntry subcat = eb.startSubCategory(Text.translatable(group.key()), groupentries)
                        .setExpanded(true)
                        .build();
                cat.addEntry(subcat);
            }

            if (page.key().equals("helium.page.advanced")) {
                List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> gpuEntries = new ArrayList<>();
                gpuEntries.add(eb.startBooleanToggle(Text.literal("Enable GPU Compute"), gpuCompute.enabled)
                        .setDefaultValue(false)
                        .setTooltip(Text.literal("Enable Helium's optional OpenCL compute backend. Disabled by default and requires a usable OpenCL device."))
                        .setSaveConsumer(v -> gpuCompute.enabled = v)
                        .build());
                gpuEntries.add(eb.startBooleanToggle(Text.literal("GPU Line-of-Sight"), gpuCompute.lineOfSight)
                        .setDefaultValue(false)
                        .setTooltip(Text.literal("Use the GPU to asynchronously test entity line-of-sight against a sampled block grid. Results are cached briefly and fall back safely when unavailable."))
                        .setSaveConsumer(v -> gpuCompute.lineOfSight = v)
                        .build());
                gpuEntries.add(eb.startBooleanToggle(Text.literal("GPU Pathfinding"), gpuCompute.pathfinding)
                        .setDefaultValue(false)
                        .setTooltip(Text.literal("Enable the OpenCL flow-field pathfinding kernel for GPU-assisted path calculations. Enabling this does not automatically replace Minecraft entity navigation."))
                        .setSaveConsumer(v -> gpuCompute.pathfinding = v)
                        .build());
                gpuEntries.add(eb.startIntSlider(Text.literal("GPU Grid Size"), gpuCompute.gridSize, 16, 48)
                        .setDefaultValue(32)
                        .setTooltip(Text.literal("Requested world-snapshot size in blocks for GPU compute. Larger grids cover longer rays but increase sampling and GPU memory work."))
                        .setTextGetter(v -> Text.literal(String.valueOf(v)))
                        .setSaveConsumer(v -> gpuCompute.gridSize = v)
                        .build());
                gpuEntries.add(eb.startIntSlider(Text.literal("GPU Refresh Ticks"), gpuCompute.refreshTicks, 1, 10)
                        .setDefaultValue(2)
                        .setTooltip(Text.literal("How many client ticks a cached GPU line-of-sight result remains valid. Lower values react faster to world changes but submit more work."))
                        .setTextGetter(v -> Text.literal(String.valueOf(v)))
                        .setSaveConsumer(v -> gpuCompute.refreshTicks = v)
                        .build());
                gpuEntries.add(eb.startIntSlider(Text.literal("GPU Max Batch"), gpuCompute.maxBatch, 1, 8)
                        .setDefaultValue(1)
                        .setTooltip(Text.literal("Maximum line-of-sight requests processed in one GPU snapshot. One is the safest default because every ray must fit inside the sampled snapshot."))
                        .setTextGetter(v -> Text.literal(String.valueOf(v)))
                        .setSaveConsumer(v -> gpuCompute.maxBatch = v)
                        .build());
                cat.addEntry(eb.startSubCategory(Text.literal("GPU Compute"), gpuEntries)
                        .setExpanded(false)
                        .build());
            }

            if (page.key().equals("helium.page.general")) {
                List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> devEntries = new ArrayList<>();
                devEntries.add(eb.startBooleanToggle(Text.translatable("helium.config.dev_mode"), config.devMode)
                        .setDefaultValue(defaults.devMode)
                        .setTooltip(Text.translatable("helium.config.dev_mode.tooltip"))
                        .setSaveConsumer(v -> config.devMode = v)
                        .build());
                cat.addEntry(eb.startSubCategory(Text.translatable("helium.config.category.developer"), devEntries)
                        .build());
            }
        }

        ConfigCategory toolsCat = builder.getOrCreateCategory(Text.translatable("helium.config.category.tools"));
        toolsCat.addEntry(eb.startTextDescription(Text.translatable("helium.config.category.tools.tooltip")).build());

        return builder.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addsharedentry(ConfigEntryBuilder eb, List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> entries, Opt opt) {
        if (opt instanceof BoolOpt b) {
            entries.add(eb.startBooleanToggle(Text.translatable(b.key()), b.get().get())
                    .setDefaultValue(b.def())
                    .setTooltip(Text.translatable(b.key() + ".tooltip"))
                    .setSaveConsumer(v -> b.set().accept(v))
                    .build());
        } else if (opt instanceof IntOpt i) {
            if (i.key().contains("display_sync") || i.key().contains("menu_framerate")) {
                boolean displaysync = i.key().contains("display_sync");
                entries.add(eb.startIntSlider(Text.translatable(i.key()), i.get().get(), i.min(), i.max())
                        .setDefaultValue(i.def())
                        .setTooltip(Text.translatable(i.key() + ".tooltip"))
                        .setTextGetter(v -> {
                            String fmt = displaysync ? HeliumSharedOptions.formatdisplaysync(v) : HeliumSharedOptions.formatmenuframerate(v);
                            return fmt.startsWith("helium.") ? Text.translatable(fmt) : Text.of(fmt);
                        })
                        .setSaveConsumer(v -> i.set().accept(v))
                        .build());
            } else {
                entries.add(eb.startIntSlider(Text.translatable(i.key()), i.get().get(), i.min(), i.max())
                        .setDefaultValue(i.def())
                        .setTooltip(Text.translatable(i.key() + ".tooltip"))
                        .setTextGetter(v -> i.suffix() != null ? Text.translatable(i.suffix(), v) : Text.of(String.valueOf(v)))
                        .setSaveConsumer(v -> i.set().accept(v))
                        .build());
            }
        } else if (opt instanceof EnumOpt e) {
            entries.add(eb.startEnumSelector(Text.translatable(e.key()), e.clazz(), (Enum) e.get().get())
                    .setDefaultValue((Enum) e.def())
                    .setTooltip(Text.translatable(e.key() + ".tooltip"))
                    .setEnumNameProvider(v -> {
                        String id = v instanceof Enum<?> en ? en.name().toLowerCase() : v.toString().toLowerCase();
                        if (v instanceof com.helium.platform.DwmEnums.WindowMaterial m) id = m.id;
                        if (v instanceof com.helium.platform.DwmEnums.WindowCorner c) id = c.id;
                        return Text.translatable(e.namePrefix() + id);
                    })
                    .setSaveConsumer(v -> ((Consumer) e.set()).accept(v))
                    .build());
        }
    }
}
