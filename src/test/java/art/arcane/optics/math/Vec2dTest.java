package art.arcane.optics.math;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

final class Vec2dTest {
    private static final double EPSILON = 1.0E-12D;

    @Test
    void arithmeticWorksComponentWise() {
        Vec2d a = new Vec2d(1.5D, -2.0D);
        Vec2d b = new Vec2d(0.5D, 4.0D);
        assertEquals(new Vec2d(2.0D, 2.0D), a.add(b));
        assertEquals(new Vec2d(1.0D, -6.0D), a.subtract(b));
        assertEquals(new Vec2d(3.0D, -4.0D), a.multiply(2.0D));
        assertEquals(1.5D * 0.5D - 2.0D * 4.0D, a.dot(b), 0.0D);
        assertEquals(1.5D * 4.0D - (-2.0D) * 0.5D, a.cross(b), 0.0D);
        assertEquals(Vec2d.ZERO, a.subtract(a));
    }

    @Test
    void lengthsAndDistancesAgree() {
        Vec2d a = new Vec2d(3.0D, 4.0D);
        assertEquals(25.0D, a.lengthSquared(), 0.0D);
        assertEquals(5.0D, a.length(), 0.0D);
        assertEquals(5.0D, Vec2d.ZERO.distance(a), 0.0D);
        assertEquals(new Vec2d(0.6D, 0.8D), a.normalize());
        SplittableRandom random = new SplittableRandom(3L);
        for (int sample = 0; sample < 1_000; sample++) {
            Vec2d v = new Vec2d(random.nextDouble(-5.0D, 5.0D), random.nextDouble(-5.0D, 5.0D));
            assertEquals(1.0D, v.normalize().length(), EPSILON);
        }
    }

    @Test
    void crossOfTheUnitAxesIsPositiveCounterClockwise() {
        assertEquals(1.0D, new Vec2d(1.0D, 0.0D).cross(new Vec2d(0.0D, 1.0D)), 0.0D);
        assertEquals(-1.0D, new Vec2d(0.0D, 1.0D).cross(new Vec2d(1.0D, 0.0D)), 0.0D);
    }

    @Test
    void rotationIsCounterClockwiseAndPerpendicularIsAQuarterTurn() {
        assertVector(new Vec2d(0.0D, 1.0D), new Vec2d(1.0D, 0.0D).rotated(Math.PI / 2.0D));
        assertVector(new Vec2d(-1.0D, 0.0D), new Vec2d(0.0D, 1.0D).rotated(Math.PI / 2.0D));
        assertVector(new Vec2d(0.0D, 1.0D), new Vec2d(1.0D, 0.0D).perpendicular());
        SplittableRandom random = new SplittableRandom(5L);
        for (int sample = 0; sample < 1_000; sample++) {
            Vec2d v = new Vec2d(random.nextDouble(-5.0D, 5.0D), random.nextDouble(-5.0D, 5.0D));
            double angle = random.nextDouble(-10.0D, 10.0D);
            Vec2d rotated = v.rotated(angle);
            assertEquals(v.length(), rotated.length(), 1.0E-9D);
            assertEquals(Math.sin(angle) * v.lengthSquared(), v.cross(rotated), 1.0E-9D);
            assertVector(v.rotated(Math.PI / 2.0D), v.perpendicular());
            assertEquals(0.0D, v.dot(v.perpendicular()), 0.0D);
        }
    }

    @Test
    void lerpHitsEndpointsAndExtrapolates() {
        Vec2d a = new Vec2d(-1.0D, 2.0D);
        Vec2d b = new Vec2d(3.0D, -6.0D);
        assertEquals(a, a.lerp(b, 0.0D));
        assertEquals(b, a.lerp(b, 1.0D));
        assertEquals(new Vec2d(1.0D, -2.0D), a.lerp(b, 0.5D));
        assertEquals(new Vec2d(7.0D, -14.0D), a.lerp(b, 2.0D));
    }

    private static void assertVector(Vec2d expected, Vec2d actual) {
        assertEquals(expected.u(), actual.u(), 1.0E-9D, "u of " + actual);
        assertEquals(expected.v(), actual.v(), 1.0E-9D, "v of " + actual);
    }
}
