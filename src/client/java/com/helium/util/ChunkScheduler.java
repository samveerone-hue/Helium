package com.helium.util;

import net.minecraft.client.renderer.LevelRenderer;

/** Safe scheduling bridge using a Mixin invoker instead of reflection. */
public final class ChunkScheduler {
    private ChunkScheduler() {}

    public static int drainLimited(LevelRenderer renderer, int max, EntryPoller poller, BypassController bypass) {
        if (renderer == null || max <= 0) return 0;
        WorldRendererInvoker invoker = (WorldRendererInvoker) renderer;
        bypass.set(true);
        int count = 0;
        try {
            while (count < max) {
                ChunkEntry entry = poller.poll();
                if (entry == null) break;
                invoker.helium$invokeScheduleChunkRender(entry.x(), entry.y(), entry.z(), entry.important());
                count++;
            }
        } finally {
            bypass.set(false);
        }
        return count;
    }

    @FunctionalInterface public interface EntryPoller { ChunkEntry poll(); }
    @FunctionalInterface public interface BypassController { void set(boolean bypassing); }
    public record ChunkEntry(int x, int y, int z, boolean important) {}
}
