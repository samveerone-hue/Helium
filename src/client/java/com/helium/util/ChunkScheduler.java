package com.helium.util;

import com.helium.HeliumClient;
import com.helium.mixin.render.WorldRendererInvoker;
import net.minecraft.client.render.WorldRenderer;

import java.util.Iterator;

/**
 * Safe scheduling bridge ported from Catalyst's 1.21.X chunk scheduler.
 * Uses a Mixin invoker rather than access-widening WorldRenderer's scheduler.
 */
public final class ChunkScheduler {
    private ChunkScheduler() {}

    public static void drain(WorldRenderer renderer,
                             Iterator<ChunkEntry> entries,
                             BypassController bypassController) {
        WorldRendererInvoker invoker = (WorldRendererInvoker) renderer;
        bypassController.set(true);
        try {
            while (entries.hasNext()) {
                ChunkEntry entry = entries.next();
                invoker.helium$invokeScheduleChunkRender(
                        entry.x(), entry.y(), entry.z(), entry.important());
            }
        } finally {
            bypassController.set(false);
        }
    }

    public static int drainLimited(WorldRenderer renderer,
                                   int max,
                                   EntryPoller poller,
                                   BypassController bypassController) {
        if (max <= 0) return 0;

        WorldRendererInvoker invoker = (WorldRendererInvoker) renderer;
        bypassController.set(true);
        int count = 0;
        try {
            while (count < max) {
                ChunkEntry entry = poller.poll();
                if (entry == null) break;
                invoker.helium$invokeScheduleChunkRender(
                        entry.x(), entry.y(), entry.z(), entry.important());
                count++;
            }
        } finally {
            bypassController.set(false);
        }
        return count;
    }

    @FunctionalInterface
    public interface BypassController {
        void set(boolean bypassing);
    }

    @FunctionalInterface
    public interface EntryPoller {
        ChunkEntry poll();
    }

    public record ChunkEntry(int x, int y, int z, boolean important) {}
}
