package art.arcane.optics.shape;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;
import org.junit.jupiter.api.Test;

final class ShapeRasterTest {
    @Test
    void circleCoverageMatchesIndependentSampling() {
        PlaneShape plane = PlaneShape.fit(Ellipse.circle(1.0D), FitMode.CONTAIN, 9, 9);
        ShapeRaster raster = ShapeRaster.of(plane, 4, 0.5D);
        int expected = 0;
        for (int row = 0; row < 9; row++) {
            for (int column = 0; column < 9; column++) {
                int hits = circleHits(column, row, 4.5D, 4.5D, 4.5D, 4);
                assertEquals(hits / 16.0D, raster.coverageFraction(column, row), "cell " + column + "," + row);
                boolean inside = hits >= 8;
                assertEquals(inside, raster.inside(column, row), "cell " + column + "," + row);
                expected += inside ? 1 : 0;
                if (hits > 0 && hits < 16) {
                    assertSame(ShapeRaster.Coverage.PARTIAL, raster.coverage(column, row));
                }
            }
        }
        assertEquals(expected, raster.insideCount());
        assertSame(ShapeRaster.Coverage.FULL, raster.coverage(4, 4));
        assertSame(ShapeRaster.Coverage.EMPTY, raster.coverage(0, 0));
        assertSame(ShapeRaster.Coverage.PARTIAL, raster.coverage(0, 4));
        assertSame(ShapeRaster.Coverage.EMPTY, raster.coverage(-1, 4));
        assertEquals(9, raster.columns());
        assertEquals(9, raster.rows());
        assertEquals(4, raster.subsamples());
        assertEquals(0.5D, raster.threshold());
    }

    @Test
    void thresholdEdgesAreInclusive() {
        PlaneShape half = PlaneShape.fit(new Rectangle(2.0D, 1.0D).translated(0.0D, -0.5D), FitMode.STRETCH, 2, 2);
        ShapeRaster raster = ShapeRaster.of(half, 4, 0.5D);
        assertEquals(1.0D, raster.coverageFraction(0, 0));
        assertEquals(0.0D, raster.coverageFraction(0, 1));
        PlaneShape middle = PlaneShape.fit(new Rectangle(2.0D, 1.0D), FitMode.STRETCH, 1, 1);
        assertEquals(0.5D, ShapeRaster.of(middle, 4, 0.5D).coverageFraction(0, 0));
        assertTrue(ShapeRaster.of(middle, 4, 0.5D).inside(0, 0));
        assertFalse(ShapeRaster.of(middle, 4, 0.5625D).inside(0, 0));
        assertTrue(ShapeRaster.of(middle, 4, 0.0625D).inside(0, 0));
        assertFalse(ShapeRaster.of(middle, 4, 1.0D).inside(0, 0));
        assertThrows(IllegalArgumentException.class, () -> ShapeRaster.of(middle, 4, 0.0D));
        assertThrows(IllegalArgumentException.class, () -> ShapeRaster.of(middle, 0, 0.5D));
        assertThrows(IllegalArgumentException.class, () -> ShapeRaster.of(middle, 9, 0.5D));
    }

    @Test
    void cellsOutsideTheBoundsAreEmptyWithoutSampling() {
        PlaneShape plane = PlaneShape.fit(Ellipse.circle(0.2D), FitMode.CONTAIN, 9, 9);
        ShapeRaster raster = plane.raster(4);
        assertSame(raster, plane.raster(4));
        for (int row = 0; row < 9; row++) {
            for (int column = 0; column < 9; column++) {
                boolean near = Math.abs(column - 4) <= 1 && Math.abs(row - 4) <= 1;
                if (!near) {
                    assertSame(ShapeRaster.Coverage.EMPTY, raster.coverage(column, row));
                    assertEquals(0.0D, raster.coverageFraction(column, row));
                }
            }
        }
        assertTrue(raster.inside(4, 4));
    }

    @Test
    void cellsAreWorldMinCornersIntersectedWithTheMask() {
        PlaneShape plane = PlaneShape.fit(Ellipse.circle(1.0D), FitMode.CONTAIN, 3, 3);
        ShapeRaster raster = plane.raster(4);
        assertEquals(9, raster.insideCount());
        long[] mask = {0b111101111L};
        List<Vec3d> cells = new ArrayList<Vec3d>();
        assertEquals(8, raster.cells(10, 64, -5, Face.S, mask, cells));
        assertTrue(cells.contains(new Vec3d(10, 64, -5)));
        assertTrue(cells.contains(new Vec3d(12, 66, -5)));
        assertFalse(cells.contains(new Vec3d(11, 65, -5)));
        cells.clear();
        assertEquals(9, raster.cells(10, 64, -5, Face.E, null, cells));
        assertTrue(cells.contains(new Vec3d(10, 66, -3)));
        cells.clear();
        assertEquals(9, raster.cells(10, 64, -5, Face.U, null, cells));
        assertTrue(cells.contains(new Vec3d(12, 64, -4)));
        assertArrayEquals(new long[] {0b111101111L}, raster.intersect(mask));
        assertArrayEquals(new long[] {0b111111111L}, raster.insideMask());
    }

    @Test
    void fullPlanesRasterEveryCell() {
        ShapeRaster raster = PlaneShape.full(5, 3).raster(2);
        assertEquals(15, raster.insideCount());
        assertSame(ShapeRaster.Coverage.FULL, raster.coverage(4, 2));
    }

    private static int circleHits(int column, int row, double centerU, double centerV, double radius, int subsamples) {
        int hits = 0;
        for (int j = 0; j < subsamples; j++) {
            for (int i = 0; i < subsamples; i++) {
                double u = column + (i + 0.5D) / subsamples - centerU;
                double v = row + (j + 0.5D) / subsamples - centerV;
                if (u * u + v * v <= radius * radius) {
                    hits++;
                }
            }
        }
        return hits;
    }
}
