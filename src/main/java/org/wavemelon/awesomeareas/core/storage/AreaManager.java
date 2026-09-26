package org.wavemelon.awesomeareas.core.storage;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.wavemelon.awesomeareas.core.area.Area;
import org.wavemelon.awesomeareas.core.area.AreaType;
import org.wavemelon.awesomeareas.core.boundary.BlockGridBoundary;
import org.wavemelon.awesomeareas.core.boundary.Boundary;
import org.wavemelon.awesomeareas.core.boundary.BoxBoundary;
import org.wavemelon.awesomeareas.core.boundary.PolygonBoundary;
import org.wavemelon.awesomeareas.core.hierarchy.HierarchyValidator;
import org.wavemelon.awesomeareas.core.history.AreaHistoryEntry;
import org.wavemelon.awesomeareas.core.home.Home;
import org.wavemelon.awesomeareas.core.role.PlayerRole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe central storage and registry for all areas, homes, and boundaries in the world.
 */
public class AreaManager {

    private final Map<UUID, Area> areasById = new ConcurrentHashMap<>();
    private final Map<String, UUID> areaIdByName = new ConcurrentHashMap<>();
    private final Map<UUID, List<UUID>> childrenByParentId = new ConcurrentHashMap<>();

    private final Map<UUID, UUID> homesByPlayerId = new ConcurrentHashMap<>();
    private final Map<UUID, Home> homesById = new ConcurrentHashMap<>();

    private final List<Consumer<Area>> areaChangeListeners = new CopyOnWriteArrayList<>();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Path storageDir;

    public AreaManager() {
    }

    public void setStorageDir(Path dir) {
        this.storageDir = dir;
    }

    public synchronized void clear() {
        areasById.clear();
        areaIdByName.clear();
        childrenByParentId.clear();
        homesById.clear();
        homesByPlayerId.clear();
        storageDir = null;
    }

    public synchronized void initStorage(Path dir) {
        clear();
        this.storageDir = dir;
        load();
    }

    public void addChangeListener(Consumer<Area> listener) {
        areaChangeListeners.add(listener);
    }

    public synchronized void notifyAreaChanged(Area area) {
        save();
        notifyChange(area);
    }

    private void notifyChange(Area area) {
        for (Consumer<Area> listener : areaChangeListeners) {
            try {
                listener.accept(area);
            } catch (Exception e) {
                // Log and ignore listener error
            }
        }
    }

    public synchronized HierarchyValidator.ValidationResult addArea(Area area) {
        if (area == null) {
            return HierarchyValidator.ValidationResult.invalid("Area cannot be null");
        }

        // Snap border to parent if area has parent and slightly misstepped outside
        if (area.getParentId() != null) {
            Area parent = getAreaById(area.getParentId());
            if (parent != null && parent.getBoundary() != null && area.getBoundary() != null && !parent.getBoundary().contains(area.getBoundary())) {
                Boundary snapped = BlockGridBoundary.clipToParent(area.getBoundary(), parent.getBoundary());
                if (snapped != null && snapped.getAreaBlocks() > 0) {
                    area.setBoundary(snapped);
                }
            }
        }

        HierarchyValidator.ValidationResult result = HierarchyValidator.validate(area, this::getAreaById);
        if (!result.valid()) {
            return result;
        }

        // Clean name check for duplicates
        String cleanName = cleanName(area.getName()).toLowerCase(Locale.ROOT);
        if (areaIdByName.containsKey(cleanName)) {
            return HierarchyValidator.ValidationResult.invalid("An area with the name '" + area.getName() + "' already exists.");
        }

        // Check peer overlap: no two areas of the same type/peer level can intersect
        if (area.getBoundary() != null) {
            for (Area existing : areasById.values()) {
                if (existing.getId().equals(area.getId())) continue;
                if (existing.getBoundary() == null) continue;
                if (existing.getType() == area.getType()) {
                    if (existing.getBoundary().intersects(area.getBoundary())) {
                        return HierarchyValidator.ValidationResult.invalid(
                                area.getType().getDefaultDisplayName() + " '" + area.getName() +
                                "' overlaps with existing " + existing.getType().getDefaultDisplayName() + " '" + existing.getName() + "'."
                        );
                    }
                }
            }
        }

        areasById.put(area.getId(), area);
        areaIdByName.put(cleanName, area.getId());

        if (area.getParentId() != null) {
            childrenByParentId.computeIfAbsent(area.getParentId(), k -> new ArrayList<>()).add(area.getId());
        }

        save();
        notifyChange(area);
        return HierarchyValidator.ValidationResult.success();
    }

    public synchronized boolean removeArea(UUID id) {
        Area area = areasById.remove(id);
        if (area == null) return false;

        areaIdByName.remove(cleanName(area.getName()).toLowerCase(Locale.ROOT));

        if (area.getParentId() != null) {
            List<UUID> siblings = childrenByParentId.get(area.getParentId());
            if (siblings != null) {
                siblings.remove(id);
            }
        }

        // Dissolve or reparent children
        List<UUID> children = childrenByParentId.remove(id);
        if (children != null) {
            for (UUID childId : children) {
                Area child = areasById.get(childId);
                if (child != null) {
                    child.setParentId(area.getParentId());
                    if (area.getParentId() != null) {
                        childrenByParentId.computeIfAbsent(area.getParentId(), k -> new ArrayList<>()).add(childId);
                    }
                }
            }
        }

        save();
        notifyChange(null);
        return true;
    }

    public Area getAreaById(UUID id) {
        if (id == null) return null;
        return areasById.get(id);
    }

    public Area getAreaByName(String name) {
        if (name == null) return null;
        String cleaned = cleanName(name);
        if (cleaned.isEmpty()) return null;

        UUID id = areaIdByName.get(cleaned.toLowerCase(Locale.ROOT));
        if (id != null) {
            return areasById.get(id);
        }

        // Fallback: match without suffix or case-insensitive search
        String search = cleaned.toLowerCase(Locale.ROOT);
        for (Area area : areasById.values()) {
            if (area.getName().toLowerCase(Locale.ROOT).equals(search)) {
                return area;
            }
        }
        for (Area area : areasById.values()) {
            if (area.getName().toLowerCase(Locale.ROOT).startsWith(search)) {
                return area;
            }
        }
        return null;
    }

    public synchronized boolean renameArea(UUID id, String newName, String actorName) {
        return renameArea(id, newName, actorName, "");
    }

    public synchronized boolean renameArea(UUID id, String newName, String actorName, String inGameDate) {
        Area area = areasById.get(id);
        if (area == null) return false;

        String cleaned = cleanName(newName);
        String oldCleaned = cleanName(area.getName());

        if (cleaned.equalsIgnoreCase(oldCleaned)) {
            return true;
        }

        // Check uniqueness
        if (areaIdByName.containsKey(cleaned.toLowerCase(Locale.ROOT))) {
            return false;
        }

        areaIdByName.remove(oldCleaned.toLowerCase(Locale.ROOT));
        area.rename(cleaned, actorName, inGameDate);
        areaIdByName.put(cleanName(area.getName()).toLowerCase(Locale.ROOT), id);

        save();
        notifyChange(area);
        return true;
    }

    public synchronized boolean updateAreaBoundary(UUID id, Boundary newBoundary, String actorName, String inGameDate) {
        Area area = areasById.get(id);
        if (area == null || newBoundary == null) return false;

        // Snap border to parent if area has parent and slightly misstepped outside
        if (area.getParentId() != null) {
            Area parent = getAreaById(area.getParentId());
            if (parent != null && parent.getBoundary() != null && !parent.getBoundary().contains(newBoundary)) {
                Boundary snapped = BlockGridBoundary.clipToParent(newBoundary, parent.getBoundary());
                if (snapped != null && snapped.getAreaBlocks() > 0) {
                    newBoundary = snapped;
                }
            }
        }

        // Ensure boundary does not overlap with existing peer areas
        for (Area existing : areasById.values()) {
            if (existing.getId().equals(id)) continue;
            if (existing.getBoundary() == null) continue;
            if (existing.getType() == area.getType()) {
                if (existing.getBoundary().intersects(newBoundary)) {
                    return false;
                }
            }
        }

        area.updateBoundary(newBoundary, actorName, inGameDate);
        save();
        notifyChange(area);
        return true;
    }

    public static String cleanName(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        while ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
               (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            if (trimmed.length() >= 2) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            } else {
                break;
            }
        }
        return trimmed;
    }

    public Collection<Area> getAllAreas() {
        return Collections.unmodifiableCollection(areasById.values());
    }

    public List<Area> getRootAreas() {
        List<Area> roots = new ArrayList<>();
        for (Area area : areasById.values()) {
            if (area.getParentId() == null) {
                roots.add(area);
            }
        }
        return roots;
    }

    public List<Area> getSubAreas(UUID parentId) {
        List<UUID> children = childrenByParentId.getOrDefault(parentId, Collections.emptyList());
        List<Area> result = new ArrayList<>();
        for (UUID cid : children) {
            Area child = areasById.get(cid);
            if (child != null) {
                result.add(child);
            }
        }
        return result;
    }

    public Area getInnermostAreaAt(int x, int z) {
        Area candidate = null;
        for (Area area : areasById.values()) {
            if (area.getBoundary() != null && area.getBoundary().contains(x, z)) {
                if (candidate == null || area.getType().getLevel() > candidate.getType().getLevel()) {
                    candidate = area;
                }
            }
        }
        return candidate;
    }

    public List<Area> getAllAreasAt(int x, int z) {
        List<Area> result = new ArrayList<>();
        for (Area area : areasById.values()) {
            if (area.getBoundary() != null && area.getBoundary().contains(x, z)) {
                result.add(area);
            }
        }
        result.sort(Comparator.comparingInt(a -> -a.getType().getLevel()));
        return result;
    }

    public List<Area> getAreasAt(int x, int z) {
        return getAllAreasAt(x, z);
    }

    public synchronized void setHome(Home home) {
        if (home == null) return;
        homesById.put(home.getId(), home);
        if (!home.isVillager()) {
            homesByPlayerId.put(home.getOwnerUuid(), home.getId());
        }
        save();
    }

    public Home getHomeById(UUID homeId) {
        return homesById.get(homeId);
    }

    public Home getHomeByPlayer(UUID playerUuid) {
        UUID homeId = homesByPlayerId.get(playerUuid);
        if (homeId == null) return null;
        return homesById.get(homeId);
    }

    public Collection<Home> getAllHomes() {
        return Collections.unmodifiableCollection(homesById.values());
    }

    public List<Home> getHomesInArea(Area area) {
        if (area == null) return Collections.emptyList();
        return getHomesInArea(area.getId());
    }

    public List<Home> getHomesInArea(UUID areaId) {
        Area area = getAreaById(areaId);
        if (area == null || area.getBoundary() == null) return Collections.emptyList();

        List<Home> inArea = new ArrayList<>();
        for (Home home : homesById.values()) {
            if (area.getBoundary().contains(home.getX(), home.getZ())) {
                inArea.add(home);
            }
        }
        return inArea;
    }

    public int getPopulation(UUID areaId) {
        Area area = getAreaById(areaId);
        if (area == null) return 0;

        int totalVillagers = area.getVillagerCount();
        for (Area sub : getSubAreas(areaId)) {
            totalVillagers += sub.getVillagerCount();
        }

        long playerResidents = getHomesInArea(areaId).stream()
                .filter(h -> !h.isVillager())
                .count();

        return (int) playerResidents + totalVillagers;
    }

    public PlayerRole getPlayerRole(UUID playerUuid) {
        if (playerUuid == null) return PlayerRole.NONE;

        PlayerRole highestRole = PlayerRole.NONE;

        for (Area area : areasById.values()) {
            if (playerUuid.equals(area.getLeaderUuid())) {
                PlayerRole leaderRole = area.getType().getDefaultLeaderRole();
                if (leaderRole.getLevel() > highestRole.getLevel()) {
                    highestRole = leaderRole;
                }
            }
        }

        if (highestRole != PlayerRole.NONE) {
            return highestRole;
        }

        Home home = getHomeByPlayer(playerUuid);
        if (home != null) {
            Area homeArea = getInnermostAreaAt(home.getX(), home.getZ());
            if (homeArea != null) {
                return PlayerRole.CITIZEN;
            } else {
                return PlayerRole.RESIDENT;
            }
        }

        return PlayerRole.NONE;
    }

    public static boolean canEditBorders(ServerPlayer player, Area area, AreaManager manager) {
        if (player == null || area == null) return false;
        boolean isOp = player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        return canEditBorders(player.getUUID(), area, manager, isOp);
    }

    public static boolean canEditBorders(UUID playerUuid, Area area, AreaManager manager, boolean isOp) {
        if (isOp) return true;
        if (playerUuid == null || area == null) return false;

        AreaType type = area.getType();
        return switch (type) {
            case COUNTRY -> false;
            case PROVINCE -> isLeaderOfAncestorType(playerUuid, area, manager, AreaType.COUNTRY);
            case COUNTY -> isLeaderOfAncestorType(playerUuid, area, manager, AreaType.PROVINCE) ||
                           isLeaderOfAncestorType(playerUuid, area, manager, AreaType.COUNTRY);
            case CITY -> isAreaLeader(playerUuid, area) ||
                         isLeaderOfAncestorType(playerUuid, area, manager, AreaType.COUNTY) ||
                         isLeaderOfAncestorType(playerUuid, area, manager, AreaType.PROVINCE) ||
                         isLeaderOfAncestorType(playerUuid, area, manager, AreaType.COUNTRY);
            case DISTRICT, TRIBE -> isAreaLeader(playerUuid, area) ||
                                    isLeaderOfAncestorType(playerUuid, area, manager, AreaType.CITY);
        };
    }

    private static boolean isAreaLeader(UUID playerUuid, Area area) {
        return area != null && playerUuid.equals(area.getLeaderUuid());
    }

    private static boolean isLeaderOfAncestorType(UUID playerUuid, Area area, AreaManager manager, AreaType targetType) {
        Area current = area;
        while (current != null && current.getParentId() != null) {
            current = manager.getAreaById(current.getParentId());
            if (current != null && current.getType() == targetType) {
                if (playerUuid.equals(current.getLeaderUuid())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean validateCountryWaterClaim(Boundary boundary, Level level) {
        if (boundary == null || level == null) return true;

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        int minX = boundary.getMinX();
        int maxX = boundary.getMaxX();
        int minZ = boundary.getMinZ();
        int maxZ = boundary.getMaxZ();

        for (int x = minX; x <= maxX; x += 4) {
            for (int z = minZ; z <= maxZ; z += 4) {
                if (!boundary.contains(x, z)) continue;

                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
                mpos.set(x, y - 1, z);
                BlockState state = level.getBlockState(mpos);

                if (state.is(Blocks.WATER) || state.getFluidState().is(FluidTags.WATER)) {
                    boolean hasNearbyLand = false;
                    for (int dx = -20; dx <= 20; dx += 5) {
                        for (int dz = -20; dz <= 20; dz += 5) {
                            int cy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x + dx, z + dz);
                            mpos.set(x + dx, cy - 1, z + dz);
                            BlockState cstate = level.getBlockState(mpos);
                            if (!cstate.isAir() && !cstate.is(Blocks.WATER) && !cstate.getFluidState().is(FluidTags.WATER)) {
                                hasNearbyLand = true;
                                break;
                            }
                        }
                    }
                    if (!hasNearbyLand) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void rebuildHierarchyTree() {
        areaIdByName.clear();
        childrenByParentId.clear();

        for (Area area : areasById.values()) {
            areaIdByName.put(cleanName(area.getName()).toLowerCase(Locale.ROOT), area.getId());
            if (area.getParentId() != null) {
                childrenByParentId.computeIfAbsent(area.getParentId(), k -> new ArrayList<>()).add(area.getId());
            }
        }
    }

    public synchronized void save() {
        if (storageDir == null) return;

        try {
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }

            Path file = storageDir.resolve("awesomeareas.json");
            Path tempFile = storageDir.resolve("awesomeareas.json.tmp");

            String json = exportToJsonString();
            Files.writeString(tempFile, json);
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[AwesomeAreas] Failed to save territory data: " + e.getMessage());
        }
    }

    public synchronized void load() {
        if (storageDir == null) return;
        Path file = storageDir.resolve("awesomeareas.json");
        if (!Files.exists(file)) return;

        try {
            String json = Files.readString(file);
            loadFromJsonString(json);
        } catch (IOException e) {
            System.err.println("[AwesomeAreas] Failed to load territory data: " + e.getMessage());
        }
    }

    public static JsonObject serializeBoundary(Boundary boundary) {
        if (boundary == null) return null;
        JsonObject b = new JsonObject();
        if (boundary instanceof BlockGridBoundary bgb) {
            b.addProperty("type", "block_grid");
            JsonArray boxArr = new JsonArray();
            for (BoxBoundary box : bgb.getBoxes()) {
                JsonObject boxObj = new JsonObject();
                boxObj.addProperty("minX", box.getMinX());
                boxObj.addProperty("minZ", box.getMinZ());
                boxObj.addProperty("maxX", box.getMaxX());
                boxObj.addProperty("maxZ", box.getMaxZ());
                boxArr.add(boxObj);
            }
            b.add("boxes", boxArr);
        } else if (boundary instanceof BoxBoundary box) {
            b.addProperty("type", "box");
            b.addProperty("minX", box.getMinX());
            b.addProperty("minZ", box.getMinZ());
            b.addProperty("maxX", box.getMaxX());
            b.addProperty("maxZ", box.getMaxZ());
        } else if (boundary instanceof PolygonBoundary poly) {
            b.addProperty("type", "polygon");
            JsonArray vArr = new JsonArray();
            for (PolygonBoundary.Point2D pt : poly.getVertices()) {
                JsonObject p = new JsonObject();
                p.addProperty("x", pt.x());
                p.addProperty("z", pt.z());
                vArr.add(p);
            }
            b.add("vertices", vArr);
        }
        return b;
    }

    public static Boundary deserializeBoundary(JsonObject bObj) {
        if (bObj == null || !bObj.has("type")) return null;
        String bType = bObj.get("type").getAsString();
        if ("block_grid".equalsIgnoreCase(bType)) {
            List<BoxBoundary> boxes = new ArrayList<>();
            if (bObj.has("boxes")) {
                for (JsonElement el : bObj.getAsJsonArray("boxes")) {
                    JsonObject o = el.getAsJsonObject();
                    boxes.add(new BoxBoundary(
                            o.get("minX").getAsInt(),
                            o.get("minZ").getAsInt(),
                            o.get("maxX").getAsInt(),
                            o.get("maxZ").getAsInt()
                    ));
                }
            }
            return new BlockGridBoundary(boxes);
        } else if ("polygon".equalsIgnoreCase(bType)) {
            List<PolygonBoundary.Point2D> pts = new ArrayList<>();
            for (JsonElement ptEl : bObj.getAsJsonArray("vertices")) {
                JsonObject pt = ptEl.getAsJsonObject();
                pts.add(new PolygonBoundary.Point2D(pt.get("x").getAsInt(), pt.get("z").getAsInt()));
            }
            return new PolygonBoundary(pts);
        } else {
            return new BoxBoundary(
                    bObj.get("minX").getAsInt(),
                    bObj.get("minZ").getAsInt(),
                    bObj.get("maxX").getAsInt(),
                    bObj.get("maxZ").getAsInt()
            );
        }
    }

    public synchronized String exportToJsonString() {
        JsonObject root = new JsonObject();

        JsonArray areasArr = new JsonArray();
        for (Area area : areasById.values()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", area.getId().toString());
            obj.addProperty("name", area.getName());
            obj.addProperty("type", area.getType().name());
            if (area.getCustomTypeDisplayName() != null) {
                obj.addProperty("customTypeDisplayName", area.getCustomTypeDisplayName());
            }
            if (area.getParentId() != null) {
                obj.addProperty("parentId", area.getParentId().toString());
            }
            if (area.getLeaderUuid() != null) {
                obj.addProperty("leaderUuid", area.getLeaderUuid().toString());
                obj.addProperty("leaderName", area.getLeaderName());
            }
            if (area.getCapitalAreaId() != null) {
                obj.addProperty("capitalAreaId", area.getCapitalAreaId().toString());
            }
            obj.addProperty("color", area.getColor());
            obj.addProperty("autoGenerated", area.isAutoGenerated());
            obj.addProperty("createdTimestamp", area.getCreatedTime());
            obj.addProperty("villagerCount", area.getVillagerCount());

            // Past names
            JsonArray pastNamesArr = new JsonArray();
            for (String pn : area.getPastNames()) {
                pastNamesArr.add(pn);
            }
            obj.add("pastNames", pastNamesArr);

            // History entries
            JsonArray historyArr = new JsonArray();
            for (AreaHistoryEntry entry : area.getHistory()) {
                JsonObject hObj = new JsonObject();
                hObj.addProperty("timestamp", entry.getTimestamp());
                hObj.addProperty("inGameDate", entry.getInGameDate());
                hObj.addProperty("eventType", entry.getEventType());
                hObj.addProperty("description", entry.getDescription());
                hObj.addProperty("actorName", entry.getActorName());
                if (entry.getSnapshotBoundary() != null) {
                    hObj.add("snapshotBoundary", serializeBoundary(entry.getSnapshotBoundary()));
                }
                historyArr.add(hObj);
            }
            obj.add("history", historyArr);

            if (area.getBoundary() != null) {
                obj.add("boundary", serializeBoundary(area.getBoundary()));
            }

            areasArr.add(obj);
        }
        root.add("areas", areasArr);

        JsonArray homesArr = new JsonArray();
        for (Home home : homesById.values()) {
            JsonObject hObj = new JsonObject();
            hObj.addProperty("id", home.getId().toString());
            hObj.addProperty("ownerUuid", home.getOwnerUuid().toString());
            hObj.addProperty("ownerName", home.getOwnerName());
            hObj.addProperty("x", home.getX());
            hObj.addProperty("y", home.getY());
            hObj.addProperty("z", home.getZ());
            hObj.addProperty("dimension", home.getDimension());
            hObj.addProperty("villager", home.isVillager());
            homesArr.add(hObj);
        }
        root.add("homes", homesArr);

        return gson.toJson(root);
    }

    public synchronized void loadFromJsonString(String json) {
        if (json == null || json.isBlank()) return;

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        areasById.clear();
        areaIdByName.clear();
        childrenByParentId.clear();
        homesById.clear();
        homesByPlayerId.clear();

        if (root.has("areas")) {
            for (JsonElement el : root.getAsJsonArray("areas")) {
                JsonObject obj = el.getAsJsonObject();
                UUID id = UUID.fromString(obj.get("id").getAsString());
                String name = obj.get("name").getAsString();
                AreaType type = AreaType.valueOf(obj.get("type").getAsString());

                Boundary boundary = null;
                if (obj.has("boundary")) {
                    boundary = deserializeBoundary(obj.getAsJsonObject("boundary"));
                }

                Area area = new Area(id, name, type, boundary != null ? boundary : new BoxBoundary(0, 0, 0, 0));
                if (obj.has("customTypeDisplayName")) {
                    area.setCustomTypeDisplayName(obj.get("customTypeDisplayName").getAsString());
                }
                if (obj.has("parentId")) {
                    area.setParentId(UUID.fromString(obj.get("parentId").getAsString()));
                }
                if (obj.has("leaderUuid")) {
                    UUID leaderUuid = UUID.fromString(obj.get("leaderUuid").getAsString());
                    String leaderName = obj.has("leaderName") ? obj.get("leaderName").getAsString() : "Unknown";
                    area.setLeader(leaderUuid, leaderName);
                }
                if (obj.has("capitalAreaId")) {
                    area.setCapitalAreaId(UUID.fromString(obj.get("capitalAreaId").getAsString()));
                }
                if (obj.has("color")) {
                    area.setColor(obj.get("color").getAsInt());
                }
                if (obj.has("autoGenerated")) {
                    area.setAutoGenerated(obj.get("autoGenerated").getAsBoolean());
                }
                if (obj.has("createdTimestamp")) {
                    area.setCreatedTime(obj.get("createdTimestamp").getAsLong());
                }
                if (obj.has("villagerCount")) {
                    area.setVillagerCount(obj.get("villagerCount").getAsInt());
                }
                if (obj.has("pastNames")) {
                    for (JsonElement p : obj.getAsJsonArray("pastNames")) {
                        area.addPastName(p.getAsString());
                    }
                }
                if (obj.has("history")) {
                    for (JsonElement h : obj.getAsJsonArray("history")) {
                        JsonObject hObj = h.getAsJsonObject();
                        Boundary snapshot = hObj.has("snapshotBoundary") ? deserializeBoundary(hObj.getAsJsonObject("snapshotBoundary")) : null;
                        area.addHistoryEntry(new AreaHistoryEntry(
                                hObj.get("timestamp").getAsLong(),
                                hObj.get("inGameDate").getAsString(),
                                hObj.get("eventType").getAsString(),
                                hObj.get("description").getAsString(),
                                hObj.get("actorName").getAsString(),
                                snapshot
                        ));
                    }
                }

                areasById.put(id, area);
            }
        }

        rebuildHierarchyTree();

        if (root.has("homes")) {
            for (JsonElement el : root.getAsJsonArray("homes")) {
                JsonObject hObj = el.getAsJsonObject();
                Home home = new Home(
                        UUID.fromString(hObj.get("id").getAsString()),
                        UUID.fromString(hObj.get("ownerUuid").getAsString()),
                        hObj.get("ownerName").getAsString(),
                        hObj.get("x").getAsInt(),
                        hObj.get("y").getAsInt(),
                        hObj.get("z").getAsInt(),
                        hObj.get("dimension").getAsString(),
                        hObj.has("villager") && hObj.get("villager").getAsBoolean()
                );
                homesById.put(home.getId(), home);
                if (!home.isVillager()) {
                    homesByPlayerId.put(home.getOwnerUuid(), home.getId());
                }
            }
        }

        notifyChange(null);
    }
}
