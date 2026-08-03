package com.helium.overlay;

import com.helium.HeliumClient;
import com.helium.config.HeliumConfig;
import com.helium.particle.ParticleLimiter;
import com.helium.tweaks.AsyncPackReloader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class OverlayRenderer {

    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 10;
    private static final int SHADOW_OFFSET = 1;

    private static final String[] SPINNER_FRAMES = { "⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏" };
    private static final int SPINNER_SPEED_MS = 80;

    private static final FpsStats fpsStats = new FpsStats();

    private static volatile java.lang.reflect.Field _fpsfield;
    private static volatile boolean _fpsfieldresolved;

    private OverlayRenderer() {}

    public static void render(GuiGraphicsExtractor context, Minecraft client) {
        renderspinner(context, client);
        renderInternal(context, client);
    }

    private static void renderInternal(GuiGraphicsExtractor context, Minecraft client) {
        HeliumConfig config = HeliumClient.getConfig();
        if (config == null || !config.modEnabled || !config.fpsOverlay) return;
        if (client.player == null) return;
        if (client.options.hideGui) return;

        fpsStats.updateFps(getfps(client));

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        String[] lines = buildOverlayLines(client, config);
        int maxWidth = 0;
        for (String line : lines) {
            int w = client.font.width(line);
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
            context.text(client.font, Component.literal(line), textX, textY, textColor, false);
            textY += LINE_HEIGHT;
        }
    }

    private static String[] buildOverlayLines(Minecraft client, HeliumConfig config) {
        java.util.List<String> lines = new java.util.ArrayList<>();

        if (config.overlayShowFps) {
            lines.add(String.format("%d FPS", fpsStats.getCurrentFps()));
        }

        if (config.overlayShowFpsMinMaxAvg) {
            lines.add(String.format("↓%d ↑%d ~%d", fpsStats.getMinFps(), fpsStats.getMaxFps(), fpsStats.getAvgFps()));
        }

        if (config.overlayShowMemory) {
            Runtime rt = Runtime.getRuntime();
            long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            long maxMb = rt.maxMemory() / 1024 / 1024;
            lines.add(String.format("%dMB / %dMB", usedMb, maxMb));
        }

        if (config.overlayShowParticles && config.particleLimiting && ParticleLimiter.isInitialized()) {
            lines.add(String.format("Particles: %d/%d", ParticleLimiter.getCurrentCount(), ParticleLimiter.getMaxParticles()));
        }

        if (config.overlayShowCoordinates && client.player != null) {
            lines.add(String.format("X: %.0f Y: %.0f Z: %.0f",
                    client.player.getX(), client.player.getY(), client.player.getZ()));
        }

        if (config.overlayShowBiome && client.player != null && client.level != null) {
            try {
                var biomeEntry = client.level.getBiome(client.player.blockPosition());
                var biomeKey = biomeEntry.unwrapKey();
                if (biomeKey.isPresent()) {
                    String biomeName = biomeKey.get().identifier().getPath();
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

    private static void drawShadow(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        int shadowColor = ColorUtils.createColor(0, 0, 0, 40);
        context.fill(x + SHADOW_OFFSET, y + SHADOW_OFFSET, x + width + SHADOW_OFFSET, y + height + SHADOW_OFFSET, shadowColor);
    }

    private static void drawRoundedBox(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + height, color);

        int cornerRadius = 2;
        int cornerColor = ColorUtils.withAlpha(color, (color >> 24) & 0xFF);
        
        context.fill(x - 1, y + cornerRadius, x, y + height - cornerRadius, cornerColor);
        context.fill(x + width, y + cornerRadius, x + width + 1, y + height - cornerRadius, cornerColor);
        context.fill(x + cornerRadius, y - 1, x + width - cornerRadius, y, cornerColor);
        context.fill(x + cornerRadius, y + height, x + width - cornerRadius, y + height + 1, cornerColor);
    }

    private static void renderspinner(GuiGraphicsExtractor context, Minecraft client) {
        if (!AsyncPackReloader.isloading()) return;
        renderglobalspinner(context, client);
    }

    public static void renderglobalspinner(GuiGraphicsExtractor context, Minecraft client) {
        if (!AsyncPackReloader.isloading()) return;

        long time = Util.getMillis();
        int frame = (int) ((time / SPINNER_SPEED_MS) % SPINNER_FRAMES.length);
        String text = SPINNER_FRAMES[frame] + " Reloading packs...";

        int x = 10;
        int y = client.getWindow().getGuiScaledHeight() - 20;
        int padding = 6;
        int bgx = x - padding;
        int bgy = y - padding + 2;
        int bgw = client.font.width(text) + padding * 2;
        int bgh = 14;

        drawroundedbox(context, bgx, bgy, bgw, bgh, 4, 0xDD000000);
        context.text(client.font, Component.literal(text), x, y, 0xFFFFFFFF, true);
    }

    private static void drawroundedbox(GuiGraphicsExtractor context, int x, int y, int w, int h, int radius, int color) {
        context.fill(x + radius, y, x + w - radius, y + h, color);
        context.fill(x, y + radius, x + radius, y + h - radius, color);
        context.fill(x + w - radius, y + radius, x + w, y + h - radius, color);

        context.fill(x + 1, y + 1, x + radius, y + radius, color);
        context.fill(x + w - radius, y + 1, x + w - 1, y + radius, color);
        context.fill(x + 1, y + h - radius, x + radius, y + h - 1, color);
        context.fill(x + w - radius, y + h - radius, x + w - 1, y + h - 1, color);
    }

    public static void renderglobal(GuiGraphicsExtractor context, Minecraft client) {
        if (client == null) return;
        try {
            renderglobalspinner(context, client);
        } catch (Throwable ignored) {}
    }

    private static int getfps(Minecraft client) {
        try {
            return client.getFps();
        } catch (NoSuchMethodError e) {
            if (!_fpsfieldresolved) {
                try {
                    String mapped = net.fabricmc.loader.api.FabricLoader.getInstance()
                            .getMappingResolver()
                            .mapFieldName("intermediary",
                                    "net.minecraft.class_310",
                                    "field_1728",
                                    "I");
                    java.lang.reflect.Field f = Minecraft.class.getDeclaredField(mapped);
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
