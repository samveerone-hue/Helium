package com.helium;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HeliumMixinConfigTest {

    @Test
    void injectorDefaultRequiresHooksToApply() {
        try (InputStream input = getClass().getResourceAsStream("/helium.mixins.json")) {
            assertNotNull(input);
            JsonObject config = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
            assertEquals("com.helium.mixin.HeliumMixinPlugin", config.get("plugin").getAsString());
            String mixinPackage = config.get("package").getAsString().replace('.', '/') + "/";
            for (JsonElement mixin : config.getAsJsonArray("client")) {
                String classPath = mixinPackage + mixin.getAsString().replace('.', '/') + ".class";
                assertNotNull(getClass().getClassLoader().getResource(classPath),
                        "Mixin class is missing: " + mixin.getAsString());
            }
        } catch (Exception exception) {
            throw new AssertionError("Unable to read Helium mixin config", exception);
        }
    }
}
