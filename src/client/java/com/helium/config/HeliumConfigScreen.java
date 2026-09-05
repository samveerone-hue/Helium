package com.helium.config;

import com.helium.HeliumClient;
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

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("helium.name"))
                .setSavingRunnable(() -> {
                    config.save();
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

                if (page.key().equals("helium.page.rendering") && group.key().equals("helium.group.entity_culling")) {
                    groupentries.add(eb.startBooleanToggle(
                                    Text.translatable("helium.option.rentities_entity_batch_debug"),
                                    config.rentitiesEntityBatchingDebug)
                            .setDefaultValue(defaults.rentitiesEntityBatchingDebug)
                            .setTooltip(Text.translatable("helium.option.rentities_entity_batch_debug.tooltip"))
                            .setSaveConsumer(v -> config.rentitiesEntityBatchingDebug = v)
                            .build());
                    groupentries.add(eb.startBooleanToggle(
                                    Text.translatable("helium.option.rentities_entity_batch_debug_solid"),
                                    config.rentitiesEntityBatchingDebugSolid)
                            .setDefaultValue(defaults.rentitiesEntityBatchingDebugSolid)
                            .setTooltip(Text.translatable("helium.option.rentities_entity_batch_debug_solid.tooltip"))
                            .setSaveConsumer(v -> config.rentitiesEntityBatchingDebugSolid = v)
                            .build());
                    groupentries.add(eb.startBooleanToggle(
                                    Text.translatable("helium.option.rentities_async_render_preparation"),
                                    config.rentitiesAsyncRenderPreparationEnabled)
                            .setDefaultValue(defaults.rentitiesAsyncRenderPreparationEnabled)
                            .setTooltip(Text.translatable("helium.option.rentities_async_render_preparation.tooltip"))
                            .setSaveConsumer(v -> config.rentitiesAsyncRenderPreparationEnabled = v)
                            .build());
                    groupentries.add(eb.startBooleanToggle(
                                    Text.translatable("helium.option.rentities_async_visibility"),
                                    config.rentitiesAsyncVisibilityEnabled)
                            .setDefaultValue(defaults.rentitiesAsyncVisibilityEnabled)
                            .setTooltip(Text.translatable("helium.option.rentities_async_visibility.tooltip"))
                            .setSaveConsumer(v -> config.rentitiesAsyncVisibilityEnabled = v)
                            .build());
                    groupentries.add(eb.startIntSlider(
                                    Text.translatable("helium.option.rentities_visibility_refresh"),
                                    config.rentitiesAsyncVisibilityRefreshFrames, 1, 16)
                            .setDefaultValue(defaults.rentitiesAsyncVisibilityRefreshFrames)
                            .setTooltip(Text.translatable("helium.option.rentities_visibility_refresh.tooltip"))
                            .setTextGetter(v -> Text.translatable("helium.suffix.frames", v))
                            .setSaveConsumer(v -> config.rentitiesAsyncVisibilityRefreshFrames = v)
                            .build());
                    groupentries.add(eb.startIntSlider(
                                    Text.translatable("helium.option.rentities_visibility_age"),
                                    config.rentitiesAsyncVisibilityMaxAgeFrames, 1, 60)
                            .setDefaultValue(defaults.rentitiesAsyncVisibilityMaxAgeFrames)
                            .setTooltip(Text.translatable("helium.option.rentities_visibility_age.tooltip"))
                            .setTextGetter(v -> Text.translatable("helium.suffix.frames", v))
                            .setSaveConsumer(v -> config.rentitiesAsyncVisibilityMaxAgeFrames = v)
                            .build());
                    groupentries.add(eb.startIntSlider(
                                    Text.translatable("helium.option.rentities_visibility_distance"),
                                    (int) Math.round(config.rentitiesAsyncVisibilityMaxDistance), 0, 256)
                            .setDefaultValue((int) Math.round(defaults.rentitiesAsyncVisibilityMaxDistance))
                            .setTooltip(Text.translatable("helium.option.rentities_visibility_distance.tooltip"))
                            .setTextGetter(v -> Text.translatable("helium.suffix.blocks", v))
                            .setSaveConsumer(v -> config.rentitiesAsyncVisibilityMaxDistance = v)
                            .build());

                    groupentries.add(eb.startBooleanToggle(
                                    Text.translatable("helium.option.rentities_entity_batch_whitelist_only"),
                                    config.rentitiesEntityBatchWhitelistOnly)
                            .setDefaultValue(defaults.rentitiesEntityBatchWhitelistOnly)
                            .setTooltip(Text.translatable("helium.option.rentities_entity_batch_whitelist_only.tooltip"))
                            .setSaveConsumer(v -> config.rentitiesEntityBatchWhitelistOnly = v)
                            .build());
                    groupentries.add(eb.startStrField(
                                    Text.translatable("helium.option.rentities_entity_batch_whitelist"),
                                    String.join(", ", config.rentitiesEntityBatchWhitelist))
                            .setDefaultValue(String.join(", ", defaults.rentitiesEntityBatchWhitelist))
                            .setTooltip(Text.translatable("helium.option.rentities_entity_batch_whitelist.tooltip"))
                            .setSaveConsumer(v -> config.rentitiesEntityBatchWhitelist = parseEntityTypeList(v))
                            .build());
                    groupentries.add(eb.startStrField(
                                    Text.translatable("helium.option.rentities_entity_batch_blacklist"),
                                    String.join(", ", config.rentitiesEntityBatchBlacklist))
                            .setDefaultValue(String.join(", ", defaults.rentitiesEntityBatchBlacklist))
                            .setTooltip(Text.translatable("helium.option.rentities_entity_batch_blacklist.tooltip"))
                            .setSaveConsumer(v -> config.rentitiesEntityBatchBlacklist = parseEntityTypeList(v))
                            .build());
                }

                if (page.key().equals("helium.page.general") && group.key().equals("helium.group.engine")) {
                    groupentries.add(eb.startBooleanToggle(Text.translatable("helium.option.auto_pause_on_idle"), config.autoPauseOnIdle)
                            .setDefaultValue(defaults.autoPauseOnIdle)
                            .setTooltip(Text.translatable("helium.option.auto_pause_on_idle.tooltip"))
                            .setSaveConsumer(v -> config.autoPauseOnIdle = v)
                            .build());
                    groupentries.add(eb.startIntSlider(Text.translatable("helium.option.idle_timeout"), config.idleTimeoutSeconds, 10, 300)
                            .setDefaultValue(defaults.idleTimeoutSeconds)
                            .setTooltip(Text.translatable("helium.option.idle_timeout.tooltip"))
                            .setTextGetter(v -> Text.translatable("helium.suffix.seconds", v))
                            .setSaveConsumer(v -> { config.idleTimeoutSeconds = v; if (IdleManager.isInitialized()) IdleManager.setTimeoutSeconds(v); })
                            .build());
                    groupentries.add(eb.startIntSlider(Text.translatable("helium.option.idle_fps_limit"), config.idleFpsLimit, 1, 30)
                            .setDefaultValue(defaults.idleFpsLimit)
                            .setTooltip(Text.translatable("helium.option.idle_fps_limit.tooltip"))
                            .setTextGetter(v -> Text.translatable("helium.suffix.fps", v))
                            .setSaveConsumer(v -> { config.idleFpsLimit = v; if (IdleManager.isInitialized()) IdleManager.setIdleFpsLimit(v); })
                            .build());
                }

                SubCategoryListEntry subcat = eb.startSubCategory(Text.translatable(group.key()), groupentries)
                        .setExpanded(true)
                        .build();
                cat.addEntry(subcat);
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

    private static List<String> parseEntityTypeList(String value) {
        if (value == null || value.isBlank()) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (String raw : value.split(",")) {
            String id = raw.trim();
            if (!id.isEmpty() && id.length() <= 128) result.add(id);
        }
        return result;
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
