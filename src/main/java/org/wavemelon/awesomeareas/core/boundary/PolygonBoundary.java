package org.wavemelon.awesomeareas.core.boundary;

import java.util.List;
import java.util.Objects;

/**
 * Arbitrary polygon boundary defined by a sequence of (X, Z) vertices.
 * Automatically converted to discrete block-grid cells conforming to Minecraft's block grid,
 * with outer border lines running along block intersections.
 */
public class PolygonBoundary implements Boundary {
    public record Point2D(int x, int z) {}

    private final List<Point2D> vertices;
    private final BlockGridBoundary gridBoundary;

    public PolygonBoundary(List<Point2D> points) {
        if (points == null || points.size() < 3) {
            throw new IllegalArgumentException("A polygon boundary requires at least 3 vertices.");
        }
        this.vertices = List.copyOf(points);
        this.gridBoundary = BlockGridBoundary.fromPolygon(this.vertices);
    }

    public List<Point2D> getVertices() {
        return vertices;
    }

    public BlockGridBoundary getGridBoundary() {
        return gridBoundary;
    }

    @Override
    public boolean contains(int x, int z) {
        return gridBoundary.contains(x, z);
    }

    @Override
    public boolean contains(Boundary other) {
        return gridBoundary.contains(other);
    }

    @Override
    public boolean intersects(Boundary other) {
        return gridBoundary.intersects(other);
    }

    @Override
    public List<BorderSegment> getBorderSegments() {
        return gridBoundary.getBorderSegments();
    }

    @Override
    public int getMinX() {
        return gridBoundary.getMinX();
    }

    @Override
    public int getMaxX() {
        return gridBoundary.getMaxX();
    }

    @Override
    public int getMinZ() {
        return gridBoundary.getMinZ();
    }

    @Override
    public int getMaxZ() {
        return gridBoundary.getMaxZ();
    }

    @Override
    public long getAreaBlocks() {
        return gridBoundary.getAreaBlocks();
    }

    public static boolean isPointInPolygon(double px, double pz, List<Point2D> vertices) {
        boolean inside = false;
        int n = vertices.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = vertices.get(i).x();
            double zi = vertices.get(i).z();
            double xj = vertices.get(j).x();
            double zj = vertices.get(j).z();

            boolean intersect = ((zi > pz) != (zj > pz)) &&
                    (px < (xj - xi) * (pz - zi) / (zj - zi) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PolygonBoundary that = (PolygonBoundary) o;
        return Objects.equals(vertices, that.vertices);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vertices);
    }
}
