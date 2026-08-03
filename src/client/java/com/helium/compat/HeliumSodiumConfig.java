package com.helium.compat;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.config.HeliumSharedOptions;
import com.helium.config.HeliumSharedOptions.*;
import com.helium.util.VersionCompat;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.*;
import net.minecraft.network.chat.Component;

import java.util.List;

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

        StorageEventHandler storage = config::save;

        ModOptionsBuilder mod = builder.registerModOptions(NAMESPACE);
        mod.setName("Helium");
        mod.setIcon(VersionCompat.createIdentifier(NAMESPACE, "textures/icon-only.png"));

        List<OptPage> pages = HeliumSharedOptions.pages(config);

        for (OptPage page : pages) {
            OptionPageBuilder sodiumpage = builder.createOptionPage();
            sodiumpage.setName(Component.translatable(page.key()));
            boolean needsreload = page.key().equals("helium.page.rendering");

            for (OptGroup group : page.groups()) {
                OptionGroupBuilder sodiumgroup = builder.createOptionGroup();
                sodiumgroup.setName(Component.translatable(group.key()));

                for (Opt opt : group.options()) {
                    addoption(builder, sodiumgroup, storage, opt, needsreload);
                }

                sodiumpage.addOptionGroup(sodiumgroup);
            }

            mod.addPage(sodiumpage);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addoption(ConfigBuilder builder, OptionGroupBuilder group, StorageEventHandler storage, Opt opt, boolean needsreload) {
        if (opt instanceof BoolOpt b) {
            String id = b.key().replace("helium.option.", "").replace(".", "_");
            BooleanOptionBuilder o = builder.createBooleanOption(VersionCompat.createIdentifier(NAMESPACE, id));
            o.setName(Component.translatable(b.key()));
            o.setTooltip(Component.translatable(b.key() + ".tooltip"));
            o.setImpact(IMPACTS[Math.min(b.impact(), IMPACTS.length - 1)]);
            o.setDefaultValue(b.def());
            o.setStorageHandler(storage);
            o.setEnabled(b.enabled().get());
            if (needsreload) o.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD);
            if (!b.enabled().get()) {
                o.setBinding(v -> {}, () -> b.def());
            } else {
                o.setBinding(v -> b.set().accept(v), () -> b.get().get());
            }
            group.addOption(o);

        } else if (opt instanceof IntOpt i) {
            String id = i.key().replace("helium.option.", "").replace(".", "_");
            IntegerOptionBuilder o = builder.createIntegerOption(VersionCompat.createIdentifier(NAMESPACE, id));
            o.setName(Component.translatable(i.key()));
            o.setTooltip(Component.translatable(i.key() + ".tooltip"));
            o.setImpact(IMPACTS[Math.min(i.impact(), IMPACTS.length - 1)]);
            o.setDefaultValue(i.def());
            o.setRange(i.min(), i.max(), i.step());
            o.setStorageHandler(storage);
            o.setBinding(v -> i.set().accept(v), () -> i.get().get());
            if (needsreload) o.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD);

            if (i.key().contains("display_sync")) {
                o.setValueFormatter(v -> {
                    String fmt = HeliumSharedOptions.formatdisplaysync(v);
                    return fmt.startsWith("helium.") ? Component.translatable(fmt) : Component.literal(fmt);
                });
            } else if (i.key().contains("menu_framerate")) {
                o.setValueFormatter(v -> {
                    String fmt = HeliumSharedOptions.formatmenuframerate(v);
                    return fmt.startsWith("helium.") ? Component.translatable(fmt) : Component.literal(fmt);
                });
            } else if (i.suffix() != null) {
                o.setValueFormatter(v -> Component.translatable(i.suffix(), v));
            } else {
                o.setValueFormatter(v -> Component.literal(String.valueOf(v)));
            }
            group.addOption(o);

        } else if (opt instanceof EnumOpt e) {
            String id = e.key().replace("helium.option.", "").replace(".", "_");
            EnumOptionBuilder o = builder.createEnumOption(
                    VersionCompat.createIdentifier(NAMESPACE, id), e.clazz());
            o.setName(Component.translatable(e.key()));
            o.setTooltip(Component.translatable(e.key() + ".tooltip"));
            o.setImpact(IMPACTS[Math.min(e.impact(), IMPACTS.length - 1)]);
            o.setDefaultValue(e.def());
            o.setElementNameProvider(v -> {
                String enumid = v instanceof Enum<?> en ? en.name().toLowerCase() : v.toString().toLowerCase();
                if (v instanceof com.helium.platform.DwmEnums.WindowMaterial m) enumid = m.id;
                if (v instanceof com.helium.platform.DwmEnums.WindowCorner c) enumid = c.id;
                return Component.translatable(e.namePrefix() + enumid);
            });
            o.setStorageHandler(storage);
            if (needsreload) o.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD);
            boolean enumEnabled = (Boolean) e.enabled().get();
            o.setEnabled(enumEnabled);
            if (!enumEnabled) {
                o.setBinding(v -> {}, () -> e.def());
            } else {
                o.setBinding(v -> e.set().accept(v), () -> e.get().get());
            }
            group.addOption(o);
        }
    }
}
