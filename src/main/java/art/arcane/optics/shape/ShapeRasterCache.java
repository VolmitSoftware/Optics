package art.arcane.optics.shape;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ShapeRasterCache {
    private static final int PLANE = 0;
    private static final int RASTER = 1;
    private static final int MESH = 2;

    private final int capacity;
    private final LinkedHashMap<Key, Object> entries;
    private long hits;
    private long misses;

    public ShapeRasterCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Cache capacity must be positive, got " + capacity);
        }
        this.capacity = capacity;
        entries = new LinkedHashMap<Key, Object>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, Object> eldest) {
                return size() > ShapeRasterCache.this.capacity;
            }
        };
    }

    public PlaneShape plane(ShapeDescriptor descriptor, int columns, int rows) {
        return plane(descriptor, columns, rows, PlaneTransform.IDENTITY);
    }

    public synchronized PlaneShape plane(ShapeDescriptor descriptor, int columns, int rows, PlaneTransform frameOrientation) {
        Key key = new Key(PLANE, descriptor, columns, rows, frameOrientation, 0, 0L, null);
        Object cached = lookup(key);
        if (cached != null) {
            return (PlaneShape) cached;
        }
        PlaneShape plane = PlaneShape.fit(descriptor.shape(), descriptor.fit(), columns, rows, frameOrientation);
        entries.put(key, plane);
        return plane;
    }

    public synchronized ShapeRaster raster(ShapeDescriptor descriptor, int columns, int rows, int subsamples, double threshold) {
        Key key = new Key(RASTER, descriptor, columns, rows, PlaneTransform.IDENTITY, subsamples, Double.doubleToLongBits(threshold), null);
        Object cached = lookup(key);
        if (cached != null) {
            return (ShapeRaster) cached;
        }
        ShapeRaster raster = ShapeRaster.of(PlaneShape.fit(descriptor.shape(), descriptor.fit(), columns, rows), subsamples, threshold);
        entries.put(key, raster);
        return raster;
    }

    public synchronized ShapeMesh mesh(ShapeDescriptor descriptor, int columns, int rows, PlaneTransform frameOrientation, int subdivisions,
                                       long[] cellMask) {
        Key key = new Key(MESH, descriptor, columns, rows, frameOrientation, subdivisions, 0L, cellMask == null ? null : cellMask.clone());
        Object cached = lookup(key);
        if (cached != null) {
            return (ShapeMesh) cached;
        }
        ShapeMesh mesh = ShapeMesh.of(PlaneShape.fit(descriptor.shape(), descriptor.fit(), columns, rows, frameOrientation), subdivisions,
            cellMask);
        entries.put(key, mesh);
        return mesh;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized long hits() {
        return hits;
    }

    public synchronized long misses() {
        return misses;
    }

    public synchronized void clear() {
        entries.clear();
    }

    private Object lookup(Key key) {
        Object cached = entries.get(key);
        if (cached != null) {
            hits++;
        } else {
            misses++;
        }
        return cached;
    }

    private static final class Key {
        private final int kind;
        private final ShapeDescriptor descriptor;
        private final int columns;
        private final int rows;
        private final PlaneTransform orientation;
        private final int detail;
        private final long threshold;
        private final long[] cellMask;
        private final int hash;

        private Key(int kind, ShapeDescriptor descriptor, int columns, int rows, PlaneTransform orientation, int detail, long threshold,
                    long[] cellMask) {
            this.kind = kind;
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
            this.columns = columns;
            this.rows = rows;
            this.orientation = Objects.requireNonNull(orientation, "orientation");
            this.detail = detail;
            this.threshold = threshold;
            this.cellMask = cellMask;
            hash = ((((Objects.hash(kind, descriptor, columns, rows, orientation, detail) * 31) + Long.hashCode(threshold)) * 31)
                + Arrays.hashCode(cellMask));
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && kind == key.kind && columns == key.columns && rows == key.rows && detail == key.detail
                && threshold == key.threshold && descriptor.equals(key.descriptor) && orientation.equals(key.orientation)
                && Arrays.equals(cellMask, key.cellMask);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
