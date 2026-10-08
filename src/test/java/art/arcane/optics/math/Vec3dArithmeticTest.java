package art.arcane.optics.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

final class Vec3dArithmeticTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void constantsAreTheOriginAndTheUnitAxes() {
        assertEquals(new Vec3d(0.0D, 0.0D, 0.0D), Vec3d.ZERO);
        assertEquals(new Vec3d(1.0D, 0.0D, 0.0D), Vec3d.UNIT_X);
        assertEquals(new Vec3d(0.0D, 1.0D, 0.0D), Vec3d.UNIT_Y);
        assertEquals(new Vec3d(0.0D, 0.0D, 1.0D), Vec3d.UNIT_Z);
    }

    @Test
    void crossFollowsTheRightHandRule() {
        assertEquals(Vec3d.UNIT_Z, Vec3d.UNIT_X.cross(Vec3d.UNIT_Y));
        assertEquals(Vec3d.UNIT_X, Vec3d.UNIT_Y.cross(Vec3d.UNIT_Z));
        assertEquals(Vec3d.UNIT_Y, Vec3d.UNIT_Z.cross(Vec3d.UNIT_X));
        assertVector(Vec3d.UNIT_Z.negate(), Vec3d.UNIT_Y.cross(Vec3d.UNIT_X), 0.0D);
    }

    @Test
    void crossIsAntiCommutativeAndPerpendicularToBothOperands() {
        SplittableRandom random = new SplittableRandom(31L);
        for (int sample = 0; sample < 1_000; sample++) {
            Vec3d a = random(random);
            Vec3d b = random(random);
            Vec3d ab = a.cross(b);
            Vec3d ba = b.cross(a);
            assertVector(ab, ba.negate(), EPSILON);
            assertEquals(0.0D, ab.dot(a), EPSILON * 100.0D);
            assertEquals(0.0D, ab.dot(b), EPSILON * 100.0D);
            double sine = ab.length() / (a.length() * b.length());
            double cosine = a.dot(b) / (a.length() * b.length());
            assertEquals(1.0D, sine * sine + cosine * cosine, EPSILON);
            assertVector(Vec3d.ZERO, a.cross(a), 0.0D);
        }
    }

    @Test
    void negateAndLengthAreConsistent() {
        SplittableRandom random = new SplittableRandom(7L);
        for (int sample = 0; sample < 1_000; sample++) {
            Vec3d a = random(random);
            assertVector(Vec3d.ZERO, a.add(a.negate()), 0.0D);
            assertEquals(a, a.negate().negate());
            assertEquals(Math.sqrt(a.lengthSquared()), a.length(), 0.0D);
            assertEquals(a.length(), a.negate().length(), 0.0D);
        }
        assertEquals(5.0D, new Vec3d(3.0D, 0.0D, 4.0D).length(), 0.0D);
    }

    @Test
    void multiplyByVectorScalesEachComponent() {
        assertEquals(new Vec3d(2.0D, -6.0D, 0.5D), new Vec3d(1.0D, 2.0D, 0.25D).multiply(new Vec3d(2.0D, -3.0D, 2.0D)));
        Vec3d a = new Vec3d(1.5D, -2.5D, 3.5D);
        assertEquals(a, a.multiply(new Vec3d(1.0D, 1.0D, 1.0D)));
        assertEquals(a.multiply(2.0D), a.multiply(new Vec3d(2.0D, 2.0D, 2.0D)));
    }

    @Test
    void lerpHitsBothEndpointsAndTheMidpoint() {
        SplittableRandom random = new SplittableRandom(11L);
        for (int sample = 0; sample < 1_000; sample++) {
            Vec3d a = random(random);
            Vec3d b = random(random);
            assertEquals(a, a.lerp(b, 0.0D));
            assertVector(b, a.lerp(b, 1.0D), EPSILON);
            assertVector(a.add(b).multiply(0.5D), a.lerp(b, 0.5D), EPSILON);
        }
        assertEquals(new Vec3d(3.0D, 0.0D, 0.0D), Vec3d.ZERO.lerp(Vec3d.UNIT_X, 3.0D));
    }

    @Test
    void finitenessRejectsNanAndInfinity() {
        assertTrue(new Vec3d(1.0D, -2.0D, 3.0D).isFinite());
        assertFalse(new Vec3d(Double.NaN, 0.0D, 0.0D).isFinite());
        assertFalse(new Vec3d(0.0D, Double.POSITIVE_INFINITY, 0.0D).isFinite());
        assertFalse(new Vec3d(0.0D, 0.0D, Double.NEGATIVE_INFINITY).isFinite());
    }

    @Test
    void arrayConversionRoundTrips() {
        Vec3d a = new Vec3d(1.25D, -7.5D, 9.0D);
        double[] out = new double[] {0.0D, 0.0D, 0.0D, 42.0D};
        a.into(out);
        assertEquals(1.25D, out[0], 0.0D);
        assertEquals(-7.5D, out[1], 0.0D);
        assertEquals(9.0D, out[2], 0.0D);
        assertEquals(42.0D, out[3], 0.0D);
        assertEquals(a, Vec3d.of(out));
    }

    private static Vec3d random(SplittableRandom random) {
        return new Vec3d(random.nextDouble(-10.0D, 10.0D), random.nextDouble(-10.0D, 10.0D), random.nextDouble(-10.0D, 10.0D));
    }

    private static void assertVector(Vec3d expected, Vec3d actual, double tolerance) {
        assertEquals(expected.x(), actual.x(), tolerance, "x of " + actual);
        assertEquals(expected.y(), actual.y(), tolerance, "y of " + actual);
        assertEquals(expected.z(), actual.z(), tolerance, "z of " + actual);
    }
}
