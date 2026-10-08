package art.arcane.optics.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class ShapeRasterCacheTest {
    private static final ShapeDescriptor CIRCLE = ShapeDescriptor.of(Ellipse.circle(1.0D), FitMode.CONTAIN);
    private static final ShapeDescriptor STAR = ShapeDescriptor.of(new Star(5, 1.0D, 0.45D, 0.0D), FitMode.CONTAIN);

    @Test
    void repeatedRequestsHit() {
        ShapeRasterCache cache = new ShapeRasterCache(8);
        PlaneShape plane = cache.plane(CIRCLE, 7, 5);
        assertSame(plane, cache.plane(ShapeDescriptor.decode(CIRCLE.encode()), 7, 5));
        assertNotSame(plane, cache.plane(CIRCLE, 7, 5, PlaneTransform.rotation(90.0D)));
        ShapeRaster raster = cache.raster(CIRCLE, 7, 5, 4, 0.5D);
        assertSame(raster, cache.raster(CIRCLE, 7, 5, 4, 0.5D));
        assertNotSame(raster, cache.raster(CIRCLE, 7, 5, 4, 0.75D));
        assertEquals(2L, cache.hits());
        assertEquals(4L, cache.misses());
        assertEquals(4, cache.size());
    }

    @Test
    void leastRecentlyUsedEntriesAreEvicted() {
        ShapeRasterCache cache = new ShapeRasterCache(2);
        PlaneShape circle = cache.plane(CIRCLE, 5, 5);
        PlaneShape star = cache.plane(STAR, 5, 5);
        assertSame(circle, cache.plane(CIRCLE, 5, 5));
        cache.plane(CIRCLE, 6, 6);
        assertEquals(2, cache.size());
        assertSame(circle, cache.plane(CIRCLE, 5, 5));
        assertNotSame(star, cache.plane(STAR, 5, 5));
        cache.clear();
        assertEquals(0, cache.size());
        assertThrows(IllegalArgumentException.class, () -> new ShapeRasterCache(0));
    }

    @Test
    void meshesAreKeyedByTheCellMask() {
        ShapeRasterCache cache = new ShapeRasterCache(8);
        long[] all = {(1L << 25) - 1L};
        long[] holed = {((1L << 25) - 1L) & ~(1L << 12)};
        ShapeMesh open = cache.mesh(CIRCLE, 5, 5, PlaneTransform.IDENTITY, 4, all);
        assertSame(open, cache.mesh(CIRCLE, 5, 5, PlaneTransform.IDENTITY, 4, all.clone()));
        ShapeMesh withHole = cache.mesh(CIRCLE, 5, 5, PlaneTransform.IDENTITY, 4, holed);
        assertNotSame(open, withHole);
        all[0] = 0L;
        assertSame(open, cache.mesh(CIRCLE, 5, 5, PlaneTransform.IDENTITY, 4, new long[] {(1L << 25) - 1L}));
        assertNotSame(open, cache.mesh(CIRCLE, 5, 5, PlaneTransform.IDENTITY, 8, new long[] {(1L << 25) - 1L}));
    }
}
