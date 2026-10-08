package art.arcane.optics.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

final class BooleanShapeTest {
    private static final Shape LEFT = Ellipse.circle(0.7D).translated(-0.3D, 0.0D);
    private static final Shape RIGHT = new Rectangle(1.0D, 0.8D).translated(0.4D, 0.1D);

    @Test
    void containmentFollowsTheTruthTables() {
        Shape union = LEFT.union(RIGHT);
        Shape intersection = LEFT.intersect(RIGHT);
        Shape difference = LEFT.subtract(RIGHT);
        Random random = new Random(7L);
        for (int sample = 0; sample < 5000; sample++) {
            double u = random.nextDouble() * 3.0D - 1.5D;
            double v = random.nextDouble() * 3.0D - 1.5D;
            boolean a = LEFT.contains(u, v);
            boolean b = RIGHT.contains(u, v);
            assertEquals(a || b, union.contains(u, v));
            assertEquals(a && b, intersection.contains(u, v));
            assertEquals(a && !b, difference.contains(u, v));
            assertEquals(Math.min(LEFT.signedDistance(u, v), RIGHT.signedDistance(u, v)), union.signedDistance(u, v), 0.0D);
            assertEquals(Math.max(LEFT.signedDistance(u, v), RIGHT.signedDistance(u, v)), intersection.signedDistance(u, v), 0.0D);
            assertEquals(Math.max(LEFT.signedDistance(u, v), -RIGHT.signedDistance(u, v)), difference.signedDistance(u, v), 0.0D);
        }
    }

    @Test
    void boundsCombineTheChildren() {
        assertEquals(LEFT.bounds().union(RIGHT.bounds()), LEFT.union(RIGHT).bounds());
        assertEquals(LEFT.bounds().intersect(RIGHT.bounds()), LEFT.intersect(RIGHT).bounds());
        assertEquals(LEFT.bounds(), LEFT.subtract(RIGHT).bounds());
    }

    @Test
    void tracedOutlinesEncloseTheInside() {
        for (Shape shape : List.of(LEFT.union(RIGHT), LEFT.intersect(RIGHT), LEFT.subtract(RIGHT),
            Ellipse.circle(1.0D).subtract(Ellipse.circle(0.4D)))) {
            List<Outline> outlines = shape.outlines(0.01D);
            assertFalse(outlines.isEmpty());
            for (int i = 0; i < 80; i++) {
                for (int j = 0; j < 80; j++) {
                    double u = -1.2D + 2.4D * (i + 0.5D) / 80.0D;
                    double v = -1.2D + 2.4D * (j + 0.5D) / 80.0D;
                    if (Math.abs(shape.signedDistance(u, v)) < 0.03D) {
                        continue;
                    }
                    assertEquals(shape.contains(u, v), insideAny(outlines, u, v), "at " + u + "," + v);
                }
            }
        }
        List<Outline> donut = Ellipse.circle(1.0D).subtract(Ellipse.circle(0.4D)).outlines(0.01D);
        assertEquals(2, donut.size());
        assertTrue(donut.stream().anyMatch(Outline::clockwise));
        assertTrue(donut.stream().anyMatch(outline -> !outline.clockwise()));
    }

    private static boolean insideAny(List<Outline> outlines, double u, double v) {
        boolean inside = false;
        for (Outline outline : outlines) {
            if (outline.contains(u, v)) {
                inside = !inside;
            }
        }
        return inside;
    }
}
