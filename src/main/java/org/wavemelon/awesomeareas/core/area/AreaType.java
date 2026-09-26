package org.wavemelon.awesomeareas.core.area;

import org.wavemelon.awesomeareas.core.role.PlayerRole;

import java.util.Locale;

/**
 * Defines the geographic and political category of an area.
 * The system supports flexible custom display names (e.g. "Kingdom", "Parish", "Town")
 * while maintaining strict underlying hierarchy rules.
 */
public enum AreaType {
    COUNTRY("Country", false, true, 0x443388EE),
    PROVINCE("Province", false, false, 0x4433CC66),
    COUNTY("County", true, false, 0x44FFAA00),
    CITY("City", false, true, 0x44FF3333),
    TRIBE("Tribe", false, true, 0x449933CC),
    DISTRICT("District", false, false, 0x4400CCCC);

    private final String defaultDisplayName;
    private final boolean requiresTypeSuffix;
    private final boolean canExistStandalone;
    private final int defaultColor;

    AreaType(String defaultDisplayName, boolean requiresTypeSuffix, boolean canExistStandalone, int defaultColor) {
        this.defaultDisplayName = defaultDisplayName;
        this.requiresTypeSuffix = requiresTypeSuffix;
        this.canExistStandalone = canExistStandalone;
        this.defaultColor = defaultColor;
    }

    public String getDefaultDisplayName() {
        return defaultDisplayName;
    }

    public boolean requiresTypeSuffix() {
        return requiresTypeSuffix;
    }

    public boolean canExistStandalone() {
        return canExistStandalone;
    }

    public int getDefaultColor() {
        return defaultColor;
    }

    public int getHierarchyLevel() {
        return switch (this) {
            case COUNTRY -> 1;
            case PROVINCE -> 2;
            case COUNTY -> 3;
            case CITY, TRIBE -> 4;
            case DISTRICT -> 5;
        };
    }

    public int getLevel() {
        return getHierarchyLevel();
    }

    public PlayerRole getDefaultLeaderRole() {
        return switch (this) {
            case COUNTRY -> PlayerRole.LEADER;
            case PROVINCE -> PlayerRole.GOVERNOR;
            case COUNTY -> PlayerRole.COUNTY_EXECUTIVE;
            case CITY -> PlayerRole.MAYOR;
            case TRIBE -> PlayerRole.LEADER;
            case DISTRICT -> PlayerRole.NONE;
        };
    }

    /**
     * Formats an area name to ensure required suffixes are preserved.
     * For example, for COUNTY, "Grant" becomes "Grant County", while "Grant County" remains unchanged.
     */
    public String formatAreaName(String rawName, String customTypeName) {
        if (rawName == null || rawName.isBlank()) {
            return "Unnamed " + (customTypeName != null ? customTypeName : defaultDisplayName);
        }

        String suffix = (customTypeName != null && !customTypeName.isBlank()) ? customTypeName : defaultDisplayName;
        if (requiresTypeSuffix) {
            String trimmed = rawName.trim();
            if (!trimmed.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) {
                return trimmed + " " + suffix;
            }
            return trimmed;
        }

        return rawName.trim();
    }
}
