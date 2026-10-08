package art.arcane.optics.shape;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import art.arcane.optics.math.Vec2d;

final class ShapeVec2dTest {
    @Test
    void pointMatchesPointInto() {
        SplittableRandom random = new SplittableRandom(111L);
        double[] out = new double[2];
        for (int sample = 0; sample < 1_000; sample++) {
            PlaneTransform transform = new PlaneTransform(random.nextDouble(-3.0D, 3.0D), random.nextDouble(-3.0D, 3.0D),
                random.nextDouble(-3.0D, 3.0D), random.nextDouble(-3.0D, 3.0D), random.nextDouble(-9.0D, 9.0D), random.nextDouble(-9.0D, 9.0D));
            Vec2d point = new Vec2d(random.nextDouble(-5.0D, 5.0D), random.nextDouble(-5.0D, 5.0D));
            transform.pointInto(point.u(), point.v(), out);
            Vec2d mapped = transform.point(point);
            assertEquals(out[0], mapped.u(), 0.0D);
            assertEquals(out[1], mapped.v(), 0.0D);
        }
        assertEquals(new Vec2d(-2.0D, 1.0D), PlaneTransform.rotation(90.0D).point(new Vec2d(1.0D, 2.0D)));
        assertEquals(new Vec2d(4.0D, 7.0D), PlaneTransform.translation(3.0D, 5.0D).point(new Vec2d(1.0D, 2.0D)));
    }

    @Test
    void polygonFromPointsKeepsTheirOrder() {
        List<Vec2d> points = List.of(new Vec2d(-0.5D, -0.5D), new Vec2d(0.5D, -0.5D), new Vec2d(0.0D, 0.75D));
        Polygon polygon = Polygon.of(points);
        assertArrayEquals(new double[] {-0.5D, -0.5D, 0.5D, -0.5D, 0.0D, 0.75D}, polygon.points(), 0.0D);
        assertEquals(new Polygon(new double[] {-0.5D, -0.5D, 0.5D, -0.5D, 0.0D, 0.75D}), polygon);
        assertEquals(polygon, Shapes.polygon(points));
        assertEquals(Shapes.polygon(-0.5D, -0.5D, 0.5D, -0.5D, 0.0D, 0.75D), Shapes.polygon(points));
        assertTrue(polygon.contains(0.0D, 0.0D));
        assertThrows(IllegalArgumentException.class, () -> Polygon.of(List.of(new Vec2d(0.0D, 0.0D), new Vec2d(1.0D, 0.0D))));
        assertThrows(IllegalArgumentException.class, () -> Shapes.polygon(List.of(new Vec2d(0.0D, 0.0D), new Vec2d(1.0D, 0.0D),
            new Vec2d(Double.NaN, 1.0D))));
        assertThrows(NullPointerException.class, () -> Polygon.of(null));
    }
}
