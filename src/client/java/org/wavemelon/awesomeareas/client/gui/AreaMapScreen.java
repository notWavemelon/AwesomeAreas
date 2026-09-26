package org.wavemelon.awesomeareas.client.gui;

import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;
import org.wavemelon.awesomeareas.client.AwesomeAreasClient;
import org.wavemelon.awesomeareas.client.ClientAreaCache;
import org.wavemelon.awesomeareas.core.area.Area;
import org.wavemelon.awesomeareas.core.area.AreaType;
import org.wavemelon.awesomeareas.core.boundary.BlockGridBoundary;
import org.wavemelon.awesomeareas.core.boundary.Boundary;
import org.wavemelon.awesomeareas.core.boundary.PolygonBoundary;
import org.wavemelon.awesomeareas.core.history.AreaHistoryEntry;
import org.wavemelon.awesomeareas.core.population.PopulationService;
import org.wavemelon.awesomeareas.core.storage.AreaManager;
import org.wavemelon.awesomeareas.network.UpdateAreaBoundaryPayload;

import java.util.*;

/**
 * Fullscreen interactive world map styled cleanly after Google Maps.
 * Supports zoom, pan, territory inspection, deconflicted label rendering,
 * and an integrated visual border editor.
 */
@Environment(EnvType.CLIENT)
public class AreaMapScreen extends Screen {

    public enum Tab {
        MAP("Map"),
        INFO("Info"),
        EDITOR("Editor"),
        HISTORY("History");

        private final String label;
        Tab(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private final TerrainMapRenderer terrainRenderer = new TerrainMapRenderer();

    // Map view state
    private double cameraX;
    private double cameraZ;
    private double zoom = 1.0; // 1 block = zoom pixels
    private static final double MIN_ZOOM = 0.15;
    private static final double MAX_ZOOM = 5.0;

    // Drag tracking to distinguish single click from drag
    private double dragStartX;
    private double dragStartY;
    private boolean isDragging = false;
    private double dragInitialCameraX;
    private double dragInitialCameraZ;

    // Selection & UI
    private Area selectedArea = null;
    private Tab activeTab = Tab.MAP;

    // Editor state
    private final List<PolygonBoundary.Point2D> editorPoints = new ArrayList<>();
    private String editorStatusMessage = "";
    private int editorStatusColor = 0xFF81C784;

    // Buttons
    private record PanelButton(int x, int y, int w, int h, String text, Runnable action) {}
    private final List<PanelButton> panelButtons = new ArrayList<>();

    public AreaMapScreen() {
        super(Component.literal("Awesome Areas World Map"));
    }

    @Override
    protected void init() {
        super.init();
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            cameraX = client.player.getBlockX();
            cameraZ = client.player.getBlockZ();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        int screenW = this.width;
        int screenH = this.height;

        int panelW = 220;
        int mapLeft = 0;
        int mapTop = 0;
        int mapW = screenW - panelW;
        int mapH = screenH;

        int mapCenterX = mapLeft + mapW / 2;
        int mapCenterY = mapTop + mapH / 2;

        // 1. Terrain Render
        terrainRenderer.renderTerrain(graphics, client, cameraX, cameraZ, zoom, mapLeft, mapTop, mapW, mapH);

        // 2. Render Territory Boundaries
        AreaManager manager = ClientAreaCache.getInstance().getManager();
        Collection<Area> allAreas = manager.getAllAreas();

        for (Area area : allAreas) {
            Boundary b = area.getBoundary();
            if (b == null) continue;

            boolean isSelected = (selectedArea != null && selectedArea.getId().equals(area.getId()));

            // City boundaries are hidden by default unless selected
            if (area.getType() == AreaType.CITY && !isSelected) {
                continue;
            }

            int borderColor;
            if (isSelected) {
                borderColor = 0xFFFFD700; // Gold outline for selected area
            } else {
                borderColor = switch (area.getType()) {
                    case COUNTRY -> 0xFFDDDDDD; // Bold light-gray lines
                    case PROVINCE -> 0xFFFFFFFF; // Dashed white lines
                    case COUNTY -> 0x88AAAAAA;   // Gray dashed lines 50% opacity
                    default -> (area.getColor() & 0x00FFFFFF) | 0xDD000000;
                };
            }

            boolean isDashed = !isSelected && (area.getType() == AreaType.PROVINCE || area.getType() == AreaType.COUNTY);

            for (Boundary.BorderSegment seg : b.getBorderSegments()) {
                double sx0 = mapCenterX + (seg.x0() - cameraX) * zoom;
                double sz0 = mapCenterY + (seg.z0() - cameraZ) * zoom;
                double sx1 = mapCenterX + (seg.x1() - cameraX) * zoom;
                double sz1 = mapCenterY + (seg.z1() - cameraZ) * zoom;

                drawMapLine(graphics, sx0, sz0, sx1, sz1, borderColor, isDashed, mapLeft, mapTop, mapLeft + mapW, mapTop + mapH);
                if (isSelected || area.getType() == AreaType.COUNTRY) {
                    // Double thickness for country / selected
                    drawMapLine(graphics, sx0 + 1, sz0, sx1 + 1, sz1, borderColor, false, mapLeft, mapTop, mapLeft + mapW, mapTop + mapH);
                    drawMapLine(graphics, sx0, sz0 + 1, sx1, sz1 + 1, borderColor, false, mapLeft, mapTop, mapLeft + mapW, mapTop + mapH);
                }
            }
        }

        // 3. Render Editor in-progress polygon
        if (activeTab == Tab.EDITOR && !editorPoints.isEmpty()) {
            for (int i = 0; i < editorPoints.size(); i++) {
                PolygonBoundary.Point2D p1 = editorPoints.get(i);
                double px1 = mapCenterX + (p1.x() - cameraX) * zoom;
                double pz1 = mapCenterY + (p1.z() - cameraZ) * zoom;

                // Point dot
                graphics.fill((int) px1 - 3, (int) pz1 - 3, (int) px1 + 4, (int) pz1 + 4, 0xFFFF0055);

                if (i > 0) {
                    PolygonBoundary.Point2D p0 = editorPoints.get(i - 1);
                    double px0 = mapCenterX + (p0.x() - cameraX) * zoom;
                    double pz0 = mapCenterY + (p0.z() - cameraZ) * zoom;
                    drawMapLine(graphics, px0, pz0, px1, pz1, 0xFFFF0055, false, mapLeft, mapTop, mapLeft + mapW, mapTop + mapH);
                }
            }
        }

        // 4. Render City Dots and Capital markers
        Set<UUID> capitalIds = new HashSet<>();
        for (Area a : allAreas) {
            if (a.getCapitalAreaId() != null) {
                capitalIds.add(a.getCapitalAreaId());
            }
        }

        for (Area area : allAreas) {
            if (area.getType() == AreaType.CITY) {
                Boundary b = area.getBoundary();
                if (b == null) continue;

                int cx = (int) (mapCenterX + (b.getCenterX() - cameraX) * zoom);
                int cz = (int) (mapCenterY + (b.getCenterZ() - cameraZ) * zoom);

                if (cx >= mapLeft && cx < mapLeft + mapW && cz >= mapTop && cz < mapTop + mapH) {
                    boolean isCapital = capitalIds.contains(area.getId());
                    int radius = isCapital ? 4 : 3;

                    // Black border
                    graphics.fill(cx - radius - 1, cz - radius - 1, cx + radius + 2, cz + radius + 2, 0xFF000000);
                    // White center
                    graphics.fill(cx - radius, cz - radius, cx + radius + 1, cz + radius + 1, 0xFFFFFFFF);
                    if (isCapital) {
                        // Gold center dot for capitals
                        graphics.fill(cx - 1, cz - 1, cx + 2, cz + 2, 0xFFFFD700);
                    }
                }
            }
        }

        // 5. Deconflicted Label Rendering (Hierarchy Priority: Country > Province > County > City)
        renderLabelsWithDeconfliction(graphics, allAreas, capitalIds, mapCenterX, mapCenterY, mapLeft, mapTop, mapW, mapH);

        // 6. Player position icon & directional arrow
        if (client.player != null) {
            LocalPlayer player = client.player;
            int px = (int) (mapCenterX + (player.getBlockX() - cameraX) * zoom);
            int pz = (int) (mapCenterY + (player.getBlockZ() - cameraZ) * zoom);

            if (px >= mapLeft && px < mapLeft + mapW && pz >= mapTop && pz < mapTop + mapH) {
                graphics.fill(px - 3, pz - 3, px + 4, pz + 4, 0xFFFFFFFF);
                graphics.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFF1A73E8);

                float yawRad = (float) Math.toRadians(player.getYRot());
                int nx = px + (int) (-Math.sin(yawRad) * 8);
                int ny = pz + (int) (Math.cos(yawRad) * 8);
                drawMapLine(graphics, px, pz, nx, ny, 0xFFEA4335, false, mapLeft, mapTop, mapLeft + mapW, mapTop + mapH);
            }
        }

        // 7. Right Side Control Panel
        renderSidePanel(graphics, screenW - panelW, 0, panelW, screenH, mouseX, mouseY);
    }

    private record LabelBox(int x0, int y0, int x1, int y1, int rank, String text, int color) {}

    private void renderLabelsWithDeconfliction(GuiGraphicsExtractor graphics, Collection<Area> areas, Set<UUID> capitalIds,
                                               int mapCenterX, int mapCenterY, int mapLeft, int mapTop, int mapW, int mapH) {
        List<LabelBox> candidates = new ArrayList<>();

        for (Area area : areas) {
            Boundary b = area.getBoundary();
            if (b == null) continue;

            int cx = (int) (mapCenterX + (b.getCenterX() - cameraX) * zoom);
            int cz = (int) (mapCenterY + (b.getCenterZ() - cameraZ) * zoom);

            String text = area.getName();
            int tw = this.font.width(text);
            int th = 9;

            int lx, ly;
            int color;

            if (area.getType() == AreaType.CITY) {
                boolean isCapital = capitalIds.contains(area.getId());
                int offset = isCapital ? 7 : 5;
                lx = cx + offset;
                ly = cz - 4;
                color = isCapital ? 0xFFFFD700 : 0xFFFFFFFF;
            } else {
                lx = cx - tw / 2;
                ly = cz - th / 2;
                color = switch (area.getType()) {
                    case COUNTRY -> 0xFFFFFFFF;
                    case PROVINCE -> 0xFFE0E0E0;
                    case COUNTY -> 0xFFB0B0B0;
                    default -> 0xFFFFFFFF;
                };
            }

            // Only consider labels inside or near map view
            if (lx + tw >= mapLeft && lx <= mapLeft + mapW && ly + th >= mapTop && ly <= mapTop + mapH) {
                candidates.add(new LabelBox(lx - 2, ly - 2, lx + tw + 2, ly + th + 2, area.getType().getLevel(), text, color));
            }
        }

        // Sort by hierarchy priority: Country (1) first, City (4) last
        candidates.sort(Comparator.comparingInt(LabelBox::rank));

        List<LabelBox> accepted = new ArrayList<>();
        for (LabelBox cand : candidates) {
            boolean overlapsHigherPriority = false;
            for (LabelBox acc : accepted) {
                if (cand.x0() < acc.x1() && cand.x1() > acc.x0() &&
                    cand.y0() < acc.y1() && cand.y1() > acc.y0()) {
                    overlapsHigherPriority = true;
                    break;
                }
            }
            if (!overlapsHigherPriority) {
                accepted.add(cand);
                graphics.text(this.font, cand.text(), cand.x0() + 2, cand.y0() + 2, cand.color(), true);
            }
        }
    }

    private void renderSidePanel(GuiGraphicsExtractor graphics, int panelX, int panelY, int panelW, int panelH, int mouseX, int mouseY) {
        panelButtons.clear();

        // Dark modern panel background
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xEE1E1E1E);
        graphics.outline(panelX, panelY, 1, panelH, 0xFF444444);

        int tabBtnW = (panelW - 10) / 4;
        int tabBtnH = 20;

        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int bx = panelX + 5 + i * tabBtnW;
            int by = panelY + 6;

            boolean isCurrent = (activeTab == t);
            int bg = isCurrent ? 0xFF1A73E8 : 0xFF2D2D2D;
            int fg = isCurrent ? 0xFFFFFFFF : 0xFFAAAAAA;

            graphics.fill(bx, by, bx + tabBtnW - 2, by + tabBtnH, bg);
            graphics.outline(bx, by, tabBtnW - 2, tabBtnH, 0xFF555555);

            int tw = this.font.width(t.getLabel());
            graphics.text(this.font, t.getLabel(), bx + (tabBtnW - 2 - tw) / 2, by + 6, fg, false);

            panelButtons.add(new PanelButton(bx, by, tabBtnW - 2, tabBtnH, t.getLabel(), () -> activeTab = t));
        }

        int contentX = panelX + 10;
        int contentY = panelY + 36;

        switch (activeTab) {
            case MAP -> renderMapControlsTab(graphics, contentX, contentY, panelW - 20);
            case INFO -> renderInfoTab(graphics, contentX, contentY, panelW - 20);
            case EDITOR -> renderEditorTab(graphics, contentX, contentY, panelW - 20);
            case HISTORY -> renderHistoryTab(graphics, contentX, contentY, panelW - 20);
        }
    }

    private void renderMapControlsTab(GuiGraphicsExtractor graphics, int contentX, int contentY, int contentW) {
        graphics.text(this.font, "Navigation Controls", contentX, contentY, 0xFFFFFFFF, true);
        int curY = contentY + 16;

        int btnW = contentW;
        int btnH = 20;

        panelButtons.add(new PanelButton(contentX, curY, btnW, btnH, "Zoom In (+)", () -> zoom = Math.min(MAX_ZOOM, zoom * 1.25)));
        curY += btnH + 6;

        panelButtons.add(new PanelButton(contentX, curY, btnW, btnH, "Zoom Out (-)", () -> zoom = Math.max(MIN_ZOOM, zoom / 1.25)));
        curY += btnH + 6;

        panelButtons.add(new PanelButton(contentX, curY, btnW, btnH, "Center on Player", () -> {
            if (this.minecraft.player != null) {
                cameraX = this.minecraft.player.getBlockX();
                cameraZ = this.minecraft.player.getBlockZ();
            }
        }));
        curY += btnH + 16;

        graphics.text(this.font, String.format(Locale.ROOT, "Zoom: %.1fx", zoom), contentX, curY, 0xFFAAAAAA, false);
        curY += 12;
        graphics.text(this.font, String.format(Locale.ROOT, "Cam: [%.0f, %.0f]", cameraX, cameraZ), contentX, curY, 0xFFAAAAAA, false);
        curY += 16;

        if (selectedArea != null) {
            graphics.text(this.font, "Selected: " + selectedArea.getName(), contentX, curY, 0xFFFFD700, false);
        } else {
            graphics.text(this.font, "Click territory to inspect", contentX, curY, 0xFF888888, false);
        }

        renderButtonList(graphics);
    }

    private void renderInfoTab(GuiGraphicsExtractor graphics, int contentX, int contentY, int contentW) {
        if (selectedArea == null) {
            graphics.text(this.font, "No territory selected.", contentX, contentY, 0xFF888888, false);
            graphics.text(this.font, "Click any area on the map.", contentX, contentY + 14, 0xFF666666, false);
            return;
        }

        AreaManager manager = ClientAreaCache.getInstance().getManager();
        PopulationService.AreaStatistics stats = PopulationService.computeStatistics(selectedArea, manager);

        graphics.text(this.font, selectedArea.getName(), contentX, contentY, 0xFFFFD700, true);
        int curY = contentY + 16;

        graphics.text(this.font, "Type: " + selectedArea.getEffectiveTypeDisplayName(), contentX, curY, 0xFFFFFFFF, false);
        curY += 14;

        String leader = selectedArea.getLeaderName() != null ? selectedArea.getLeaderName() : "None";
        graphics.text(this.font, "Leader: " + leader, contentX, curY, 0xFFCCCCCC, false);
        curY += 14;

        graphics.text(this.font, "Population: " + stats.population(), contentX, curY, 0xFFCCCCCC, false);
        curY += 14;

        graphics.text(this.font, "Homes / Beds: " + stats.homesCount(), contentX, curY, 0xFFCCCCCC, false);
        curY += 14;

        graphics.text(this.font, "Subdivisions: " + stats.subAreasCount(), contentX, curY, 0xFFCCCCCC, false);
        curY += 14;

        graphics.text(this.font, "Area: " + stats.areaBlocks() + " blocks", contentX, curY, 0xFFCCCCCC, false);
        curY += 18;

        if (!selectedArea.getPastNames().isEmpty()) {
            graphics.text(this.font, "Former Names:", contentX, curY, 0xFFFFA726, false);
            curY += 12;
            for (String past : selectedArea.getPastNames()) {
                graphics.text(this.font, "• " + past, contentX + 4, curY, 0xFF888888, false);
                curY += 12;
            }
        }
    }

    private void renderEditorTab(GuiGraphicsExtractor graphics, int contentX, int contentY, int contentW) {
        if (selectedArea == null) {
            graphics.text(this.font, "Select an area to edit.", contentX, contentY, 0xFF888888, false);
            return;
        }

        graphics.text(this.font, "Editing: " + selectedArea.getName(), contentX, contentY, 0xFFFFD700, true);
        int curY = contentY + 16;

        graphics.text(this.font, "Click map to place polygon points.", contentX, curY, 0xFFCCCCCC, false);
        curY += 14;
        graphics.text(this.font, "Points placed: " + editorPoints.size(), contentX, curY, 0xFFAAAAAA, false);
        curY += 16;

        int btnW = contentW;
        int btnH = 20;

        panelButtons.add(new PanelButton(contentX, curY, btnW, btnH, "Clear Points", () -> {
            editorPoints.clear();
            editorStatusMessage = "Cleared vertices";
            editorStatusColor = 0xFFCCCCCC;
        }));
        curY += btnH + 6;

        panelButtons.add(new PanelButton(contentX, curY, btnW, btnH, "Apply New Border", this::applyEditorBoundary));
        curY += btnH + 12;

        if (!editorStatusMessage.isEmpty()) {
            graphics.text(this.font, editorStatusMessage, contentX, curY, editorStatusColor, false);
        }

        renderButtonList(graphics);
    }

    private void renderHistoryTab(GuiGraphicsExtractor graphics, int contentX, int contentY, int contentW) {
        if (selectedArea == null) {
            graphics.text(this.font, "Select an area to view history.", contentX, contentY, 0xFF888888, false);
            return;
        }

        graphics.text(this.font, "History: " + selectedArea.getName(), contentX, contentY, 0xFFFFD700, true);
        int curY = contentY + 16;

        List<AreaHistoryEntry> history = selectedArea.getHistory();
        if (history.isEmpty()) {
            graphics.text(this.font, "No logged history events.", contentX, curY, 0xFF888888, false);
            return;
        }

        for (int i = history.size() - 1; i >= Math.max(0, history.size() - 8); i--) {
            AreaHistoryEntry entry = history.get(i);
            graphics.text(this.font, entry.getInGameDate() + " - " + entry.getEventType(), contentX, curY, 0xFF81C784, false);
            curY += 12;
            graphics.text(this.font, entry.getDescription(), contentX + 4, curY, 0xFFCCCCCC, false);
            curY += 14;
        }
    }

    private void renderButtonList(GuiGraphicsExtractor graphics) {
        for (PanelButton btn : panelButtons) {
            graphics.fill(btn.x(), btn.y(), btn.x() + btn.w(), btn.y() + btn.h(), 0xFF2D2D2D);
            graphics.outline(btn.x(), btn.y(), btn.w(), btn.h(), 0xFF555555);
            int tw = this.font.width(btn.text());
            graphics.text(this.font, btn.text(), btn.x() + (btn.w() - tw) / 2, btn.y() + 6, 0xFFFFFFFF, false);
        }
    }

    private void applyEditorBoundary() {
        if (selectedArea == null || editorPoints.size() < 3) return;

        Boundary newBoundary = BlockGridBoundary.fromPolygon(new ArrayList<>(editorPoints));
        AreaManager manager = ClientAreaCache.getInstance().getManager();

        if (selectedArea.getParentId() != null) {
            Area parent = manager.getAreaById(selectedArea.getParentId());
            if (parent != null && parent.getBoundary() != null && !parent.getBoundary().contains(newBoundary)) {
                Boundary snapped = BlockGridBoundary.clipToParent(newBoundary, parent.getBoundary());
                if (snapped != null && snapped.getAreaBlocks() > 0) {
                    newBoundary = snapped;
                    editorStatusMessage = "Border snapped inside " + parent.getName() + "!";
                }
            }
        }

        JsonObject json = AreaManager.serializeBoundary(newBoundary);
        ClientPlayNetworking.send(new UpdateAreaBoundaryPayload(selectedArea.getId(), json.toString()));

        if (editorStatusMessage.isEmpty()) {
            editorStatusMessage = "Border update sent to server!";
        }
        editorStatusColor = 0xFF81C784;
        editorPoints.clear();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) { // Left click
            double mx = event.x();
            double my = event.y();

            // 1. Check panel tab and toggle buttons
            for (PanelButton btn : panelButtons) {
                if (mx >= btn.x() && mx <= btn.x() + btn.w() && my >= btn.y() && my <= btn.y() + btn.h()) {
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
                    }
                    btn.action().run();
                    return true;
                }
            }

            // 2. Click in map viewport: start drag track
            int mapW = this.width - 220;
            if (mx < mapW) {
                dragStartX = mx;
                dragStartY = my;
                isDragging = false;
                dragInitialCameraX = cameraX;
                dragInitialCameraZ = cameraZ;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (event.button() == 0) {
            int mapW = this.width - 220;
            if (dragStartX < mapW) {
                double totalDistSq = (event.x() - dragStartX) * (event.x() - dragStartX) + (event.y() - dragStartY) * (event.y() - dragStartY);
                if (totalDistSq > 16) { // More than 4 pixels moved: treat as drag
                    isDragging = true;
                    cameraX = dragInitialCameraX - (event.x() - dragStartX) / zoom;
                    cameraZ = dragInitialCameraZ - (event.y() - dragStartY) / zoom;
                    return true;
                }
            }
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            int mapW = this.width - 220;
            if (event.x() < mapW && !isDragging) {
                // This was a single click!
                int mapCenterX = mapW / 2;
                int mapCenterY = this.height / 2;
                int worldX = (int) Math.round(cameraX + (event.x() - mapCenterX) / zoom);
                int worldZ = (int) Math.round(cameraZ + (event.y() - mapCenterY) / zoom);

                if (activeTab == Tab.EDITOR) {
                    editorPoints.add(new PolygonBoundary.Point2D(worldX, worldZ));
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.4F));
                    }
                } else {
                    AreaManager manager = ClientAreaCache.getInstance().getManager();
                    Area clicked = manager.getInnermostAreaAt(worldX, worldZ);
                    if (clicked != null) {
                        selectedArea = clicked;
                        activeTab = Tab.INFO;
                        if (this.minecraft != null) {
                            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        }
                    } else {
                        selectedArea = null;
                    }
                }
            }
            isDragging = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX < this.width - 220) {
            if (verticalAmount > 0) {
                zoom = Math.min(MAX_ZOOM, zoom * 1.15);
            } else if (verticalAmount < 0) {
                zoom = Math.max(MIN_ZOOM, zoom / 1.15);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || AwesomeAreasClient.getOpenMapKey().matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private static void drawMapLine(GuiGraphicsExtractor graphics, double x0, double y0, double x1, double y1, int color, boolean dashed, int minX, int minY, int maxX, int maxY) {
        int ix0 = (int) Math.round(x0);
        int iy0 = (int) Math.round(y0);
        int ix1 = (int) Math.round(x1);
        int iy1 = (int) Math.round(y1);

        int dx = Math.abs(ix1 - ix0);
        int dy = Math.abs(iy1 - iy0);
        int sx = ix0 < ix1 ? 1 : -1;
        int sy = iy0 < iy1 ? 1 : -1;
        int err = dx - dy;

        int x = ix0;
        int y = iy0;
        int step = 0;

        while (true) {
            step++;
            if (!dashed || (step % 8 < 5)) {
                if (x >= minX && x < maxX && y >= minY && y < maxY) {
                    graphics.fill(x, y, x + 1, y + 1, color);
                }
            }
            if (x == ix1 && y == iy1) break;
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
