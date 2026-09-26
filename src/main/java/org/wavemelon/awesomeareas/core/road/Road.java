package org.wavemelon.awesomeareas.core.road;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a recognized road or street for navigation and addressing.
 * Contains safety limits to protect against runaway search operations.
 */
public class Road {
    public static final int MAX_ROAD_LENGTH = 5000;
    public static final int MAX_BLOCK_SEARCH_ITERATIONS = 20000;

    private final UUID id;
    private String name;
    private RoadType type;
    private final int startX;
    private final int startZ;
    private final int endX;
    private final int endZ;
    private final String dimension;

    public Road(UUID id, String name, RoadType type, int startX, int startZ, int endX, int endZ, String dimension) {
        this.id = Objects.requireNonNull(id, "Road ID cannot be null");
        this.type = Objects.requireNonNull(type, "RoadType cannot be null");
        this.startX = startX;
        this.startZ = startZ;
        this.endX = endX;
        this.endZ = endZ;
        this.dimension = dimension != null ? dimension : "minecraft:overworld";
        this.name = type.formatRoadName(name);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = type.formatRoadName(name);
    }

    public RoadType getType() {
        return type;
    }

    public void setType(RoadType type) {
        this.type = Objects.requireNonNull(type);
        this.name = type.formatRoadName(this.name);
    }

    public int getStartX() {
        return startX;
    }

    public int getStartZ() {
        return startZ;
    }

    public int getEndX() {
        return endX;
    }

    public int getEndZ() {
        return endZ;
    }

    public String getDimension() {
        return dimension;
    }

    public double getEuclideanLength() {
        double dx = endX - startX;
        double dz = endZ - startZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public String toString() {
        return name + " [" + startX + ", " + startZ + " -> " + endX + ", " + endZ + "]";
    }
}
