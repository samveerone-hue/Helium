package com.helium.rentities.entities;

import com.helium.HeliumClient;
import com.helium.rentities.gl.GlShader;

import static org.lwjgl.opengl.GL20C.glUseProgram;

final class EntityShaderManager {

    record Program(GlShader shader, int viewProjection, int gameTime, int entityTextures,
                   int baseInstance, int indirect, int cameraPos, int slimeOverlay,
                   int lightMap, int hasLightMap) {
        static Program unavailable() {
            return new Program(null, -1, -1, -1, -1, -1, -1, -1, -1, -1);
        }
    }

    Program compile() {
        GlShader shader = null;
        try {
            String vertexSource = loadShader("assets/helium/shaders/rentities/entity/entity_vert.glsl");
            String fragmentSource = loadShader("assets/helium/shaders/rentities/entity/entity_frag.glsl");
            shader = GlShader.builder()
                    .vert(vertexSource)
                    .frag(fragmentSource)
                    .compile();
            shader.bind();
            Program program = new Program(
                    shader,
                    shader.getUniformLocation("uViewProjection"),
                    shader.getUniformLocation("uGameTime"),
                    shader.getUniformLocation("uEntityTexture"),
                    shader.getUniformLocation("uBaseInstance"),
                    shader.getUniformLocation("uIndirect"),
                    shader.getUniformLocation("uCameraPos"),
                    shader.getUniformLocation("uSlimeOverlay"),
                    shader.getUniformLocation("uLightMap"),
                    shader.getUniformLocation("uHasLightMap"));
            glUseProgram(0);
            return program;
        } catch (Exception exception) {
            if (shader != null) shader.delete();
            HeliumClient.LOGGER.error("Entity shader compilation failed", exception);
            return Program.unavailable();
        }
    }

    private String loadShader(String path) {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Shader not found: " + path);
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load entity shader: " + path, exception);
        }
    }
}
