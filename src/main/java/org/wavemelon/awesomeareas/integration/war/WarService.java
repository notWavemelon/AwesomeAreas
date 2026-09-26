package org.wavemelon.awesomeareas.integration.war;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import org.wavemelon.awesomeareas.core.area.Area;
import org.wavemelon.awesomeareas.core.area.AreaType;
import org.wavemelon.awesomeareas.core.history.AreaHistoryEntry;
import org.wavemelon.awesomeareas.core.history.GameCalendar;
import org.wavemelon.awesomeareas.core.storage.AreaManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages pillager raid wars against territories.
 * Tracks evil clans, casualty statistics (including repeated player deaths),
 * annexation upon pillager victory, and dissolution upon defender victory.
 */
public class WarService {

    public static class ActiveWar {
        final Raid raid;
        final ServerLevel level;
        final String evilClanName;
        final UUID targetAreaId;
        final String initialAreaName;
        int pillagerCasualties = 0;
        int playerDeaths = 0;
        int villagerDeaths = 0;
        int maxWavesSpawned = 0;
        final long startDayTime;

        public ActiveWar(Raid raid, ServerLevel level, String evilClanName, Area area) {
            this.raid = raid;
            this.level = level;
            this.evilClanName = evilClanName;
            this.targetAreaId = area != null ? area.getId() : null;
            this.initialAreaName = area != null ? area.getName() : "Wilderness";
            this.startDayTime = level.getOverworldClockTime();
        }
    }

    private static final Map<Raid, ActiveWar> ACTIVE_WARS = new ConcurrentHashMap<>();
    private static AreaManager areaManager;

    public static void initialize(AreaManager manager) {
        areaManager = manager;

        // 1. Listen for entity casualties during wars
        ServerLivingEntityEvents.AFTER_DEATH.register(WarService::handleEntityDeath);

        // 2. Tick raids to detect start and completion of wars
        ServerTickEvents.END_LEVEL_TICK.register(WarService::tickWorld);
    }

    private static void handleEntityDeath(LivingEntity entity, DamageSource damageSource) {
        if (entity == null || entity.level().isClientSide()) return;

        if (entity instanceof Raider raider) {
            Raid raid = raider.getCurrentRaid();
            if (raid != null) {
                ActiveWar war = ACTIVE_WARS.get(raid);
                if (war != null) {
                    war.pillagerCasualties++;
                }
            }
        } else if (entity instanceof ServerPlayer player) {
            BlockPos pos = player.blockPosition();
            for (ActiveWar war : ACTIVE_WARS.values()) {
                if (war.level == player.level()) {
                    BlockPos center = war.raid.getCenter();
                    if (pos.closerThan(center, 128.0)) {
                        war.playerDeaths++;
                        break;
                    }
                }
            }
        } else if (entity instanceof Villager villager) {
            BlockPos pos = villager.blockPosition();
            for (ActiveWar war : ACTIVE_WARS.values()) {
                if (war.level == villager.level()) {
                    BlockPos center = war.raid.getCenter();
                    if (pos.closerThan(center, 128.0)) {
                        war.villagerDeaths++;
                        break;
                    }
                }
            }
        }
    }

    private static void tickWorld(ServerLevel level) {
        if (areaManager == null || level == null) return;

        // Check active raids in this level
        // Detect newly spawned raiders belonging to a raid
        for (ServerPlayer player : level.players()) {
            BlockPos pos = player.blockPosition();
            Raid raid = level.getRaidAt(pos);
            if (raid != null && raid.isActive() && !ACTIVE_WARS.containsKey(raid)) {
                startWarForRaid(raid, level);
            }
        }

        // Process active wars
        Iterator<Map.Entry<Raid, ActiveWar>> it = ACTIVE_WARS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Raid, ActiveWar> entry = it.next();
            Raid raid = entry.getKey();
            ActiveWar war = entry.getValue();

            if (raid.getGroupsSpawned() > war.maxWavesSpawned) {
                war.maxWavesSpawned = raid.getGroupsSpawned();
            }

            if (raid.isOver() || raid.isStopped()) {
                concludeWar(war, raid.isVictory(), raid.isLoss());
                it.remove();
            }
        }
    }

    private static void startWarForRaid(Raid raid, ServerLevel level) {
        BlockPos center = raid.getCenter();
        Area targetArea = areaManager.getInnermostAreaAt(center.getX(), center.getZ());
        String evilClanName = EvilClanNameGenerator.generateEvilClanName();

        ActiveWar war = new ActiveWar(raid, level, evilClanName, targetArea);
        ACTIVE_WARS.put(raid, war);

        String targetName = targetArea != null ? targetArea.getName() : "the local settlement";
        String dateStr = GameCalendar.formatCurrentDate(level);

        broadcast(level.getServer(), Component.literal(
                "§c§l[WAR OUTBREAK] §f" + evilClanName + " §chas laid siege to §e" + targetName + "§c! (" + dateStr + ")"
        ));

        if (targetArea != null) {
            targetArea.addHistoryEntry(new AreaHistoryEntry(
                    System.currentTimeMillis(),
                    dateStr,
                    "WAR_DECLARED",
                    "Under siege by " + evilClanName + " in a full-scale pillager war.",
                    evilClanName,
                    targetArea.getBoundary()
            ));
            areaManager.notifyAreaChanged(targetArea);
        }
    }

    private static void concludeWar(ActiveWar war, boolean pillagersWon, boolean defendersWon) {
        MinecraftServer server = war.level.getServer();
        String dateStr = GameCalendar.formatCurrentDate(war.level);
        Area targetArea = war.targetAreaId != null ? areaManager.getAreaById(war.targetAreaId) : null;

        String casualtyReport = String.format(
                "Casualties: %d Pillagers | Defenders: %d player deaths, %d villagers.",
                war.pillagerCasualties, war.playerDeaths, war.villagerDeaths
        );

        if (pillagersWon) {
            // Pillagers defeated the village
            broadcast(server, Component.literal(
                    "§4§l[WAR DEFEAT] §e" + war.initialAreaName + " §4has fallen to §c" + war.evilClanName + "§4!"
            ));
            broadcast(server, Component.literal("§7" + casualtyReport));

            if (targetArea != null) {
                // Annex village to the evil clan
                String originalName = targetArea.getName();
                targetArea.setName(war.evilClanName + " Territory");
                targetArea.setType(AreaType.TRIBE);
                targetArea.setCustomTypeDisplayName("Evil Clan Territory");
                targetArea.setLeader(UUID.randomUUID(), war.evilClanName + " Warlord");

                targetArea.addHistoryEntry(new AreaHistoryEntry(
                        System.currentTimeMillis(),
                        dateStr,
                        "WAR_DEFEAT",
                        "Settlement fell to " + war.evilClanName + " and was annexed. " + casualtyReport,
                        war.evilClanName,
                        targetArea.getBoundary()
                ));
                areaManager.notifyAreaChanged(targetArea);
            }
        } else {
            // Defenders repelled the raid / clan dissolved
            broadcast(server, Component.literal(
                    "§a§l[WAR VICTORY] §e" + war.initialAreaName + " §asurvived the siege! §c" + war.evilClanName + " §awas dissolved!"
            ));
            broadcast(server, Component.literal("§e" + casualtyReport));

            if (targetArea != null) {
                targetArea.addHistoryEntry(new AreaHistoryEntry(
                        System.currentTimeMillis(),
                        dateStr,
                        "WAR_VICTORY",
                        war.evilClanName + " was routed and dissolved after " + war.maxWavesSpawned + " waves. " + casualtyReport,
                        "Defenders",
                        targetArea.getBoundary()
                ));
                areaManager.notifyAreaChanged(targetArea);
            }
        }
    }

    private static void broadcast(MinecraftServer server, Component msg) {
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(msg, false);
        }
    }
}
