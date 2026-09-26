package org.wavemelon.awesomeareas.core.road;

import java.util.Locale;

/**
 * Common types of roads and transit lines.
 * Must always be included as a suffix in the displayed name.
 */
public enum RoadType {
    ROAD("Road"),
    STREET("Street"),
    AVENUE("Avenue"),
    BOULEVARD("Boulevard"),
    HIGHWAY("Highway"),
    LANE("Lane"),
    TRAIL("Trail"),
    WAY("Way");

    private final String displayName;

    RoadType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String formatRoadName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "Unnamed " + displayName;
        }
        String trimmed = rawName.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).endsWith(displayName.toLowerCase(Locale.ROOT))) {
            return trimmed + " " + displayName;
        }
        return trimmed;
    }
}
