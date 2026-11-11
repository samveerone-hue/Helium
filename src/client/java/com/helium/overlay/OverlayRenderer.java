package com.helium.overlay;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.memory.NativeMemoryManager;
import com.helium.particle.ParticleLimiter;
import com.helium.render.AsyncChunkMeshing;
import com.helium.rentities.entities.EntityBatchRenderer;
import com.helium.tweaks.AsyncPackReloader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public final class OverlayRenderer {

    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 10;
    private static final int SHADOW_OFFSET = 1;

    private static final String[] SPINNER_FRAMES = { "⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏" };
    private static final int SPINNER_SPEED_MS = 80;

    private static final FpsStats fpsStats = new FpsStats();
    private static long lastNativeSampleNanos;
    private static long lastNativeAllocationBytes;
    private static double nativeAllocationRateMbPerSecond;
    private static long peakHeapBytes;
    private static long peakNativeBytes;

    private static volatile java.lang.reflect.Field _fpsfield;
    private static volatile boolean _fpsfieldresolved;

    private OverlayRenderer() {}

    public static void render(DrawContext context, MinecraftClient client) {
        renderspinner(context, client);
        renderInternal(context, client);
    }

    private static void renderInternal(DrawContext context, MinecraftClient client) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.modEnabled || !config.fpsOverlay) return;
        if (client.player == null) return;
        if (client.options.hudHidden) return;

        fpsStats.updateFps(getfps(client));

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        String[] lines = buildOverlayLines(client, config);
        int maxWidth = 0;
        for (String line : lines) {
            int w = client.textRenderer.getWidth(line);
            if (w > maxWidth) maxWidth = w;
        }

        int totalHeight = lines.length * LINE_HEIGHT;
        int boxWidth = maxWidth + PADDING * 2;
        int boxHeight = totalHeight + PADDING * 2 - 2;

        OverlayPosition position = parsePosition(config.overlayPosition);
        int[] pos = calculatePosition(position, screenWidth, screenHeight, boxWidth, boxHeight);
        int boxX = pos[0];
        int boxY = pos[1];

        int bgColor = ColorUtils.parseColor(config.overlayBackgroundColor, config.overlayTransparency);
        int textColor = ColorUtils.parseColor(config.overlayTextColor, 100);

        drawShadow(context, boxX, boxY, boxWidth, boxHeight);
        drawRoundedBox(context, boxX, boxY, boxWidth, boxHeight, bgColor);

        int textX = boxX + PADDING;
        int textY = boxY + PADDING;
        for (String line : lines) {
            context.drawText(client.textRenderer, Text.literal(line), textX, textY, textColor, false);
            textY += LINE_HEIGHT;
        }
    }

    private static String[] buildOverlayLines(MinecraftClient client, HeliumConfig config) {
        java.util.List<String> lines = new java.util.ArrayList<>();

        if (config.overlayShowFps) {
            lines.add(String.format("%d FPS", fpsStats.getCurrentFps()));
        }

        if (config.overlayShowFpsMinMaxAvg) {
            lines.add(String.format("↓%d ↑%d ~%d", fpsStats.getMinFps(), fpsStats.getMaxFps(), fpsStats.getAvgFps()));
                lines.add(String.format("Frame interval: %.2f ms", fpsStats.getFrameIntervalMs()));
                lines.add(String.format("Entity batch: %d | Chunks queued: %d",
                    EntityBatchRenderer.getLastBatchCount(), AsyncChunkMeshing.size()));
                lines.add(String.format("GPU fence poll: %.3f ms",
                    EntityBatchRenderer.getLastFencePollDurationNanos() / 1_000_000.0));
                addFeatureStatus(lines, config);
        }

        if (config.overlayShowMemory) {
            Runtime rt = Runtime.getRuntime();
            long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            long maxMb = rt.maxMemory() / 1024 / 1024;
            peakHeapBytes = Math.max(peakHeapBytes, rt.totalMemory() - rt.freeMemory());
            lines.add(String.format("Heap: %d / %d MB (peak %d MB)", usedMb, maxMb, peakHeapBytes / 1024 / 1024));
            if (NativeMemoryManager.isInitialized()) {
                long nativeBytes = NativeMemoryManager.getTotalAllocatedBytes();
                peakNativeBytes = Math.max(peakNativeBytes, nativeBytes);
                lines.add(String.format("Native: %d active / %d pooled MB",
                        NativeMemoryManager.getActiveBytes() / 1024 / 1024,
                        NativeMemoryManager.getTotalPooledBytes() / 1024 / 1024));
                lines.add(String.format("Native peak: %d MB", peakNativeBytes / 1024 / 1024));
                lines.add(String.format("Native alloc: %.2f MB/s", getNativeAllocationRateMbPerSecond()));
            }
        }

        if (config.overlayShowParticles && config.particleLimiting && ParticleLimiter.isInitialized()) {
            lines.add(String.format("Particles: %d/%d", ParticleLimiter.getCurrentCount(), ParticleLimiter.getMaxParticles()));
        }

        if (config.overlayShowCoordinates && client.player != null) {
            lines.add(String.format("X: %.0f Y: %.0f Z: %.0f",
                    client.player.getX(), client.player.getY(), client.player.getZ()));
        }

        if (config.overlayShowBiome && client.player != null && client.world != null) {
            try {
                var biomeEntry = client.world.getBiome(client.player.getBlockPos());
                var biomeKey = biomeEntry.getKey();
                if (biomeKey.isPresent()) {
                    String biomeName = biomeKey.get().getValue().getPath();
                    biomeName = biomeName.replace('_', ' ');
                    StringBuilder formatted = new StringBuilder();
                    boolean capitalize = true;
                    for (char c : biomeName.toCharArray()) {
                        formatted.append(capitalize ? Character.toUpperCase(c) : c);
                        capitalize = (c == ' ');
                    }
                    lines.add(formatted.toString());
                }
            } catch (Throwable ignored) {}
        }

        if (lines.isEmpty()) {
            lines.add("Helium");
        }

        return lines.toArray(new String[0]);
    }

    private static void addFeatureStatus(java.util.List<String> lines, HeliumConfig config) {
        java.util.List<String> renderFeatures = new java.util.ArrayList<>();
        addIfEnabled(renderFeatures, config.entityCulling, "entity cull");
        addIfEnabled(renderFeatures, config.blockEntityCulling, "block cull");
        addIfEnabled(renderFeatures, config.particleCulling, "particle cull");
        addIfEnabled(renderFeatures, config.entityGpuBatching, "entity GPU");
        addIfEnabled(renderFeatures, config.temporalReprojection, "temporal");
        if (!renderFeatures.isEmpty()) lines.add("Render: " + String.join(", ", renderFeatures));

        java.util.List<String> engineFeatures = new java.util.ArrayList<>();
        addIfEnabled(engineFeatures, config.nativeMemory, "native memory");
        addIfEnabled(engineFeatures, config.renderPipelining, "chunk pipeline");
        addIfEnabled(engineFeatures, config.adaptiveChunkScheduling, "adaptive chunks");
        addIfEnabled(engineFeatures, config.asyncLightUpdates, "async lighting");
        if (!engineFeatures.isEmpty()) lines.add("Engine: " + String.join(", ", engineFeatures));
    }

    private static void addIfEnabled(java.util.List<String> features, boolean enabled, String name) {
        if (enabled) features.add(name);
    }

    private static synchronized double getNativeAllocationRateMbPerSecond() {
        long now = System.nanoTime();
        long total = NativeMemoryManager.getCumulativeAllocatedBytes();
        if (lastNativeSampleNanos == 0L) {
            lastNativeSampleNanos = now;
            lastNativeAllocationBytes = total;
        } else {
            long elapsed = now - lastNativeSampleNanos;
            if (elapsed >= 500_000_000L) {
                nativeAllocationRateMbPerSecond = Math.max(0L, total - lastNativeAllocationBytes)
                        * 1_000_000_000.0 / elapsed / (1024.0 * 1024.0);
                lastNativeSampleNanos = now;
                lastNativeAllocationBytes = total;
            }
        }
        return nativeAllocationRateMbPerSecond;
    }

    private static OverlayPosition parsePosition(String posStr) {
        if (posStr == null) return OverlayPosition.TOP_LEFT;
        return switch (posStr.toUpperCase()) {
            case "TOP_RIGHT" -> OverlayPosition.TOP_RIGHT;
            case "BOTTOM_LEFT" -> OverlayPosition.BOTTOM_LEFT;
            case "BOTTOM_RIGHT" -> OverlayPosition.BOTTOM_RIGHT;
            default -> OverlayPosition.TOP_LEFT;
        };
    }

    private static int[] calculatePosition(OverlayPosition position, int screenWidth, int screenHeight, int boxWidth, int boxHeight) {
        int margin = 10;
        return switch (position) {
            case TOP_RIGHT -> new int[] { screenWidth - boxWidth - margin, margin };
            case BOTTOM_LEFT -> new int[] { margin, screenHeight - boxHeight - margin };
            case BOTTOM_RIGHT -> new int[] { screenWidth - boxWidth - margin, screenHeight - boxHeight - margin };
            default -> new int[] { margin, margin };
        };
    }

    private static void drawShadow(DrawContext context, int x, int y, int width, int height) {
        int shadowColor = ColorUtils.createColor(0, 0, 0, 40);
        context.fill(x + SHADOW_OFFSET, y + SHADOW_OFFSET, x + width + SHADOW_OFFSET, y + height + SHADOW_OFFSET, shadowColor);
    }

    private static void drawRoundedBox(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + height, color);

        int cornerRadius = 2;
        int cornerColor = ColorUtils.withAlpha(color, (color >> 24) & 0xFF);
        
        context.fill(x - 1, y + cornerRadius, x, y + height - cornerRadius, cornerColor);
        context.fill(x + width, y + cornerRadius, x + width + 1, y + height - cornerRadius, cornerColor);
        context.fill(x + cornerRadius, y - 1, x + width - cornerRadius, y, cornerColor);
        context.fill(x + cornerRadius, y + height, x + width - cornerRadius, y + height + 1, cornerColor);
    }

    private static void renderspinner(DrawContext context, MinecraftClient client) {
        if (!AsyncPackReloader.isloading()) return;
        renderglobalspinner(context, client);
    }

    public static void renderglobalspinner(DrawContext context, MinecraftClient client) {
        if (!AsyncPackReloader.isloading()) return;

        long time = Util.getMeasuringTimeMs();
        int frame = (int) ((time / SPINNER_SPEED_MS) % SPINNER_FRAMES.length);
        String text = SPINNER_FRAMES[frame] + " Reloading packs...";

        int x = 10;
        int y = client.getWindow().getScaledHeight() - 20;
        int padding = 6;
        int bgx = x - padding;
        int bgy = y - padding + 2;
        int bgw = client.textRenderer.getWidth(text) + padding * 2;
        int bgh = 14;

        drawroundedbox(context, bgx, bgy, bgw, bgh, 4, 0xDD000000);
        context.drawText(client.textRenderer, Text.literal(text), x, y, 0xFFFFFFFF, true);
    }

    private static void drawroundedbox(DrawContext context, int x, int y, int w, int h, int radius, int color) {
        context.fill(x + radius, y, x + w - radius, y + h, color);
        context.fill(x, y + radius, x + radius, y + h - radius, color);
        context.fill(x + w - radius, y + radius, x + w, y + h - radius, color);

        context.fill(x + 1, y + 1, x + radius, y + radius, color);
        context.fill(x + w - radius, y + 1, x + w - 1, y + radius, color);
        context.fill(x + 1, y + h - radius, x + radius, y + h - 1, color);
        context.fill(x + w - radius, y + h - radius, x + w - 1, y + h - 1, color);
    }

    public static void renderglobal(DrawContext context, MinecraftClient client) {
        if (client == null) return;
        try {
            renderglobalspinner(context, client);
        } catch (Throwable ignored) {}
    }

    private static int getfps(MinecraftClient client) {
        try {
            return client.getCurrentFps();
        } catch (NoSuchMethodError e) {
            if (!_fpsfieldresolved) {
                try {
                    String mapped = net.fabricmc.loader.api.FabricLoader.getInstance()
                            .getMappingResolver()
                            .mapFieldName("intermediary",
                                    "net.minecraft.class_310",
                                    "field_1728",
                                    "I");
                    java.lang.reflect.Field f = MinecraftClient.class.getDeclaredField(mapped);
                    f.setAccessible(true);
                    _fpsfield = f;
                } catch (Throwable t) {
                    HeliumClient.LOGGER.warn("could not resolve currentFps field");
                }
                _fpsfieldresolved = true;
            }
            if (_fpsfield != null) {
                try {
                    return _fpsfield.getInt(null);
                } catch (Throwable t) {
                    return 0;
                }
            }
            return 0;
        }
    }

    public static FpsStats getFpsStats() {
        return fpsStats;
    }
}
