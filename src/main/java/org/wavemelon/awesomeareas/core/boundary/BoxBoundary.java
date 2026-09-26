package org.wavemelon.awesomeareas.core.boundary;

import java.util.List;
import java.util.Objects;

/**
 * Axis-aligned rectangular boundary defined by integer block bounds [minX, minZ] to [maxX, maxZ].
 * The outer perimeter segments lie on block seams: [minX, maxX + 1] and [minZ, maxZ + 1].
 */
public class BoxBoundary implements Boundary {
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;

    public BoxBoundary(int x1, int z1, int x2, int z2) {
        this.minX = Math.min(x1, x2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxZ = Math.max(z1, z2);
    }

    @Override
    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    @Override
    public boolean contains(Boundary other) {
        if (other == null) return false;
        return this.minX <= other.getMinX() && this.maxX >= other.getMaxX()
                && this.minZ <= other.getMinZ() && this.maxZ >= other.getMaxZ();
    }

    @Override
    public boolean intersects(Boundary other) {
        if (other == null) return false;
        if (other instanceof BlockGridBoundary bgb) {
            return bgb.intersects(this);
        }
        return this.minX <= other.getMaxX() && this.maxX >= other.getMinX()
                && this.minZ <= other.getMaxZ() && this.maxZ >= other.getMinZ();
    }

    @Override
    public List<BorderSegment> getBorderSegments() {
        return List.of(
                new BorderSegment(minX, minZ, maxX + 1, minZ),
                new BorderSegment(maxX + 1, minZ, maxX + 1, maxZ + 1),
                new BorderSegment(maxX + 1, maxZ + 1, minX, maxZ + 1),
                new BorderSegment(minX, maxZ + 1, minX, minZ)
        );
    }

    @Override
    public int getMinX() {
        return minX;
    }

    @Override
    public int getMaxX() {
        return maxX;
    }

    @Override
    public int getMinZ() {
        return minZ;
    }

    @Override
    public int getMaxZ() {
        return maxZ;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoxBoundary that = (BoxBoundary) o;
        return minX == that.minX && minZ == that.minZ && maxX == that.maxX && maxZ == that.maxZ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minX, minZ, maxX, maxZ);
    }

    @Override
    public String toString() {
        return "BoxBoundary[" + minX + ", " + minZ + " to " + maxX + ", " + maxZ + "]";
    }
}
