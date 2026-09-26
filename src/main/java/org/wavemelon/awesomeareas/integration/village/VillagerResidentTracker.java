package org.wavemelon.awesomeareas.integration.village;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import org.wavemelon.awesomeareas.AwesomeAreas;
import org.wavemelon.awesomeareas.core.area.Area;
import org.wavemelon.awesomeareas.core.storage.AreaManager;

import java.util.*;

/**
 * Periodically scans loaded villagers to count them as residents of areas based on their claimed beds.
 */
public class VillagerResidentTracker {
    private static int tickCounter = 0;

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter % 100 != 0) return; // Scan every 5 seconds (100 ticks)

            AreaManager manager = AwesomeAreas.getAreaManager();
            if (manager == null) return;

            Map<UUID, Integer> counts = new HashMap<>();

            for (ServerLevel level : server.getAllLevels()) {
                Set<UUID> countedVillagers = new HashSet<>();

                for (ServerPlayer player : level.players()) {
                    AABB box = new AABB(player.blockPosition()).inflate(128.0);
                    List<Villager> villagers = level.getEntitiesOfClass(Villager.class, box);

                    for (Villager villager : villagers) {
                        if (!countedVillagers.add(villager.getUUID())) continue;

                        BlockPos pos = villager.blockPosition();
                        Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
                        if (home.isPresent() && home.get().dimension() == level.dimension()) {
                            pos = home.get().pos();
                        }

                        Area area = manager.getInnermostAreaAt(pos.getX(), pos.getZ());
                        if (area != null) {
                            counts.merge(area.getId(), 1, Integer::sum);
                        }
                    }
                }
            }

            for (Area area : manager.getAllAreas()) {
                int newCount = counts.getOrDefault(area.getId(), 0);
                if (newCount > 0 && newCount != area.getVillagerCount()) {
                    area.setVillagerCount(newCount);
                    manager.notifyAreaChanged(area);
                }
            }
        });
    }
}
