package org.wavemelon.awesomeareas.integration.editor;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.wavemelon.awesomeareas.AwesomeAreas;
import org.wavemelon.awesomeareas.core.area.Area;
import org.wavemelon.awesomeareas.core.area.AreaType;
import org.wavemelon.awesomeareas.core.boundary.BlockGridBoundary;
import org.wavemelon.awesomeareas.core.boundary.Boundary;
import org.wavemelon.awesomeareas.core.boundary.BoxBoundary;
import org.wavemelon.awesomeareas.core.boundary.PolygonBoundary;
import org.wavemelon.awesomeareas.core.history.GameCalendar;
import org.wavemelon.awesomeareas.core.storage.AreaManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-world territory editor allowing authorized leaders to redefine an area's borders block-by-block
 * using a special tool (Stick crouch-right-click).
 */
public class InWorldTerritoryEditor {

    public static class EditSession {
        public final Area area;
        public final List<PolygonBoundary.Point2D> points = new ArrayList<>();
        public final long startTime = System.currentTimeMillis();

        public EditSession(Area area) {
            this.area = area;
        }
    }

    private static final Map<UUID, EditSession> activeSessions = new ConcurrentHashMap<>();

    public static void initialize() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }

            ItemStack held = player.getItemInHand(hand);
            if (held.isEmpty()) return InteractionResult.PASS;

            // Triggered using a STICK with crouch-right-click
            if (held.is(Items.STICK) && player.isShiftKeyDown()) {
                return handleStickUse(serverPlayer, hand);
            }

            return InteractionResult.PASS;
        });
    }

    public static void startEditing(ServerPlayer player, Area area) {
        if (player == null || area == null) return;
        AreaManager manager = AwesomeAreas.getAreaManager();
        if (!AreaManager.canEditBorders(player, area, manager)) {
            player.sendSystemMessage(Component.literal("§c[AwesomeAreas] You do not have permission to edit borders for " + area.getName() + "."));
            return;
        }

        EditSession session = new EditSession(area);
        activeSessions.put(player.getUUID(), session);
        player.sendSystemMessage(Component.literal("§a[AwesomeAreas] Started in-world territory editing for " + area.getName() + "!"));
        player.sendSystemMessage(Component.literal("§eSneak + Right-Click with a Stick on surface blocks to place border boundary points. Click the first point again to close the polygon loop."));
    }

    public static void cancelEditing(ServerPlayer player) {
        if (player == null) return;
        if (activeSessions.remove(player.getUUID()) != null) {
            player.sendSystemMessage(Component.literal("§e[AwesomeAreas] Canceled in-world territory editor."));
        }
    }

    public static boolean isEditing(ServerPlayer player) {
        return player != null && activeSessions.containsKey(player.getUUID());
    }

    private static InteractionResult handleStickUse(ServerPlayer player, InteractionHand hand) {
        EditSession session = activeSessions.get(player.getUUID());
        if (session == null) {
            return InteractionResult.PASS;
        }

        HitResult hit = player.pick(20.0D, 0.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        }

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        int bx = pos.getX();
        int bz = pos.getZ();

        AreaManager manager = AwesomeAreas.getAreaManager();
        Area area = session.area;

        // If clicking near first point (within 3 blocks) and has at least 3 points, close polygon
        if (session.points.size() >= 3) {
            PolygonBoundary.Point2D first = session.points.get(0);
            int distSq = (first.x() - bx) * (first.x() - bx) + (first.z() - bz) * (first.z() - bz);
            if (distSq <= 9) { // within 3 blocks
                // Completed shape converted cleanly to Minecraft block grid
                BlockGridBoundary newBoundary = BlockGridBoundary.fromPolygon(session.points);

                if (area.getType() == AreaType.COUNTRY) {
                    if (!AreaManager.validateCountryWaterClaim(newBoundary, player.level())) {
                        player.sendSystemMessage(Component.literal("§c[Territory Editor] Country water territory must have land within 20 blocks."));
                        return InteractionResult.SUCCESS;
                    }
                }

                if (area.getParentId() != null) {
                    Area parent = manager.getAreaById(area.getParentId());
                    if (parent != null && parent.getBoundary() != null && !parent.getBoundary().contains(newBoundary)) {
                        Boundary snapped = BlockGridBoundary.clipToParent(newBoundary, parent.getBoundary());
                        if (snapped == null || snapped.getAreaBlocks() == 0) {
                            player.sendSystemMessage(Component.literal("§c[Territory Editor] Territory must be located inside parent " + parent.getName() + "."));
                            return InteractionResult.SUCCESS;
                        }
                        newBoundary = (snapped instanceof BlockGridBoundary bgb) ? bgb : new BlockGridBoundary(List.of((BoxBoundary) snapped));
                        player.sendSystemMessage(Component.literal("§e[Territory Editor] Border automatically snapped to the inside of parent " + parent.getName() + "."));
                    }
                }

                String inGameDate = GameCalendar.formatCurrentDate(player.level());
                boolean updated = manager.updateAreaBoundary(area.getId(), newBoundary, player.getName().getString(), inGameDate);
                if (!updated) {
                    player.sendSystemMessage(Component.literal("§c[Territory Editor] Territory overlaps with another existing " + area.getType().getDefaultDisplayName() + "!"));
                    return InteractionResult.SUCCESS;
                }

                session.points.clear();
                activeSessions.remove(player.getUUID());

                spawnHighlightParticle(player.level(), pos);
                player.sendSystemMessage(Component.literal("§a✔ [Territory Editor] Successfully updated borders for " + area.getName() + "! (" + inGameDate + ")"));
                return InteractionResult.SUCCESS;
            }
        }

        // Add vertex
        session.points.add(new PolygonBoundary.Point2D(bx, bz));
        spawnHighlightParticle(player.level(), pos);
        PolygonBoundary.Point2D start = session.points.get(0);
        player.sendSystemMessage(Component.literal(
                "§e[Territory Editor] Added vertex " + session.points.size() + " at (" + bx + ", " + bz + "). Return to start (" + start.x() + ", " + start.z() + ") to complete."
        ));
        return InteractionResult.SUCCESS;
    }

    private static void spawnHighlightParticle(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            DustParticleOptions options = new DustParticleOptions(0xFFD700, 1.5F); // Gold particle
            serverLevel.sendParticles(options, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 10, 0.2, 0.2, 0.2, 0.05);
        }
    }
}
