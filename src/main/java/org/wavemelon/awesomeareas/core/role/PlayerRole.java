package org.wavemelon.awesomeareas.core.role;

/**
 * Hierarchy of administrative and civic roles for players.
 * Ordered from least distinguished to most distinguished.
 */
public enum PlayerRole {
    NONE(0, "None"),
    RESIDENT(1, "Resident"),
    CITIZEN(2, "Citizen"),
    MAYOR(3, "Mayor"),
    COUNTY_EXECUTIVE(4, "County Executive"),
    GOVERNOR(5, "Governor"),
    LEADER(6, "Leader");

    private final int rank;
    private final String defaultTitle;

    PlayerRole(int rank, String defaultTitle) {
        this.rank = rank;
        this.defaultTitle = defaultTitle;
    }

    public int getRank() {
        return rank;
    }

    public int getLevel() {
        return rank;
    }

    public String getDefaultTitle() {
        return defaultTitle;
    }

    public boolean isHigherThan(PlayerRole other) {
        return other == null || this.rank > other.rank;
    }
}
