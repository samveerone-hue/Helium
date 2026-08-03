package com.helium.config;

import com.helium.HeliumClient;
import com.helium.idle.IdleManager;
import com.helium.config.HeliumSharedOptions.*;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
                .setTitle(Component.translatable("helium.name"))
                .setSavingRunnable(() -> {
                    config.save();
                    if (HeliumSharedOptions.consumedirty()) {
                        Minecraft client = Minecraft.getInstance();
                        if (client != null) {
                            HeliumClient.LOGGER.info("rendering config changed, scheduling chunk reload");
                            client.execute(() -> {
                                if (client.levelRenderer != null) {
                                    client.levelRenderer.allChanged();
                                }
                            });
                        }
                    }
                });

        ConfigEntryBuilder eb = builder.entryBuilder();
        List<OptPage> sharedpages = HeliumSharedOptions.pages(config);

        for (OptPage page : sharedpages) {
            ConfigCategory cat = builder.getOrCreateCategory(Component.translatable(page.key()));

            if (page.key().equals("helium.page.general")) {
                cat.addEntry(eb.startBooleanToggle(Component.translatable("helium.config.enable"), config.modEnabled)
                        .setDefaultValue(defaults.modEnabled)
                        .setTooltip(Component.translatable("helium.config.enable.description"))
                        .setSaveConsumer(v -> config.modEnabled = v)
                        .build());
            }

            for (OptGroup group : page.groups()) {
                List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> groupentries = new ArrayList<>();

                for (Opt opt : group.options()) {
                    addsharedentry(eb, groupentries, opt);
                }

                if (page.key().equals("helium.page.general") && group.key().equals("helium.group.engine")) {
                    groupentries.add(eb.startBooleanToggle(Component.translatable("helium.option.auto_pause_on_idle"), config.autoPauseOnIdle)
                            .setDefaultValue(defaults.autoPauseOnIdle)
                            .setTooltip(Component.translatable("helium.option.auto_pause_on_idle.tooltip"))
                            .setSaveConsumer(v -> config.autoPauseOnIdle = v)
                            .build());
                    groupentries.add(eb.startIntSlider(Component.translatable("helium.option.idle_timeout"), config.idleTimeoutSeconds, 10, 300)
                            .setDefaultValue(defaults.idleTimeoutSeconds)
                            .setTooltip(Component.translatable("helium.option.idle_timeout.tooltip"))
                            .setTextGetter(v -> Component.translatable("helium.suffix.seconds", v))
                            .setSaveConsumer(v -> { config.idleTimeoutSeconds = v; if (IdleManager.isInitialized()) IdleManager.setTimeoutSeconds(v); })
                            .build());
                    groupentries.add(eb.startIntSlider(Component.translatable("helium.option.idle_fps_limit"), config.idleFpsLimit, 1, 30)
                            .setDefaultValue(defaults.idleFpsLimit)
                            .setTooltip(Component.translatable("helium.option.idle_fps_limit.tooltip"))
                            .setTextGetter(v -> Component.translatable("helium.suffix.fps", v))
                            .setSaveConsumer(v -> { config.idleFpsLimit = v; if (IdleManager.isInitialized()) IdleManager.setIdleFpsLimit(v); })
                            .build());
                }

                SubCategoryListEntry subcat = eb.startSubCategory(Component.translatable(group.key()), groupentries)
                        .setExpanded(true)
                        .build();
                cat.addEntry(subcat);
            }

            if (page.key().equals("helium.page.general")) {
                List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> devEntries = new ArrayList<>();
                devEntries.add(eb.startBooleanToggle(Component.translatable("helium.config.dev_mode"), config.devMode)
                        .setDefaultValue(defaults.devMode)
                        .setTooltip(Component.translatable("helium.config.dev_mode.tooltip"))
                        .setSaveConsumer(v -> config.devMode = v)
                        .build());
                cat.addEntry(eb.startSubCategory(Component.translatable("helium.config.category.developer"), devEntries)
                        .build());
            }
        }

        ConfigCategory toolsCat = builder.getOrCreateCategory(Component.translatable("helium.config.category.tools"));
        toolsCat.addEntry(eb.startTextDescription(Component.translatable("helium.config.category.tools.tooltip")).build());

        return builder.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addsharedentry(ConfigEntryBuilder eb, List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> entries, Opt opt) {
        if (opt instanceof BoolOpt b) {
            entries.add(eb.startBooleanToggle(Component.translatable(b.key()), b.get().get())
                    .setDefaultValue(b.def())
                    .setTooltip(Component.translatable(b.key() + ".tooltip"))
                    .setSaveConsumer(v -> b.set().accept(v))
                    .build());
        } else if (opt instanceof IntOpt i) {
            if (i.key().contains("display_sync") || i.key().contains("menu_framerate")) {
                boolean displaysync = i.key().contains("display_sync");
                entries.add(eb.startIntSlider(Component.translatable(i.key()), i.get().get(), i.min(), i.max())
                        .setDefaultValue(i.def())
                        .setTooltip(Component.translatable(i.key() + ".tooltip"))
                        .setTextGetter(v -> {
                            String fmt = displaysync ? HeliumSharedOptions.formatdisplaysync(v) : HeliumSharedOptions.formatmenuframerate(v);
                            return fmt.startsWith("helium.") ? Component.translatable(fmt) : Component.literal(fmt);
                        })
                        .setSaveConsumer(v -> i.set().accept(v))
                        .build());
            } else {
                entries.add(eb.startIntSlider(Component.translatable(i.key()), i.get().get(), i.min(), i.max())
                        .setDefaultValue(i.def())
                        .setTooltip(Component.translatable(i.key() + ".tooltip"))
                        .setTextGetter(v -> i.suffix() != null ? Component.translatable(i.suffix(), v) : Component.literal(String.valueOf(v)))
                        .setSaveConsumer(v -> i.set().accept(v))
                        .build());
            }
        } else if (opt instanceof EnumOpt e) {
            entries.add(eb.startEnumSelector(Component.translatable(e.key()), e.clazz(), (Enum) e.get().get())
                    .setDefaultValue((Enum) e.def())
                    .setTooltip(Component.translatable(e.key() + ".tooltip"))
                    .setEnumNameProvider(v -> {
                        String id = v instanceof Enum<?> en ? en.name().toLowerCase() : v.toString().toLowerCase();
                        if (v instanceof com.helium.platform.DwmEnums.WindowMaterial m) id = m.id;
                        if (v instanceof com.helium.platform.DwmEnums.WindowCorner c) id = c.id;
                        return Component.translatable(e.namePrefix() + id);
                    })
                    .setSaveConsumer(v -> ((Consumer) e.set()).accept(v))
                    .build());
        }
    }
}
