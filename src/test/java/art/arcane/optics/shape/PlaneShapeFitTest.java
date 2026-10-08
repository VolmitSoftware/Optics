package art.arcane.optics.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

final class PlaneShapeFitTest {
    private static final double EPSILON = 1.0E-12D;

    @Test
    void containUsesTheShorterEdgeCentered() {
        assertMaps(PlaneShape.fit(Ellipse.circle(1.0D), FitMode.CONTAIN, 7, 5), 1.0D, 0.0D, 6.0D, 2.5D);
        assertMaps(PlaneShape.fit(Ellipse.circle(1.0D), FitMode.CONTAIN, 7, 5), 0.0D, 1.0D, 3.5D, 5.0D);
        assertMaps(PlaneShape.fit(Ellipse.circle(1.0D), FitMode.CONTAIN, 5, 7), 1.0D, 0.0D, 5.0D, 3.5D);
        assertMaps(PlaneShape.fit(Ellipse.circle(1.0D), FitMode.CONTAIN, 1, 1), 1.0D, 1.0D, 1.0D, 1.0D);
    }

    @Test
    void coverUsesTheLongerEdgeCentered() {
        assertMaps(PlaneShape.fit(Ellipse.circle(1.0D), FitMode.COVER, 7, 5), 0.0D, 1.0D, 3.5D, 6.0D);
        assertMaps(PlaneShape.fit(Ellipse.circle(1.0D), FitMode.COVER, 5, 7), -1.0D, 0.0D, -1.0D, 3.5D);
    }

    @Test
    void stretchFillsBothEdges() {
        assertMaps(PlaneShape.fit(Ellipse.circle(1.0D), FitMode.STRETCH, 7, 5), 1.0D, 1.0D, 7.0D, 5.0D);
        assertMaps(PlaneShape.fit(Ellipse.circle(1.0D), FitMode.STRETCH, 5, 7), -1.0D, -1.0D, 0.0D, 0.0D);
    }

    @Test
    void unitCoordinatesInvertThePlacement() {
        Random random = new Random(3L);
        PlaneTransform orientation = new PlaneTransform(0.0D, -1.0D, -1.0D, 0.0D, 0.0D, 0.0D);
        for (FitMode fit : FitMode.values()) {
            PlaneShape plane = PlaneShape.fit(new Heart(1.0D, 0.0D), fit, 7, 5, orientation);
            double[] cell = new double[2];
            double[] unit = new double[2];
            for (int trial = 0; trial < 200; trial++) {
                double u = random.nextDouble() * 2.0D - 1.0D;
                double v = random.nextDouble() * 2.0D - 1.0D;
                plane.placement().pointInto(u, v, cell);
                plane.unitCoordinates(cell[0], cell[1], unit);
                assertEquals(u, unit[0], 1.0E-12D);
                assertEquals(v, unit[1], 1.0E-12D);
            }
        }
    }

    @Test
    void fullPlanesAnswerInsideTheRectangle() {
        PlaneShape full = PlaneShape.full(7, 5);
        assertTrue(full.isFull());
        assertTrue(full.contains(6.9D, 4.9D));
        assertTrue(full.contains(0.0D, 0.0D));
        assertFalse(full.contains(7.0D, 0.0D));
        assertFalse(full.contains(-0.1D, 2.0D));
        assertEquals(new Bounds2(0.0D, 0.0D, 7.0D, 5.0D), full.bounds());
        assertEquals(-2.5D, full.signedDistance(3.5D, 2.5D), EPSILON);
        assertTrue(PlaneShape.fit(Shapes.FULL, FitMode.STRETCH, 3, 9).isFull());
        assertTrue(PlaneShape.fit(Shapes.FULL, FitMode.STRETCH, 3, 9, PlaneTransform.rotation(90.0D)).isFull());
        assertFalse(PlaneShape.fit(Shapes.FULL, FitMode.CONTAIN, 3, 9).isFull());
        assertFalse(PlaneShape.fit(Ellipse.circle(1.0D), FitMode.STRETCH, 3, 9).isFull());
    }

    @Test
    void containmentStaysInsideTheRectangleAndBoundsAreClipped() {
        PlaneShape cover = PlaneShape.fit(Ellipse.circle(1.0D), FitMode.COVER, 7, 3);
        assertEquals(new Bounds2(0.0D, 0.0D, 7.0D, 3.0D), cover.bounds());
        assertTrue(cover.contains(3.5D, 0.1D));
        assertFalse(cover.contains(3.5D, 3.2D));
        PlaneShape small = PlaneShape.fit(Ellipse.circle(0.5D), FitMode.CONTAIN, 8, 8);
        assertEquals(new Bounds2(2.0D, 2.0D, 6.0D, 6.0D), small.bounds());
        assertEquals(-2.0D, small.signedDistance(4.0D, 4.0D), EPSILON);
        assertEquals(1.0D, small.signedDistance(7.0D, 4.0D), EPSILON);
    }

    @Test
    void outlinesMapIntoCellSpace() {
        PlaneShape plane = PlaneShape.fit(new Rectangle(1.0D, 1.0D), FitMode.CONTAIN, 8, 4);
        Outline outline = plane.outlines(0.05D).getFirst();
        assertEquals(new Bounds2(3.0D, 1.0D, 5.0D, 3.0D), outline.bounds());
        assertFalse(outline.clockwise());
        PlaneShape flipped = PlaneShape.fit(new Heart(1.0D, 0.0D), FitMode.CONTAIN, 8, 4, PlaneTransform.flipV());
        assertFalse(flipped.outlines(0.05D).getFirst().clockwise());
    }

    @Test
    void invalidSizesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PlaneShape.fit(Ellipse.circle(1.0D), FitMode.CONTAIN, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> PlaneShape.full(5, 70000));
        assertThrows(IllegalArgumentException.class, () -> PlaneShape.full(5, 5).raster(9));
    }

    private static void assertMaps(PlaneShape plane, double u, double v, double column, double row) {
        double[] out = new double[2];
        plane.placement().pointInto(u, v, out);
        assertEquals(column, out[0], EPSILON);
        assertEquals(row, out[1], EPSILON);
    }
}
