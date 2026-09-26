package org.wavemelon.awesomeareas;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wavemelon.awesomeareas.command.AreaCommands;
import org.wavemelon.awesomeareas.core.area.Area;
import org.wavemelon.awesomeareas.core.area.AreaType;
import org.wavemelon.awesomeareas.core.boundary.BlockGridBoundary;
import org.wavemelon.awesomeareas.core.boundary.Boundary;
import org.wavemelon.awesomeareas.core.history.GameCalendar;
import org.wavemelon.awesomeareas.core.home.Home;
import org.wavemelon.awesomeareas.core.storage.AreaManager;
import org.wavemelon.awesomeareas.integration.editor.InWorldTerritoryEditor;
import org.wavemelon.awesomeareas.integration.village.VillageDetector;
import org.wavemelon.awesomeareas.integration.village.VillagerResidentTracker;
import org.wavemelon.awesomeareas.integration.war.WarService;
import org.wavemelon.awesomeareas.network.OpenAreaMapPayload;
import org.wavemelon.awesomeareas.network.SyncAreasPayload;
import org.wavemelon.awesomeareas.network.UpdateAreaBoundaryPayload;

import java.nio.file.Path;
import java.util.UUID;

public class AwesomeAreas implements ModInitializer {
    public static final String MOD_ID = "awesomeareas";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final AreaManager AREA_MANAGER = new AreaManager();
    private static MinecraftServer currentServer;

    public static AreaManager getAreaManager() {
        return AREA_MANAGER;
    }

    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("[AwesomeAreas] Initializing Awesome Areas...");

        // 1. Register Network Payloads
        PayloadTypeRegistry.clientboundPlay().register(SyncAreasPayload.TYPE, SyncAreasPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenAreaMapPayload.TYPE, OpenAreaMapPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateAreaBoundaryPayload.TYPE, UpdateAreaBoundaryPayload.CODEC);

        // 2. Register Server Receivers
        ServerPlayNetworking.registerGlobalReceiver(UpdateAreaBoundaryPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            UUID areaId = payload.areaId();
            JsonObject bObj = JsonParser.parseString(payload.boundaryJson()).getAsJsonObject();
            Boundary newBoundary = AreaManager.deserializeBoundary(bObj);
            if (newBoundary == null) return;

            Area area = AREA_MANAGER.getAreaById(areaId);
            if (area == null) return;

            if (!AreaManager.canEditBorders(player, area, AREA_MANAGER)) {
                player.sendSystemMessage(Component.literal("§c[AwesomeAreas] You do not have permission to edit borders for " + area.getName() + "."));
                return;
            }

            if (area.getType() == AreaType.COUNTRY) {
                if (!AreaManager.validateCountryWaterClaim(newBoundary, player.level())) {
                    player.sendSystemMessage(Component.literal("§c[AwesomeAreas] Country water territory must have land within 20 blocks."));
                    return;
                }
            }

            if (area.getParentId() != null) {
                Area parent = AREA_MANAGER.getAreaById(area.getParentId());
                if (parent != null && parent.getBoundary() != null && !parent.getBoundary().contains(newBoundary)) {
                    Boundary snapped = BlockGridBoundary.clipToParent(newBoundary, parent.getBoundary());
                    if (snapped == null || snapped.getAreaBlocks() == 0) {
                        player.sendSystemMessage(Component.literal("§c[AwesomeAreas] Territory must be located inside parent " + parent.getName() + "."));
                        return;
                    }
                    newBoundary = snapped;
                    player.sendSystemMessage(Component.literal("§e[AwesomeAreas] Border automatically snapped to the inside of parent " + parent.getName() + "."));
                }
            }

            String inGameDate = GameCalendar.formatCurrentDate(player.level());
            boolean success = AREA_MANAGER.updateAreaBoundary(areaId, newBoundary, player.getName().getString(), inGameDate);
            if (success) {
                player.sendSystemMessage(Component.literal("§a✔ [AwesomeAreas] Successfully updated territory borders for " + area.getName() + "! (" + inGameDate + ")"));
            } else {
                player.sendSystemMessage(Component.literal("§c[AwesomeAreas] Could not update border: overlapping with a neighboring territory."));
            }
        });

        // 3. Register Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AreaCommands.register(dispatcher);
        });

        // 4. Register In-World Editor & War System & Villager Tracker
        InWorldTerritoryEditor.initialize();
        // WarService.initialize(AREA_MANAGER); // Temporarily disabled per user request
        VillagerResidentTracker.initialize();

        // 5. Register Server Lifecycle
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            currentServer = server;
            Path dataDir = server.getWorldPath(LevelResource.ROOT).resolve("awesomeareas");
            AREA_MANAGER.initStorage(dataDir);
            LOGGER.info("[AwesomeAreas] Loaded area data from {}", dataDir);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            AREA_MANAGER.save();
            LOGGER.info("[AwesomeAreas] Saved area data.");
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            AREA_MANAGER.clear();
            currentServer = null;
            LOGGER.info("[AwesomeAreas] Cleared server area manager for next world.");
        });

        // 6. Synchronize data when players join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            ServerPlayNetworking.send(player, new SyncAreasPayload(AREA_MANAGER.exportToJsonString()));

            // Auto-detect player home from respawn config if not registered yet
            if (AREA_MANAGER.getHomeByPlayer(player.getUUID()) == null) {
                ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
                if (respawn != null && respawn.respawnData() != null) {
                    BlockPos bedPos = respawn.respawnData().pos();
                    if (bedPos != null) {
                        Home home = new Home(
                                UUID.randomUUID(),
                                player.getUUID(),
                                player.getName().getString(),
                                bedPos.getX(),
                                bedPos.getY(),
                                bedPos.getZ(),
                                player.level().dimension().identifier().toString(),
                                false
                        );
                        AREA_MANAGER.setHome(home);
                    }
                }
            }
        });

        // Broadcast updates to all players when areas change
        AREA_MANAGER.addChangeListener(area -> {
            MinecraftServer server = currentServer;
            if (server != null) {
                String json = AREA_MANAGER.exportToJsonString();
                SyncAreasPayload payload = new SyncAreasPayload(json);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        });

        // Tick loop for territory tracking and village auto-detection
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 40 == 0) { // Check every 2 seconds
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    VillageDetector.checkPlayerPosition(player);
                }
            }
        });

        // Register bed interaction callback to dynamically update player homes
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                BlockPos pos = hitResult.getBlockPos();
                BlockState state = world.getBlockState(pos);
                if (state.is(BlockTags.BEDS)) {
                    // Bed interacted with: create or update player's home position
                    Home existingHome = AREA_MANAGER.getHomeByPlayer(player.getUUID());
                    if (existingHome == null) {
                        Home newHome = new Home(
                                UUID.randomUUID(),
                                player.getUUID(),
                                player.getName().getString(),
                                pos.getX(),
                                pos.getY(),
                                pos.getZ(),
                                world.dimension().identifier().toString(),
                                false
                        );
                        AREA_MANAGER.setHome(newHome);
                    } else if (existingHome.getX() != pos.getX() || existingHome.getY() != pos.getY() || existingHome.getZ() != pos.getZ()) {
                        Home updatedHome = new Home(
                                existingHome.getId(),
                                player.getUUID(),
                                player.getName().getString(),
                                pos.getX(),
                                pos.getY(),
                                pos.getZ(),
                                world.dimension().identifier().toString(),
                                false
                        );
                        AREA_MANAGER.setHome(updatedHome);
                    }
                }
            }
            return InteractionResult.PASS;
        });

        LOGGER.info("[AwesomeAreas] Initialized successfully.");
    }
}
