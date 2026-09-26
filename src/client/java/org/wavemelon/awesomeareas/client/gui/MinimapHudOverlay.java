package org.wavemelon.awesomeareas.client.gui;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import org.wavemelon.awesomeareas.client.ClientAreaCache;
import org.wavemelon.awesomeareas.core.area.Area;
import org.wavemelon.awesomeareas.core.area.AreaType;
import org.wavemelon.awesomeareas.core.boundary.Boundary;
import org.wavemelon.awesomeareas.core.storage.AreaManager;

import java.util.Locale;

/**
 * Renders an always-visible minimap in the top-right corner of the HUD,
 * along with current coordinates and territory inspection text below it.
 * Toggled via F12.
 */
public class MinimapHudOverlay implements HudElement {
    private static boolean visible = true;
    private final TerrainMapRenderer terrainRenderer = new TerrainMapRenderer();

    public static boolean isVisible() {
        return visible;
    }

    public static void setVisible(boolean state) {
        visible = state;
    }

    public static void toggleVisibility() {
        visible = !visible;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!visible) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (client.gui.screen() instanceof AreaMapScreen) return; // Don't render minimap over world map screen

        LocalPlayer player = client.player;
        int px = player.getBlockX();
        int pz = player.getBlockZ();

        int mapSize = 96;
        int padding = 10;
        int x = client.getWindow().getGuiScaledWidth() - mapSize - padding;
        int y = padding;

        float zoom = 0.5f; // Minimap zoom

        // 1. Minimap background terrain
        terrainRenderer.renderTerrain(graphics, client, px, pz, zoom, x, y, mapSize, mapSize);

        // Minimap border
        graphics.outline(x - 1, y - 1, mapSize + 2, mapSize + 2, 0xFFFFFFFF);
        graphics.outline(x - 2, y - 2, mapSize + 4, mapSize + 4, 0x88000000);

        // 2. Render area borders on minimap
        AreaManager manager = ClientAreaCache.getInstance().getManager();
        int halfSize = mapSize / 2;
        int mapCenterX = x + halfSize;
        int mapCenterY = y + halfSize;

        int clipMinX = x;
        int clipMinY = y;
        int clipMaxX = x + mapSize - 1;
        int clipMaxY = y + mapSize - 1;

        for (Area area : manager.getAllAreas()) {
            if (area.getType() == AreaType.CITY) {
                // Cities don't render border outlines on maps unless selected
                continue;
            }

            Boundary b = area.getBoundary();
            if (b == null) continue;

            int sx1 = (int) (mapCenterX + (b.getMinX() - px) * zoom);
            int sz1 = (int) (mapCenterY + (b.getMinZ() - pz) * zoom);
            int sx2 = (int) (mapCenterX + (b.getMaxX() + 1 - px) * zoom);
            int sz2 = (int) (mapCenterY + (b.getMaxZ() + 1 - pz) * zoom);

            // Check intersection with the minimap box
            if (sx2 >= x && sx1 <= x + mapSize && sz2 >= y && sz1 <= y + mapSize) {
                int borderColor = (area.getColor() & 0x00FFFFFF) | 0xDD000000;
                for (Boundary.BorderSegment seg : b.getBorderSegments()) {
                    double lx1 = mapCenterX + (seg.x0() - px) * zoom;
                    double lz1 = mapCenterY + (seg.z0() - pz) * zoom;
                    double lx2 = mapCenterX + (seg.x1() - px) * zoom;
                    double lz2 = mapCenterY + (seg.z1() - pz) * zoom;
                    drawClippedMinimapLine(graphics, lx1, lz1, lx2, lz2, borderColor, clipMinX, clipMinY, clipMaxX, clipMaxY);
                }
            }
        }

        // 3. Player marker at center
        int cx = mapCenterX;
        int cy = mapCenterY;
        graphics.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFFFFFFFF);
        graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF1A73E8);

        // Player yaw directional needle
        float yawRad = (float) Math.toRadians(player.getYRot());
        int nx = cx + (int) (-Math.sin(yawRad) * 7);
        int ny = cy + (int) (Math.cos(yawRad) * 7);
        drawClippedMinimapLine(graphics, cx, cy, nx, ny, 0xFFEA4335, clipMinX, clipMinY, clipMaxX, clipMaxY);

        // 4. Territory Inspection and Coordinates text underneath minimap
        int textY = y + mapSize + 5;
        Area currentArea = manager.getInnermostAreaAt(player.getBlockX(), player.getBlockZ());

        String areaTitle = currentArea != null
                ? currentArea.getName() + " [" + currentArea.getEffectiveTypeDisplayName() + "]"
                : "Wilderness";
        int areaColor = currentArea != null ? 0xFFFFA726 : 0xFFAAAAAA;

        String coordText = String.format(Locale.ROOT, "XYZ: %d, %d, %d", player.getBlockX(), player.getBlockY(), player.getBlockZ());

        // Background box for HUD text
        int textBgW = Math.max(mapSize, Math.max(client.font.width(areaTitle), client.font.width(coordText)) + 10);
        int textBgX = client.getWindow().getGuiScaledWidth() - textBgW - padding;

        graphics.fill(textBgX, textY - 2, textBgX + textBgW, textY + 23, 0x99000000);
        graphics.outline(textBgX, textY - 2, textBgW, 25, 0x44FFFFFF);

        graphics.text(client.font, areaTitle, textBgX + 5, textY + 1, areaColor, true);
        graphics.text(client.font, coordText, textBgX + 5, textY + 12, 0xFFCCCCCC, true);
    }

    private static final int INSIDE = 0; // 0000
    private static final int LEFT = 1;   // 0001
    private static final int RIGHT = 2;  // 0010
    private static final int BOTTOM = 4; // 0100
    private static final int TOP = 8;    // 1000

    private static int computeOutCode(double x, double y, int minX, int minY, int maxX, int maxY) {
        int code = INSIDE;
        if (x < minX) code |= LEFT;
        else if (x > maxX) code |= RIGHT;
        if (y < minY) code |= TOP;
        else if (y > maxY) code |= BOTTOM;
        return code;
    }

    private static void drawClippedMinimapLine(GuiGraphicsExtractor graphics, double x0, double y0, double x1, double y1, int color, int minX, int minY, int maxX, int maxY) {
        int code0 = computeOutCode(x0, y0, minX, minY, maxX, maxY);
        int code1 = computeOutCode(x1, y1, minX, minY, maxX, maxY);
        boolean accept = false;

        while (true) {
            if ((code0 | code1) == 0) {
                accept = true;
                break;
            } else if ((code0 & code1) != 0) {
                break;
            } else {
                double x = 0, y = 0;
                int outcodeOut = code0 != 0 ? code0 : code1;

                if ((outcodeOut & TOP) != 0) {
                    x = x0 + (x1 - x0) * (minY - y0) / (y1 - y0);
                    y = minY;
                } else if ((outcodeOut & BOTTOM) != 0) {
                    x = x0 + (x1 - x0) * (maxY - y0) / (y1 - y0);
                    y = maxY;
                } else if ((outcodeOut & RIGHT) != 0) {
                    y = y0 + (y1 - y0) * (maxX - x0) / (x1 - x0);
                    x = maxX;
                } else if ((outcodeOut & LEFT) != 0) {
                    y = y0 + (y1 - y0) * (minX - x0) / (x1 - x0);
                    x = minX;
                }

                if (outcodeOut == code0) {
                    x0 = x;
                    y0 = y;
                    code0 = computeOutCode(x0, y0, minX, minY, maxX, maxY);
                } else {
                    x1 = x;
                    y1 = y;
                    code1 = computeOutCode(x1, y1, minX, minY, maxX, maxY);
                }
            }
        }

        if (accept) {
            drawBresenhamLine(graphics, (int) Math.round(x0), (int) Math.round(y0), (int) Math.round(x1), (int) Math.round(y1), color, minX, minY, maxX, maxY);
        }
    }

    private static void drawBresenhamLine(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color, int minX, int minY, int maxX, int maxY) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int x = x0;
        int y = y0;

        while (true) {
            if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
                graphics.fill(x, y, x + 1, y + 1, color);
            }
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }
}
