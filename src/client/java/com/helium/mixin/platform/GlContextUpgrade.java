package com.helium.mixin.platform;

import com.helium.HeliumClient;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

final class GlContextUpgrade {

    private static Boolean enabled = null;

    private GlContextUpgrade() {}

    static boolean isEnabled() {
        if (enabled != null) return enabled;

        // Added Catalyst check here alongside threatengl
        if (FabricLoader.getInstance().isModLoaded("threatengl") || FabricLoader.getInstance().isModLoaded("catalyst")) {
            enabled = false;
            HeliumClient.LOGGER.info("catalyst or threatengl detected - disabling gl context upgrade to avoid conflicts");
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
        } catch (Throwable ignored) {}

        enabled = true;
        return true;
    }
}
