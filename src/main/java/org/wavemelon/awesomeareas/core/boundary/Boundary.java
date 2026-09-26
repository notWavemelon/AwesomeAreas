package org.wavemelon.awesomeareas.core.boundary;

import java.util.List;

/**
 * Common interface for discrete 2D X/Z geographic boundaries conforming to the Minecraft block grid.
 * Boundaries are defined in terms of block columns, with perimeter border lines running
 * precisely along the intersections between blocks.
 */
public interface Boundary {

    record BorderSegment(int x0, int z0, int x1, int z1) {
        public boolean isHorizontal() {
            return z0 == z1;
        }

        public boolean isVertical() {
            return x0 == x1;
        }

        public int minX() {
            return Math.min(x0, x1);
        }

        public int maxX() {
            return Math.max(x0, x1);
        }

        public int minZ() {
            return Math.min(z0, z1);
        }

        public int maxZ() {
            return Math.max(z0, z1);
        }
    }

    boolean contains(int x, int z);

    boolean contains(Boundary other);

    boolean intersects(Boundary other);

    int getMinX();

    int getMaxX();

    int getMinZ();

    int getMaxZ();

    List<BorderSegment> getBorderSegments();

    default double getCenterX() {
        return (getMinX() + getMaxX()) / 2.0;
    }

    default double getCenterZ() {
        return (getMinZ() + getMaxZ()) / 2.0;
    }

    default int getWidth() {
        return getMaxX() - getMinX() + 1;
    }

    default int getLength() {
        return getMaxZ() - getMinZ() + 1;
    }

    default long getAreaBlocks() {
        return (long) getWidth() * (long) getLength();
    }

    /**
     * Clips/snaps this boundary to fit completely inside the parent boundary.
     * Any missteps or protrusions outside the parent are smoothly trimmed away.
     */
    default Boundary clipToParent(Boundary parent) {
        return BlockGridBoundary.clipToParent(this, parent);
    }
}
