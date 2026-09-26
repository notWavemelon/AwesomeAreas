package org.wavemelon.awesomeareas.core.boundary;

import java.util.*;

/**
 * Discrete block-grid boundary composed of one or more non-overlapping rectangular block boxes.
 * Outer border segments run exclusively along integer block intersections (seams).
 */
public class BlockGridBoundary implements Boundary {
    private final List<BoxBoundary> boxes;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final long totalBlocks;
    private final List<BorderSegment> borderSegments;

    public BlockGridBoundary(List<BoxBoundary> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            this.boxes = List.of(new BoxBoundary(0, 0, 0, 0));
        } else {
            this.boxes = List.copyOf(boxes);
        }

        int curMinX = Integer.MAX_VALUE;
        int curMinZ = Integer.MAX_VALUE;
        int curMaxX = Integer.MIN_VALUE;
        int curMaxZ = Integer.MIN_VALUE;
        long blocks = 0;

        for (BoxBoundary b : this.boxes) {
            curMinX = Math.min(curMinX, b.getMinX());
            curMinZ = Math.min(curMinZ, b.getMinZ());
            curMaxX = Math.max(curMaxX, b.getMaxX());
            curMaxZ = Math.max(curMaxZ, b.getMaxZ());
            blocks += b.getAreaBlocks();
        }

        this.minX = curMinX;
        this.minZ = curMinZ;
        this.maxX = curMaxX;
        this.maxZ = curMaxZ;
        this.totalBlocks = blocks;
        this.borderSegments = computeBorderSegments();
    }

    public List<BoxBoundary> getBoxes() {
        return boxes;
    }

    @Override
    public boolean contains(int x, int z) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return false;
        }
        for (BoxBoundary b : boxes) {
            if (b.contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean contains(Boundary other) {
        if (other == null) return false;
        if (other.getMinX() < this.minX || other.getMaxX() > this.maxX ||
            other.getMinZ() < this.minZ || other.getMaxZ() > this.maxZ) {
            return false;
        }
        if (other instanceof BoxBoundary bb) {
            return containsBox(bb);
        }
        if (other instanceof BlockGridBoundary bgb) {
            for (BoxBoundary b : bgb.getBoxes()) {
                if (!containsBox(b)) return false;
            }
            return true;
        }
        // General fallback: sample bounding box points and center
        return contains(other.getMinX(), other.getMinZ()) &&
               contains(other.getMaxX(), other.getMaxZ()) &&
               contains(other.getMinX(), other.getMaxZ()) &&
               contains(other.getMaxX(), other.getMinZ());
    }

    private boolean containsBox(BoxBoundary bb) {
        // Fast containment if matched exactly by one box
        for (BoxBoundary b : boxes) {
            if (b.contains(bb)) return true;
        }
        // Check corners and perimeter
        for (int x = bb.getMinX(); x <= bb.getMaxX(); x++) {
            if (!contains(x, bb.getMinZ()) || !contains(x, bb.getMaxZ())) return false;
        }
        for (int z = bb.getMinZ(); z <= bb.getMaxZ(); z++) {
            if (!contains(bb.getMinX(), z) || !contains(bb.getMaxX(), z)) return false;
        }
        return true;
    }

    @Override
    public boolean intersects(Boundary other) {
        if (other == null) return false;
        if (other.getMinX() > this.maxX || other.getMaxX() < this.minX ||
            other.getMinZ() > this.maxZ || other.getMaxZ() < this.minZ) {
            return false;
        }
        if (other instanceof BoxBoundary bb) {
            for (BoxBoundary b : boxes) {
                if (b.intersects(bb)) return true;
            }
            return false;
        }
        if (other instanceof BlockGridBoundary bgb) {
            for (BoxBoundary b1 : boxes) {
                for (BoxBoundary b2 : bgb.getBoxes()) {
                    if (b1.intersects(b2)) return true;
                }
            }
            return false;
        }
        return other.intersects(this);
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
    public long getAreaBlocks() {
        return totalBlocks;
    }

    @Override
    public List<BorderSegment> getBorderSegments() {
        return borderSegments;
    }

    private List<BorderSegment> computeBorderSegments() {
        Set<Long> hEdges = new HashSet<>(); // packed (z, x)
        Set<Long> vEdges = new HashSet<>(); // packed (x, z)

        for (BoxBoundary b : boxes) {
            int bMinX = b.getMinX();
            int bMaxX = b.getMaxX();
            int bMinZ = b.getMinZ();
            int bMaxZ = b.getMaxZ();

            // North perimeter (Z = bMinZ)
            int zN = bMinZ;
            for (int x = bMinX; x <= bMaxX; x++) {
                if (!contains(x, zN - 1)) {
                    hEdges.add(packCoord(zN, x));
                }
            }

            // South perimeter (Z = bMaxZ + 1)
            int zS = bMaxZ + 1;
            for (int x = bMinX; x <= bMaxX; x++) {
                if (!contains(x, zS)) {
                    hEdges.add(packCoord(zS, x));
                }
            }

            // West perimeter (X = bMinX)
            int xW = bMinX;
            for (int z = bMinZ; z <= bMaxZ; z++) {
                if (!contains(xW - 1, z)) {
                    vEdges.add(packCoord(xW, z));
                }
            }

            // East perimeter (X = bMaxX + 1)
            int xE = bMaxX + 1;
            for (int z = bMinZ; z <= bMaxZ; z++) {
                if (!contains(xE, z)) {
                    vEdges.add(packCoord(xE, z));
                }
            }
        }

        List<BorderSegment> segments = new ArrayList<>();

        // Merge contiguous horizontal segments
        Map<Integer, List<Integer>> hByZ = new HashMap<>();
        for (long packed : hEdges) {
            int z = unpackFirst(packed);
            int x = unpackSecond(packed);
            hByZ.computeIfAbsent(z, k -> new ArrayList<>()).add(x);
        }

        for (Map.Entry<Integer, List<Integer>> entry : hByZ.entrySet()) {
            int z = entry.getKey();
            List<Integer> xs = entry.getValue();
            Collections.sort(xs);
            int start = xs.get(0);
            int end = start + 1;
            for (int i = 1; i < xs.size(); i++) {
                int x = xs.get(i);
                if (x == end) {
                    end = x + 1;
                } else {
                    segments.add(new BorderSegment(start, z, end, z));
                    start = x;
                    end = x + 1;
                }
            }
            segments.add(new BorderSegment(start, z, end, z));
        }

        // Merge contiguous vertical segments
        Map<Integer, List<Integer>> vByX = new HashMap<>();
        for (long packed : vEdges) {
            int x = unpackFirst(packed);
            int z = unpackSecond(packed);
            vByX.computeIfAbsent(x, k -> new ArrayList<>()).add(z);
        }

        for (Map.Entry<Integer, List<Integer>> entry : vByX.entrySet()) {
            int x = entry.getKey();
            List<Integer> zs = entry.getValue();
            Collections.sort(zs);
            int start = zs.get(0);
            int end = start + 1;
            for (int i = 1; i < zs.size(); i++) {
                int z = zs.get(i);
                if (z == end) {
                    end = z + 1;
                } else {
                    segments.add(new BorderSegment(x, start, x, end));
                    start = z;
                    end = z + 1;
                }
            }
            segments.add(new BorderSegment(x, start, x, end));
        }

        return List.copyOf(segments);
    }

    private static long packCoord(int a, int b) {
        return (((long) a) << 32) | (b & 0xFFFFFFFFL);
    }

    private static int unpackFirst(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackSecond(long packed) {
        return (int) packed;
    }

    public static BlockGridBoundary fromBoxes(List<BoxBoundary> boxes) {
        return new BlockGridBoundary(boxes);
    }

    public static List<BoxBoundary> getBoxesOf(Boundary b) {
        if (b instanceof BlockGridBoundary bgb) {
            return bgb.getBoxes();
        }
        if (b instanceof BoxBoundary bb) {
            return List.of(bb);
        }
        if (b instanceof PolygonBoundary pb) {
            return pb.getGridBoundary().getBoxes();
        }
        return List.of(new BoxBoundary(b.getMinX(), b.getMinZ(), b.getMaxX(), b.getMaxZ()));
    }

    /**
     * Snaps a child boundary to the interior of its parent boundary.
     * Any missteps protruding outside the parent are clipped away.
     * Returns null if the child does not overlap with the parent at all.
     */
    public static Boundary clipToParent(Boundary child, Boundary parent) {
        if (child == null || parent == null) return child;
        if (parent.contains(child)) return child; // Already fully contained

        List<BoxBoundary> childBoxes = getBoxesOf(child);
        List<BoxBoundary> parentBoxes = getBoxesOf(parent);

        List<BoxBoundary> resultBoxes = new ArrayList<>();
        for (BoxBoundary c : childBoxes) {
            for (BoxBoundary p : parentBoxes) {
                int iMinX = Math.max(c.getMinX(), p.getMinX());
                int iMaxX = Math.min(c.getMaxX(), p.getMaxX());
                int iMinZ = Math.max(c.getMinZ(), p.getMinZ());
                int iMaxZ = Math.min(c.getMaxZ(), p.getMaxZ());
                if (iMinX <= iMaxX && iMinZ <= iMaxZ) {
                    resultBoxes.add(new BoxBoundary(iMinX, iMinZ, iMaxX, iMaxZ));
                }
            }
        }

        if (resultBoxes.isEmpty()) {
            return null; // Completely outside parent
        }

        if (resultBoxes.size() == 1) {
            return resultBoxes.get(0);
        }
        return new BlockGridBoundary(resultBoxes);
    }

    public static BlockGridBoundary fromBlocks(Collection<Long> blockCoords) {
        if (blockCoords == null || blockCoords.isEmpty()) {
            return new BlockGridBoundary(List.of(new BoxBoundary(0, 0, 0, 0)));
        }

        // Group by Z
        Map<Integer, List<Integer>> byZ = new TreeMap<>();
        for (long packed : blockCoords) {
            int x = unpackFirst(packed);
            int z = unpackSecond(packed);
            byZ.computeIfAbsent(z, k -> new ArrayList<>()).add(x);
        }

        record Interval(int startX, int endX) {}

        Map<Integer, List<Interval>> intervalsByZ = new TreeMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : byZ.entrySet()) {
            List<Integer> xs = entry.getValue();
            Collections.sort(xs);
            List<Interval> intervals = new ArrayList<>();
            int start = xs.get(0);
            int end = start;
            for (int i = 1; i < xs.size(); i++) {
                int x = xs.get(i);
                if (x == end + 1) {
                    end = x;
                } else {
                    intervals.add(new Interval(start, end));
                    start = x;
                    end = x;
                }
            }
            intervals.add(new Interval(start, end));
            intervalsByZ.put(entry.getKey(), intervals);
        }

        // Merge contiguous intervals across Z into 2D rectangular boxes
        List<BoxBoundary> boxes = new ArrayList<>();
        Map<Interval, int[]> activeBoxes = new HashMap<>(); // interval -> [startZ, lastZ]

        for (Map.Entry<Integer, List<Interval>> entry : intervalsByZ.entrySet()) {
            int z = entry.getKey();
            Set<Interval> curIntervals = new HashSet<>(entry.getValue());

            // Check which active boxes ended
            List<Interval> ended = new ArrayList<>();
            for (Map.Entry<Interval, int[]> active : activeBoxes.entrySet()) {
                Interval interval = active.getKey();
                int[] range = active.getValue();
                if (curIntervals.contains(interval) && range[1] == z - 1) {
                    range[1] = z;
                } else {
                    ended.add(interval);
                }
            }

            for (Interval interval : ended) {
                int[] range = activeBoxes.remove(interval);
                boxes.add(new BoxBoundary(interval.startX(), range[0], interval.endX(), range[1]));
            }

            // Start new intervals
            for (Interval interval : curIntervals) {
                activeBoxes.putIfAbsent(interval, new int[]{z, z});
            }
        }

        for (Map.Entry<Interval, int[]> active : activeBoxes.entrySet()) {
            Interval interval = active.getKey();
            int[] range = active.getValue();
            boxes.add(new BoxBoundary(interval.startX(), range[0], interval.endX(), range[1]));
        }

        return new BlockGridBoundary(boxes);
    }

    public static BlockGridBoundary fromPolygon(List<PolygonBoundary.Point2D> vertices) {
        if (vertices == null || vertices.size() < 3) {
            return new BlockGridBoundary(List.of(new BoxBoundary(0, 0, 0, 0)));
        }

        int polyMinX = Integer.MAX_VALUE;
        int polyMinZ = Integer.MAX_VALUE;
        int polyMaxX = Integer.MIN_VALUE;
        int polyMaxZ = Integer.MIN_VALUE;

        for (PolygonBoundary.Point2D p : vertices) {
            polyMinX = Math.min(polyMinX, p.x());
            polyMinZ = Math.min(polyMinZ, p.z());
            polyMaxX = Math.max(polyMaxX, p.x());
            polyMaxZ = Math.max(polyMaxZ, p.z());
        }

        Set<Long> blocks = new HashSet<>();
        for (int x = polyMinX; x <= polyMaxX; x++) {
            for (int z = polyMinZ; z <= polyMaxZ; z++) {
                if (PolygonBoundary.isPointInPolygon(x + 0.5, z + 0.5, vertices)) {
                    blocks.add(packCoord(x, z));
                }
            }
        }

        if (blocks.isEmpty()) {
            return new BlockGridBoundary(List.of(new BoxBoundary(polyMinX, polyMinZ, polyMaxX, polyMaxZ)));
        }

        return fromBlocks(blocks);
    }
}
