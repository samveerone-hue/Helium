package com.helium.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HeliumConfigTest {
    @Test
    void exportToFileHandlesRelativePathsWithoutParent() throws IOException {
        Path relative = Path.of("helium-config-" + UUID.randomUUID() + ".json");
        HeliumConfig config = new HeliumConfig();

        try {
            assertTrue(config.exportToFile(relative));
            assertTrue(Files.exists(relative));
        } finally {
            Files.deleteIfExists(relative);
        }
    }

    @Test
    void importFromFileRejectsMalformedJson() throws IOException {
        Path file = Files.createTempFile("helium-invalid-config", ".json");
        try {
            Files.writeString(file, "{not valid json");
            assertNull(HeliumConfig.importFromFile(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void importingOlderConfigKeepsDefaultsAndClampsValues() throws IOException {
        Path file = Files.createTempFile("helium-old-config", ".json");
        try {
            Files.writeString(file, "{\"particleCullDistance\":999,\"fastRandom\":true}");

            HeliumConfig imported = HeliumConfig.importFromFile(file);

            assertNotNull(imported);
            assertEquals(64, imported.particleCullDistance);
            assertEquals(1000, imported.maxParticles);
            assertFalse(imported.fastRandom);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
