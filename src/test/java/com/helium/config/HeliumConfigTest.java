package com.helium.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HeliumConfigTest {
    @Test
    void exportToFileHandlesRelativePathsWithoutParent() throws IOException {
        Path tempDir = Files.createTempDirectory("helium-config-export");
        Path original = Path.of(".").toAbsolutePath();
        Path cwd = Files.createDirectory(tempDir.resolve("cwd"));

        Path previous = Path.of("").toAbsolutePath();
        // The config export should work with a relative path even when it has no parent.
        Path relative = cwd.resolve("settings.json");
        HeliumConfig config = new HeliumConfig();

        assertTrue(config.exportToFile(relative.getFileName()));
        assertTrue(Files.exists(relative.getFileName()));
    }

    @Test
    void importFromFileRejectsMalformedJson() throws IOException {
        Path file = Files.createTempFile("helium-invalid-config", ".json");
        Files.writeString(file, "{not valid json");

        assertNull(HeliumConfig.importFromFile(file));
    }
}
