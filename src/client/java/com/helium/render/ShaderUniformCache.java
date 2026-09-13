package com.helium.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import org.lwjgl.opengl.GL20;

import java.util.HashMap;

public final class ShaderUniformCache {

    private static final HashMap<Integer, HashMap<String, Integer>> _programs = new HashMap<>();

    private ShaderUniformCache() {}

    public static boolean isenabled() {
        HeliumConfig config = HeliumClient.getConfig();
        return config != null && config.modEnabled && config.shaderUniformCache;
    }

    public static int getuniform(int program, CharSequence name) {
        String key = name.toString();
        return _programs
                .computeIfAbsent(program, id -> new HashMap<>())
                .computeIfAbsent(key, k -> GL20.glGetUniformLocation(program, name));
    }

    public static void invalidate() {
        _programs.clear();
    }
}
