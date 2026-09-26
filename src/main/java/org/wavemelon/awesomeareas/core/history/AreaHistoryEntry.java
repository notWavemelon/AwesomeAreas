package org.wavemelon.awesomeareas.core.history;

import org.wavemelon.awesomeareas.core.boundary.Boundary;

/**
 * Represents a historical event or snapshot for an area (e.g., creation, renaming, claim modifications, wars).
 */
public class AreaHistoryEntry {
    private final long timestamp;
    private final String inGameDate;
    private final String eventType;
    private final String description;
    private final String actorName;
    private final Boundary snapshotBoundary;

    public AreaHistoryEntry(long timestamp, String inGameDate, String eventType, String description, String actorName, Boundary snapshotBoundary) {
        this.timestamp = timestamp;
        this.inGameDate = inGameDate != null ? inGameDate : "";
        this.eventType = eventType != null ? eventType : "EVENT";
        this.description = description != null ? description : "";
        this.actorName = actorName != null ? actorName : "System";
        this.snapshotBoundary = snapshotBoundary;
    }

    public AreaHistoryEntry(long timestamp, String eventType, String description, String actorName, Boundary snapshotBoundary) {
        this(timestamp, "", eventType, description, actorName, snapshotBoundary);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getInGameDate() {
        return inGameDate;
    }

    public String getEventType() {
        return eventType;
    }

    public String getDescription() {
        return description;
    }

    public String getActorName() {
        return actorName;
    }

    public Boundary getSnapshotBoundary() {
        return snapshotBoundary;
    }
}
