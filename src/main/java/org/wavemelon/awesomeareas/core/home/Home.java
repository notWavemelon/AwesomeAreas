package org.wavemelon.awesomeareas.core.home;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a registered residence (e.g. bed / spawn point) of a player or villager.
 */
public class Home {
    private final UUID id;
    private final UUID ownerUuid;
    private String ownerName;
    private final int x;
    private final int y;
    private final int z;
    private final String dimension;
    private final boolean isVillager;
    private long registeredTime;

    public Home(UUID id, UUID ownerUuid, String ownerName, int x, int y, int z, String dimension, boolean isVillager) {
        this.id = Objects.requireNonNull(id, "Home ID cannot be null");
        this.ownerUuid = Objects.requireNonNull(ownerUuid, "Owner UUID cannot be null");
        this.ownerName = ownerName != null ? ownerName : "Unknown";
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension != null ? dimension : "minecraft:overworld";
        this.isVillager = isVillager;
        this.registeredTime = System.currentTimeMillis();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getDimension() {
        return dimension;
    }

    public boolean isVillager() {
        return isVillager;
    }

    public long getRegisteredTime() {
        return registeredTime;
    }

    public long getRegisteredTimestamp() {
        return registeredTime;
    }

    public void setRegisteredTime(long registeredTime) {
        this.registeredTime = registeredTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Home home = (Home) o;
        return id.equals(home.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Home[" + ownerName + " at " + x + ", " + y + ", " + z + "]";
    }
}
