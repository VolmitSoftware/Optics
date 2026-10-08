package art.arcane.optics.shape;

import java.util.List;
import java.util.Objects;

import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

public final class ShapeRaster {
    public static final double DEFAULT_THRESHOLD = 0.5D;
    public static final int DEFAULT_SUBSAMPLES = 4;
    private static final int MAX_SUBSAMPLES = 8;

    private final int columns;
    private final int rows;
    private final int subsamples;
    private final double threshold;
    private final byte[] counts;
    private final long[] inside;
    private final long[] partial;
    private final int insideCount;

    private ShapeRaster(PlaneShape shape, int subsamples, double threshold) {
        columns = shape.columns();
        rows = shape.rows();
        this.subsamples = subsamples;
        this.threshold = threshold;
        int cells = columns * rows;
        counts = new byte[cells];
        inside = new long[(cells + 63) >>> 6];
        partial = new long[inside.length];
        int samples = subsamples * subsamples;
        Bounds2 bounds = shape.bounds();
        int insideCells = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int bit = row * columns + column;
                int hits = shape.isFull() ? samples : hits(shape, bounds, column, row);
                counts[bit] = (byte) hits;
                boolean certain = shape.isFull() || certain(shape, bounds, column, row, hits, samples);
                if (!certain) {
                    partial[bit >>> 6] |= 1L << (bit & 63);
                }
                if (hits > 0 && hits / (double) samples >= threshold) {
                    inside[bit >>> 6] |= 1L << (bit & 63);
                    insideCells++;
                }
            }
        }
        insideCount = insideCells;
    }

    public static ShapeRaster of(PlaneShape shape, int subsamples, double threshold) {
        Objects.requireNonNull(shape, "shape");
        if (subsamples < 1 || subsamples > MAX_SUBSAMPLES) {
            throw new IllegalArgumentException("Raster subsamples must be in 1.." + MAX_SUBSAMPLES + ", got " + subsamples);
        }
        if (!Double.isFinite(threshold) || !(threshold > 0.0D) || threshold > 1.0D) {
            throw new IllegalArgumentException("Raster threshold must be in (0, 1], got " + threshold);
        }
        return new ShapeRaster(shape, subsamples, threshold);
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public int subsamples() {
        return subsamples;
    }

    public double threshold() {
        return threshold;
    }

    public Coverage coverage(int column, int row) {
        if (column < 0 || row < 0 || column >= columns || row >= rows) {
            return Coverage.EMPTY;
        }
        int bit = row * columns + column;
        if ((partial[bit >>> 6] & (1L << (bit & 63))) != 0L) {
            return Coverage.PARTIAL;
        }
        return counts[bit] == 0 ? Coverage.EMPTY : Coverage.FULL;
    }

    public double coverageFraction(int column, int row) {
        if (column < 0 || row < 0 || column >= columns || row >= rows) {
            return 0.0D;
        }
        return counts[row * columns + column] / (double) (subsamples * subsamples);
    }

    public boolean inside(int column, int row) {
        if (column < 0 || row < 0 || column >= columns || row >= rows) {
            return false;
        }
        int bit = row * columns + column;
        return (inside[bit >>> 6] & (1L << (bit & 63))) != 0L;
    }

    public int insideCount() {
        return insideCount;
    }

    public long[] insideMask() {
        return inside.clone();
    }

    public long[] intersect(long[] cellMask) {
        Objects.requireNonNull(cellMask, "cellMask");
        long[] out = new long[inside.length];
        for (int word = 0; word < out.length; word++) {
            out[word] = inside[word] & (word < cellMask.length ? cellMask[word] : 0L);
        }
        return out;
    }

    public int cells(int originX, int originY, int originZ, Face normal, long[] cellMask, List<Vec3d> out) {
        Objects.requireNonNull(normal, "normal");
        Objects.requireNonNull(out, "out");
        int normalAxis = normal.axisIndex();
        int added = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int bit = row * columns + column;
                int word = bit >>> 6;
                long mask = 1L << (bit & 63);
                if ((inside[word] & mask) == 0L || (cellMask != null && (word >= cellMask.length || (cellMask[word] & mask) == 0L))) {
                    continue;
                }
                out.add(switch (normalAxis) {
                    case 0 -> new Vec3d(originX, originY + row, originZ + column);
                    case 1 -> new Vec3d(originX + column, originY, originZ + row);
                    default -> new Vec3d(originX + column, originY + row, originZ);
                });
                added++;
            }
        }
        return added;
    }

    private int hits(PlaneShape shape, Bounds2 bounds, int column, int row) {
        if (outside(bounds, column, row)) {
            return 0;
        }
        int hits = 0;
        for (int j = 0; j < subsamples; j++) {
            double sampleRow = row + (j + 0.5D) / subsamples;
            for (int i = 0; i < subsamples; i++) {
                if (shape.containsAnalytic(column + (i + 0.5D) / subsamples, sampleRow)) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private static boolean certain(PlaneShape shape, Bounds2 bounds, int column, int row, int hits, int samples) {
        if (hits == 0 && outside(bounds, column, row)) {
            return true;
        }
        if (hits != 0 && hits != samples) {
            return false;
        }
        int certified = shape.certify(column, row);
        return hits == 0 ? certified > 0 : certified < 0;
    }

    private static boolean outside(Bounds2 bounds, int column, int row) {
        return bounds.isEmpty() || column + 1.0D <= bounds.minU() || column > bounds.maxU()
            || row + 1.0D <= bounds.minV() || row > bounds.maxV();
    }

    public enum Coverage {
        EMPTY,
        PARTIAL,
        FULL
    }
}
