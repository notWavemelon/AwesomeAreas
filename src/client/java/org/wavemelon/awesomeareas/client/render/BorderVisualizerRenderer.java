package org.wavemelon.awesomeareas.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.wavemelon.awesomeareas.client.ClientAreaCache;
import org.wavemelon.awesomeareas.core.area.Area;
import org.wavemelon.awesomeareas.core.boundary.Boundary;
import org.wavemelon.awesomeareas.core.storage.AreaManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders holographic in-world territory borders precisely along the seams
 * (intersections) between blocks on the terrain surface.
 * Toggled off by default, activated via keybinding (;).
 */
public class BorderVisualizerRenderer {
    private static boolean enabled = false;

    private record LineSegment(
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float r, float g, float b, float a
    ) {}

    public static void initialize() {
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(BorderVisualizerRenderer::render);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
            mc.player.sendSystemMessage(Component.literal(
                    "§6[AwesomeAreas] §fBorder Visualizer: " + (enabled ? "§aEnabled" : "§cDisabled")
            ));
        }
    }

    private static void render(LevelRenderContext context) {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        AreaManager manager = ClientAreaCache.getInstance().getManager();
        if (manager == null) return;

        Vec3 cam = mc.gameRenderer.mainCamera().position();
        double camX = cam.x;
        double camY = cam.y;
        double camZ = cam.z;

        final double maxRenderDist = 128.0;
        List<LineSegment> segments = new ArrayList<>();

        for (Area area : manager.getAllAreas()) {
            Boundary b = area.getBoundary();
            if (b == null) continue;

            // Bounding box rejection vs camera position
            if (camX < b.getMinX() - maxRenderDist || camX > b.getMaxX() + 1 + maxRenderDist ||
                camZ < b.getMinZ() - maxRenderDist || camZ > b.getMaxZ() + 1 + maxRenderDist) {
                continue;
            }

            int color = area.getColor() | 0xFF000000;
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float bCol = (color & 0xFF) / 255.0f;
            float a = 0.85f;

            for (Boundary.BorderSegment seg : b.getBorderSegments()) {
                int x0 = seg.x0();
                int z0 = seg.z0();
                int x1 = seg.x1();
                int z1 = seg.z1();

                if (Math.max(x0, x1) < camX - maxRenderDist || Math.min(x0, x1) > camX + maxRenderDist ||
                    Math.max(z0, z1) < camZ - maxRenderDist || Math.min(z0, z1) > camZ + maxRenderDist) {
                    continue;
                }

                if (seg.isHorizontal()) {
                    int z = z0;
                    int minX = Math.min(x0, x1);
                    int maxX = Math.max(x0, x1);
                    for (int x = minX; x < maxX; x++) {
                        double dCam = Math.hypot(x + 0.5 - camX, z - camZ);
                        if (dCam > maxRenderDist) continue;

                        int yA = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z - 1);
                        int yB = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                        int curY = Math.max(yA, yB);

                        float rx0 = (float) (x - camX);
                        float rx1 = (float) (x + 1 - camX);
                        float rz = (float) (z - camZ);
                        float ry0 = (float) (curY + 0.03 - camY);
                        float ryTop = (float) (curY + 2.5 - camY);

                        // Ground contour along block seam
                        segments.add(new LineSegment(rx0, ry0, rz, rx1, ry0, rz, r, g, bCol, a));
                        // Elevated beam
                        segments.add(new LineSegment(rx0, ryTop, rz, rx1, ryTop, rz, r, g, bCol, a));
                        // Laser pillar at corner
                        segments.add(new LineSegment(rx0, ry0, rz, rx0, ryTop, rz, r, g, bCol, a * 0.45f));
                    }
                } else if (seg.isVertical()) {
                    int x = x0;
                    int minZ = Math.min(z0, z1);
                    int maxZ = Math.max(z0, z1);
                    for (int z = minZ; z < maxZ; z++) {
                        double dCam = Math.hypot(x - camX, z + 0.5 - camZ);
                        if (dCam > maxRenderDist) continue;

                        int yA = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x - 1, z);
                        int yB = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                        int curY = Math.max(yA, yB);

                        float rx = (float) (x - camX);
                        float rz0 = (float) (z - camZ);
                        float rz1 = (float) (z + 1 - camZ);
                        float ry0 = (float) (curY + 0.03 - camY);
                        float ryTop = (float) (curY + 2.5 - camY);

                        // Ground contour along block seam
                        segments.add(new LineSegment(rx, ry0, rz0, rx, ry0, rz1, r, g, bCol, a));
                        // Elevated beam
                        segments.add(new LineSegment(rx, ryTop, rz0, rx, ryTop, rz1, r, g, bCol, a));
                        // Laser pillar at corner
                        segments.add(new LineSegment(rx, ry0, rz0, rx, ryTop, rz0, r, g, bCol, a * 0.45f));
                    }
                }
            }
        }

        if (!segments.isEmpty()) {
            context.submitNodeCollector().submitCustomGeometry(
                    context.poseStack(),
                    RenderTypes.lines(),
                    (pose, consumer) -> {
                        for (LineSegment seg : segments) {
                            consumer.addVertex(pose, seg.x0(), seg.y0(), seg.z0())
                                    .setColor(seg.r(), seg.g(), seg.b(), seg.a())
                                    .setNormal(pose, 0f, 1f, 0f)
                                    .setLineWidth(2.0f);
                            consumer.addVertex(pose, seg.x1(), seg.y1(), seg.z1())
                                    .setColor(seg.r(), seg.g(), seg.b(), seg.a())
                                    .setNormal(pose, 0f, 1f, 0f)
                                    .setLineWidth(2.0f);
                        }
                    }
            );
        }
    }
}
