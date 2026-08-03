package com.helium.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.network.chat.Style;

import java.util.concurrent.ConcurrentHashMap;

public final class TextRenderOptimizer {

    private static final int MAX_CACHE_SIZE = 1024;
    private static final ConcurrentHashMap<Long, Object> _glyphcache = new ConcurrentHashMap<>(256);
    private static boolean _fontaccessfailed = false;

    private TextRenderOptimizer() {}

    public static boolean isenabled() {
        HeliumConfig config = HeliumClient.getConfig();
        return config != null && config.modEnabled && config.acceleratedText;
    }

    public static Object getcached(long key) {
        return _glyphcache.get(key);
    }

    public static void cache(long key, Object glyph) {
        if (_glyphcache.size() >= MAX_CACHE_SIZE) {
            _glyphcache.clear();
        }
        _glyphcache.put(key, glyph);
    }

    public static long glyphkey(int codepoint, Style style) {
        int fonthash;
        if (!_fontaccessfailed) {
            try {
                fonthash = style.getFont().hashCode();
            } catch (Throwable t) {
                _fontaccessfailed = true;
                fonthash = style.hashCode();
            }
        } else {
            fonthash = style.hashCode();
        }
        return ((long) codepoint << 32) | (fonthash & 0xFFFFFFFFL);
    }

    public static void invalidate() {
        _glyphcache.clear();
    }
}
