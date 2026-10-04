package com.helium.render;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import net.minecraft.text.Style;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class TextRenderOptimizer {

    private static final int MAX_CACHE_SIZE = 1024;
    private record GlyphEntry(long key, Object glyph) {}

    private static final ConcurrentHashMap<Long, GlyphEntry> _glyphcache = new ConcurrentHashMap<>(256);
    private static final ConcurrentLinkedQueue<GlyphEntry> _glyphOrder = new ConcurrentLinkedQueue<>();
    private static boolean _fontaccessfailed = false;

    private TextRenderOptimizer() {}

    public static boolean isenabled() {
        HeliumConfig config = HeliumClient.getConfig();
        return config != null && config.modEnabled && config.acceleratedText;
    }

    public static Object getcached(long key) {
        GlyphEntry entry = _glyphcache.get(key);
        return entry == null ? null : entry.glyph();
    }

    public static void cache(long key, Object glyph) {
        if (glyph == null) return;
        GlyphEntry entry = new GlyphEntry(key, glyph);
        if (_glyphcache.putIfAbsent(key, entry) != null) return;
        _glyphOrder.offer(entry);
        while (_glyphcache.size() > MAX_CACHE_SIZE) {
            GlyphEntry oldest = _glyphOrder.poll();
            if (oldest == null) return;
            _glyphcache.remove(oldest.key(), oldest);
        }
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
        _glyphOrder.clear();
    }
}
