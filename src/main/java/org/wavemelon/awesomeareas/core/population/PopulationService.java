package org.wavemelon.awesomeareas.core.population;

import org.wavemelon.awesomeareas.core.area.Area;
import org.wavemelon.awesomeareas.core.home.Home;
import org.wavemelon.awesomeareas.core.storage.AreaManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for calculating population statistics and demographic information.
 * Counts player residents as well as villagers who have claimed beds within the territory.
 */
public class PopulationService {

    public record AreaStatistics(
            int population,
            int homesCount,
            int subAreasCount,
            long areaBlocks,
            List<String> residentNames
    ) {}

    public static AreaStatistics computeStatistics(Area area, AreaManager manager) {
        if (area == null || manager == null) {
            return new AreaStatistics(0, 0, 0, 0, List.of());
        }

        List<Home> homes = manager.getHomesInArea(area);
        List<Area> subAreas = manager.getSubAreas(area.getId());

        long playerResidents = homes.stream().filter(h -> !h.isVillager()).count();
        long villagerHomes = homes.stream().filter(Home::isVillager).count();
        int totalVillagers = Math.max((int) villagerHomes, area.getVillagerCount());

        int totalPop = (int) playerResidents + totalVillagers;
        int totalHomes = homes.size() + (totalVillagers > villagerHomes ? (totalVillagers - (int) villagerHomes) : 0);

        List<String> names = new ArrayList<>(homes.stream().filter(h -> !h.isVillager()).map(Home::getOwnerName).distinct().toList());
        if (totalVillagers > 0) {
            names.add(totalVillagers + (totalVillagers == 1 ? " Villager" : " Villagers") + " (Claimed Beds)");
        }

        long size = area.getBoundary() != null ? area.getBoundary().getAreaBlocks() : 0;

        return new AreaStatistics(
                totalPop,
                totalHomes,
                subAreas.size(),
                size,
                names
        );
    }
}
