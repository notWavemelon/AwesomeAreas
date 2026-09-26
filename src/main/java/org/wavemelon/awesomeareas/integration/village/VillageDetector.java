package org.wavemelon.awesomeareas.integration.village;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wavemelon.awesomeareas.AwesomeAreas;
import org.wavemelon.awesomeareas.core.area.Area;
import org.wavemelon.awesomeareas.core.area.AreaType;
import org.wavemelon.awesomeareas.core.boundary.BoxBoundary;
import org.wavemelon.awesomeareas.core.hierarchy.HierarchyValidator;
import org.wavemelon.awesomeareas.core.history.AreaHistoryEntry;
import org.wavemelon.awesomeareas.core.storage.AreaManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Automatically detects vanilla villages and claims them as City territories.
 */
public class VillageDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(VillageDetector.class);

    public static void checkPlayerPosition(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        ChunkPos center = player.chunkPosition();
        // Check 3x3 chunks around player
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkAccess chunk = level.getChunkSource().getChunk(center.x() + dx, center.z() + dz, ChunkStatus.FULL, false);
                if (chunk != null) {
                    scanChunkForVillages(level, chunk);
                }
            }
        }
    }

    private static void scanChunkForVillages(ServerLevel level, ChunkAccess chunk) {
        AreaManager manager = AwesomeAreas.getAreaManager();

        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            Structure structure = entry.getKey();
            StructureStart start = entry.getValue();

            if (start != null && start.isValid() && isVillageStructure(level, structure)) {
                processVillageStart(manager, start);
            }
        }
    }

    private static boolean isVillageStructure(ServerLevel level, Structure structure) {
        if (structure == null) return false;
        try {
            var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            boolean isVillageTag = registry.getResourceKey(structure)
                    .flatMap(registry::get)
                    .map(holder -> holder.is(StructureTags.VILLAGE))
                    .orElse(false);
            if (isVillageTag) return true;

            Identifier id = registry.getKey(structure);
            return id != null && id.getPath().contains("village");
        } catch (Exception e) {
            return false;
        }
    }

    private static synchronized void processVillageStart(AreaManager manager, StructureStart start) {
        BoundingBox bb = start.getBoundingBox();
        if (bb == null) return;

        // If pieces exist, compute union bounding box to encompass all village buildings & paths
        int minX = bb.minX();
        int minZ = bb.minZ();
        int maxX = bb.maxX();
        int maxZ = bb.maxZ();

        List<StructurePiece> pieces = start.getPieces();
        if (pieces != null && !pieces.isEmpty()) {
            for (StructurePiece piece : pieces) {
                BoundingBox pbb = piece.getBoundingBox();
                if (pbb != null) {
                    minX = Math.min(minX, pbb.minX());
                    minZ = Math.min(minZ, pbb.minZ());
                    maxX = Math.max(maxX, pbb.maxX());
                    maxZ = Math.max(maxZ, pbb.maxZ());
                }
            }
        }

        // Add small margin around village boundary
        minX -= 8;
        minZ -= 8;
        maxX += 8;
        maxZ += 8;

        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;

        // Check if an area already covers this village location
        for (Area existing : manager.getAllAreas()) {
            if (existing.getBoundary() != null) {
                // If existing area contains village center or is within 60 blocks of center
                if (existing.getBoundary().contains(centerX, centerZ) && existing.getType() == AreaType.CITY) {
                    return;
                }
                double ecx = existing.getBoundary().getCenterX();
                double ecz = existing.getBoundary().getCenterZ();
                if (Math.hypot(ecx - centerX, ecz - centerZ) < 60) {
                    return;
                }
            }
        }

        // Generate a unique generic prefix + suffix town name (e.g., Harriotville, Wayford, Riverton)
        int chunkX = start.getChunkPos() != null ? start.getChunkPos().x() : (minX >> 4);
        int chunkZ = start.getChunkPos() != null ? start.getChunkPos().z() : (minZ >> 4);
        String name = VillageNameGenerator.generateUniqueVillageName(manager, chunkX, chunkZ);

        BoxBoundary boundary = new BoxBoundary(minX, minZ, maxX, maxZ);
        Area villageArea = new Area(UUID.randomUUID(), name, AreaType.CITY, boundary);
        villageArea.setAutoGenerated(true);
        villageArea.addHistoryEntry(new AreaHistoryEntry(
                System.currentTimeMillis(),
                "CREATED",
                "Settlement founded as " + name,
                "World Generator",
                boundary
        ));

        // Find potential parent area (County or Province or Country) containing the village center
        List<Area> containing = manager.getAreasAt(centerX, centerZ);
        for (Area parent : containing) {
            if (HierarchyValidator.validateParentChild(villageArea, parent, manager::getAreaById).valid()) {
                villageArea.setParentId(parent.getId());
                break;
            }
        }

        HierarchyValidator.ValidationResult result = manager.addArea(villageArea);
        if (result.valid()) {
            LOGGER.info("[AwesomeAreas] Automatically registered village City '{}' at [{}, {}] to [{}, {}]",
                    name, minX, minZ, maxX, maxZ);
        }
    }
}
