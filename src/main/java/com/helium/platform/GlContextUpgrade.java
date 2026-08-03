package com.helium.platform;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public final class GlContextUpgrade {

    private static final Logger LOGGER = LoggerFactory.getLogger("Helium/GlContextUpgrade");
    private static volatile Boolean enabled = null;

    private GlContextUpgrade() {}

    public static boolean isEnabled() {
        if (enabled != null) return enabled;

        if (FabricLoader.getInstance().isModLoaded("threatengl") || FabricLoader.getInstance().isModLoaded("catalyst")) {
            enabled = false;
            LOGGER.info("catalyst or threatengl detected - disabling gl context upgrade to avoid conflicts");
            return false;
        }

        try {
            Path cfgPath = FabricLoader.getInstance().getConfigDir().resolve("helium.json");
            if (Files.exists(cfgPath)) {
                String json = Files.readString(cfgPath);
                if (json.contains("\"glContextUpgrade\": false") || json.contains("\"glContextUpgrade\":false")) {
                    enabled = false;
                    return false;
                }
            }
        } catch (Exception ignored) {}

        enabled = true;
        return true;
    }
}
