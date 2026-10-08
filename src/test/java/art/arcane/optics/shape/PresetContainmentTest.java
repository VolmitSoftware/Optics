package art.arcane.optics.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

final class PresetContainmentTest {
    @Test
    void centersAreInsideAndFarPointsOutside() {
        for (Map.Entry<String, Shape> entry : ShapeFixtures.presets().entrySet()) {
            Shape shape = entry.getValue();
            if (!(shape instanceof Ring) && !(shape instanceof Polygon)) {
                assertTrue(shape.contains(0.0D, 0.0D), entry.getKey());
            }
            assertFalse(shape.contains(3.0D, 3.0D), entry.getKey());
            assertFalse(shape.contains(-2.5D, 0.1D), entry.getKey());
        }
        assertFalse(new Ring(1.0D, 0.6D).contains(0.0D, 0.0D));
        assertTrue(new Ring(1.0D, 0.6D).contains(0.8D, 0.0D));
    }

    @Test
    void mirrorSymmetricPresetsAgreeAcrossTheVerticalAxis() {
        for (Map.Entry<String, Shape> entry : ShapeFixtures.mirrorSymmetric().entrySet()) {
            Shape shape = entry.getValue();
            for (int i = 0; i < 64; i++) {
                for (int j = 0; j < 64; j++) {
                    double u = -1.2D + 2.4D * (i + 0.37D) / 64.0D;
                    double v = -1.2D + 2.4D * (j + 0.21D) / 64.0D;
                    if (Math.abs(shape.signedDistance(u, v)) < 1.0E-6D) {
                        continue;
                    }
                    assertEquals(shape.contains(u, v), shape.contains(-u, v), entry.getKey() + " at " + u + "," + v);
                }
            }
        }
    }

    @Test
    void boundsEncloseOutlinesAndInsideSamples() {
        for (Map.Entry<String, Shape> entry : ShapeFixtures.presets().entrySet()) {
            Shape shape = entry.getValue();
            Bounds2 bounds = shape.bounds().grown(1.0E-9D);
            for (Outline outline : shape.outlines(0.01D)) {
                for (int index = 0; index < outline.size(); index++) {
                    assertTrue(bounds.contains(outline.u(index), outline.v(index)), entry.getKey() + " outline point " + index);
                }
            }
            for (int i = 0; i < 96; i++) {
                for (int j = 0; j < 96; j++) {
                    double u = -2.0D + 4.0D * (i + 0.5D) / 96.0D;
                    double v = -2.0D + 4.0D * (j + 0.5D) / 96.0D;
                    if (shape.contains(u, v)) {
                        assertTrue(bounds.contains(u, v), entry.getKey() + " inside sample " + u + "," + v);
                    }
                }
            }
        }
    }

    @Test
    void outlinesTraceTheBoundaryCounterClockwise() {
        for (Map.Entry<String, Shape> entry : ShapeFixtures.presets().entrySet()) {
            Shape shape = entry.getValue();
            Outline outer = shape.outlines(0.005D).getFirst();
            assertFalse(outer.clockwise(), entry.getKey());
            for (int index = 0; index < outer.size(); index++) {
                assertEquals(0.0D, shape.signedDistance(outer.u(index), outer.v(index)), 0.02D, entry.getKey() + " vertex " + index);
            }
        }
        assertEquals(2, new Ring(1.0D, 0.6D).outlines(0.01D).size());
        assertTrue(new Ring(1.0D, 0.6D).outlines(0.01D).get(1).clockwise());
    }

    @Test
    void rotationTurnsPresetsCounterClockwise() {
        Shape upright = new RegularPolygon(3, 1.0D, 0.0D);
        assertTrue(upright.contains(0.0D, 0.95D));
        Shape turned = new RegularPolygon(3, 1.0D, 90.0D);
        assertTrue(turned.contains(-0.95D, 0.0D));
        assertFalse(turned.contains(0.0D, 0.95D));
        assertTrue(new Star(5, 1.0D, 0.4D, 0.0D).contains(0.0D, 0.97D));
        assertTrue(new Flower(4, 1.0D, 0.9D, 0.0D).contains(0.0D, 0.97D));
        assertFalse(new Flower(4, 1.0D, 0.9D, 0.0D).contains(0.5D, 0.5D));
        assertTrue(new Flower(4, 1.0D, 0.9D, 45.0D).contains(0.5D, 0.5D));
        assertTrue(new Feather(2.0D, 1.0D, 0.0D, 0.0D).contains(0.0D, 0.95D));
        assertTrue(new Feather(2.0D, 1.0D, 0.0D, -90.0D).contains(0.95D, 0.0D));
        assertTrue(new Heart(1.0D, 0.0D).contains(0.0D, -0.85D));
        assertFalse(new Heart(1.0D, 0.0D).contains(0.0D, 0.85D));
    }

    @Test
    void invalidParametersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Rectangle(0.0D, 1.0D));
        assertThrows(IllegalArgumentException.class, () -> new Rectangle(4.5D, 1.0D));
        assertThrows(IllegalArgumentException.class, () -> new RoundedRectangle(1.0D, 1.0D, 0.6D));
        assertThrows(IllegalArgumentException.class, () -> new Ellipse(-1.0D, 1.0D));
        assertThrows(IllegalArgumentException.class, () -> new RegularPolygon(2, 1.0D, 0.0D));
        assertThrows(IllegalArgumentException.class, () -> new RegularPolygon(65, 1.0D, 0.0D));
        assertThrows(IllegalArgumentException.class, () -> new Star(5, 0.5D, 0.5D, 0.0D));
        assertThrows(IllegalArgumentException.class, () -> new Star(33, 1.0D, 0.5D, 0.0D));
        assertThrows(IllegalArgumentException.class, () -> new Flower(1, 1.0D, 0.5D, 0.0D));
        assertThrows(IllegalArgumentException.class, () -> new Flower(5, 1.0D, 1.5D, 0.0D));
        assertThrows(IllegalArgumentException.class, () -> new Heart(0.0D, 0.0D));
        assertThrows(IllegalArgumentException.class, () -> new Feather(2.0D, 1.0D, 1.5D, 0.0D));
        assertThrows(IllegalArgumentException.class, () -> new Ring(0.5D, 0.5D));
        assertThrows(IllegalArgumentException.class, () -> new Polygon(new double[] {0.0D, 0.0D, 1.0D, 1.0D}));
        assertThrows(IllegalArgumentException.class, () -> new Polygon(new double[1026]));
        assertThrows(IllegalArgumentException.class, () -> new Spline(new double[] {0.0D, 0.0D, 1.0D, 1.0D, 1.0D, 0.0D}, 1));
        assertThrows(IllegalArgumentException.class, () -> new RegularPolygon(6, 1.0D, Double.NaN));
    }
}
