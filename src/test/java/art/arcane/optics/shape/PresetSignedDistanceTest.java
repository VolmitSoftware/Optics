package art.arcane.optics.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

final class PresetSignedDistanceTest {
    private static final int GRID = 128;
    private static final double EXACT = 1.0E-9D;

    @Test
    void signAgreesWithContainmentEverywhere() {
        for (Map.Entry<String, Shape> entry : ShapeFixtures.presets().entrySet()) {
            Shape shape = entry.getValue();
            for (int i = 0; i < GRID; i++) {
                for (int j = 0; j < GRID; j++) {
                    double u = -1.5D + 3.0D * (i + 0.5D) / GRID;
                    double v = -1.5D + 3.0D * (j + 0.5D) / GRID;
                    double distance = shape.signedDistance(u, v);
                    if (distance < 0.0D) {
                        assertTrue(shape.contains(u, v), entry.getKey() + " at " + u + "," + v);
                    } else if (distance > 0.0D) {
                        assertFalse(shape.contains(u, v), entry.getKey() + " at " + u + "," + v);
                    }
                }
            }
        }
    }

    @Test
    void exactPresetsMatchTheirClosedForms() {
        Rectangle rectangle = new Rectangle(1.6D, 1.1D);
        RoundedRectangle rounded = new RoundedRectangle(1.8D, 1.4D, 0.35D);
        Ellipse circle = Ellipse.circle(0.9D);
        Ring ring = new Ring(1.0D, 0.6D);
        RegularPolygon hexagon = new RegularPolygon(6, 0.95D, 17.0D);
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                double u = -1.5D + 3.0D * (i + 0.5D) / GRID;
                double v = -1.5D + 3.0D * (j + 0.5D) / GRID;
                assertEquals(box(u, v, 0.8D, 0.55D, 0.0D), rectangle.signedDistance(u, v), EXACT);
                assertEquals(box(u, v, 0.9D, 0.7D, 0.35D), rounded.signedDistance(u, v), EXACT);
                assertEquals(Math.hypot(u, v) - 0.9D, circle.signedDistance(u, v), EXACT);
                double rho = Math.hypot(u, v);
                assertEquals(Math.max(rho - 1.0D, 0.6D - rho), ring.signedDistance(u, v), EXACT);
                assertEquals(polygon(u, v, 6, 0.95D, 17.0D), hexagon.signedDistance(u, v), EXACT);
            }
        }
    }

    @Test
    void ellipseApproximationIsExactOnTheAxes() {
        Ellipse ellipse = new Ellipse(1.0D, 0.5D);
        assertEquals(0.0D, ellipse.signedDistance(1.0D, 0.0D), EXACT);
        assertEquals(0.0D, ellipse.signedDistance(0.0D, 0.5D), EXACT);
        assertEquals(-0.5D, ellipse.signedDistance(0.0D, 0.0D), EXACT);
        assertTrue(ellipse.signedDistance(1.2D, 0.0D) > 0.0D);
    }

    private static double box(double u, double v, double halfWidth, double halfHeight, double radius) {
        double qx = Math.abs(u) - halfWidth + radius;
        double qy = Math.abs(v) - halfHeight + radius;
        double outside = Math.hypot(Math.max(qx, 0.0D), Math.max(qy, 0.0D));
        return outside + Math.min(Math.max(qx, qy), 0.0D) - radius;
    }

    private static double polygon(double u, double v, int sides, double radius, double rotation) {
        double best = Double.POSITIVE_INFINITY;
        boolean inside = true;
        for (int index = 0; index < sides; index++) {
            double first = Math.toRadians(90.0D + rotation + 360.0D * index / sides);
            double second = Math.toRadians(90.0D + rotation + 360.0D * (index + 1) / sides);
            double ax = radius * Math.cos(first);
            double ay = radius * Math.sin(first);
            double bx = radius * Math.cos(second);
            double by = radius * Math.sin(second);
            double dx = bx - ax;
            double dy = by - ay;
            double t = Math.max(0.0D, Math.min(1.0D, ((u - ax) * dx + (v - ay) * dy) / (dx * dx + dy * dy)));
            best = Math.min(best, Math.hypot(ax + dx * t - u, ay + dy * t - v));
            if (dx * (v - ay) - dy * (u - ax) < 0.0D) {
                inside = false;
            }
        }
        return inside ? -best : best;
    }
}
