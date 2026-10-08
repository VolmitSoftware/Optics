package art.arcane.optics.transform;

import static art.arcane.optics.transform.TransformFixtures.assertSameRotation;
import static art.arcane.optics.transform.TransformFixtures.assertVector;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import com.sun.management.ThreadMXBean;

import art.arcane.optics.math.Vec3d;

final class QuaternionGroupTest {
    private static final double EPSILON = 1.0E-12D;

    @Test
    void identityIsTheUnitOfMultiplication() {
        SplittableRandom random = new SplittableRandom(1L);
        for (int sample = 0; sample < 1_000; sample++) {
            Quaternion q = TransformFixtures.rotation(random);
            assertQuaternion(q, Quaternion.IDENTITY.multiply(q), 0.0D);
            assertQuaternion(q, q.multiply(Quaternion.IDENTITY), 0.0D);
        }
        assertEquals(new Quaternion(0.0D, 0.0D, 0.0D, 1.0D), Quaternion.IDENTITY);
    }

    @Test
    void inverseUndoesMultiplicationEvenWhenNotNormalized() {
        SplittableRandom random = new SplittableRandom(2L);
        for (int sample = 0; sample < 1_000; sample++) {
            Quaternion q = TransformFixtures.rotation(random);
            Quaternion scaled = new Quaternion(q.x() * 3.0D, q.y() * 3.0D, q.z() * 3.0D, q.w() * 3.0D);
            assertQuaternion(Quaternion.IDENTITY, q.multiply(q.inverse()), EPSILON);
            assertQuaternion(Quaternion.IDENTITY, scaled.multiply(scaled.inverse()), EPSILON);
            assertQuaternion(Quaternion.IDENTITY, scaled.inverse().multiply(scaled), EPSILON);
            assertQuaternion(q.conjugate(), q.inverse(), EPSILON);
        }
    }

    @Test
    void multiplicationIsAssociative() {
        SplittableRandom random = new SplittableRandom(3L);
        for (int sample = 0; sample < 1_000; sample++) {
            Quaternion a = TransformFixtures.rotation(random);
            Quaternion b = TransformFixtures.rotation(random);
            Quaternion c = TransformFixtures.rotation(random);
            assertQuaternion(a.multiply(b).multiply(c), a.multiply(b.multiply(c)), EPSILON);
        }
    }

    @Test
    void multiplyAppliesTheRightOperandFirst() {
        SplittableRandom random = new SplittableRandom(4L);
        for (int sample = 0; sample < 1_000; sample++) {
            Quaternion a = TransformFixtures.rotation(random);
            Quaternion b = TransformFixtures.rotation(random);
            Vec3d v = TransformFixtures.vector(random, 10.0D);
            assertVector(a.rotate(b.rotate(v)), a.multiply(b).rotate(v), 1.0E-11D);
        }
        Quaternion yaw = Quaternion.axisAngleDegrees(Vec3d.UNIT_Y, 90.0D);
        Quaternion pitch = Quaternion.axisAngleDegrees(Vec3d.UNIT_X, 90.0D);
        assertVector(Vec3d.UNIT_X, yaw.multiply(pitch).rotate(Vec3d.UNIT_Y), EPSILON);
    }

    @Test
    void rotateMatchesTheAffineRotation() {
        SplittableRandom random = new SplittableRandom(5L);
        for (int sample = 0; sample < 1_000; sample++) {
            Quaternion q = TransformFixtures.rotation(random);
            Vec3d v = TransformFixtures.vector(random, 10.0D);
            Vec3d rotated = q.rotate(v);
            assertVector(Affine.rotation(q).vector(v), rotated, 1.0E-11D);
            assertVector(q.affine().vector(v), rotated, 1.0E-11D);
            assertEquals(v.length(), rotated.length(), 1.0E-11D);
            Quaternion scaled = new Quaternion(q.x() * 2.5D, q.y() * 2.5D, q.z() * 2.5D, q.w() * 2.5D);
            assertVector(rotated, scaled.rotate(v), 1.0E-11D);
            assertVector(rotated, q.negate().rotate(v), 1.0E-11D);
        }
    }

    @Test
    void rotateIntoMatchesRotateWithoutAllocating() {
        SplittableRandom random = new SplittableRandom(6L);
        Quaternion q = TransformFixtures.rotation(random);
        double[] out = new double[3];
        for (int sample = 0; sample < 100; sample++) {
            Vec3d v = TransformFixtures.vector(random, 10.0D);
            q.rotateInto(v.x(), v.y(), v.z(), out);
            assertVector(q.rotate(v), Vec3d.of(out), 0.0D);
        }
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        double sink = 0.0D;
        for (int warm = 0; warm < 20_000; warm++) {
            q.rotateInto(warm, 1.0D, 2.0D, out);
            sink += out[0];
        }
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int call = 0; call < 100_000; call++) {
            q.rotateInto(call, 1.0D, 2.0D, out);
            sink += out[0];
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        assertTrue(allocated < 1_024L, allocated + " bytes allocated by rotateInto");
        assertTrue(Double.isFinite(sink));
    }

    @Test
    void axisAngleFollowsTheRightHandRule() {
        assertVector(Vec3d.UNIT_X, Quaternion.axisAngleDegrees(Vec3d.UNIT_Y, 90.0D).rotate(Vec3d.UNIT_Z), EPSILON);
        assertVector(Vec3d.UNIT_Y, Quaternion.axisAngle(Vec3d.UNIT_Z, Math.PI / 2.0D).rotate(Vec3d.UNIT_X), EPSILON);
        assertVector(Vec3d.UNIT_Z, Quaternion.axisAngle(new Vec3d(5.0D, 0.0D, 0.0D), Math.PI / 2.0D).rotate(Vec3d.UNIT_Y), EPSILON);
        assertVector(Vec3d.UNIT_Z.negate(), Quaternion.axisAngleDegrees(Vec3d.UNIT_X, 180.0D).rotate(Vec3d.UNIT_Z), EPSILON);
        assertThrows(IllegalArgumentException.class, () -> Quaternion.axisAngle(Vec3d.ZERO, 1.0D));
        assertThrows(IllegalArgumentException.class, () -> Quaternion.axisAngle(new Vec3d(Double.NaN, 0.0D, 0.0D), 1.0D));
    }

    @Test
    void angleAndAxisRecoverTheAxisAngle() {
        SplittableRandom random = new SplittableRandom(7L);
        for (int sample = 0; sample < 1_000; sample++) {
            Vec3d axis = TransformFixtures.direction(random);
            double angle = random.nextDouble(1.0E-3D, Math.PI - 1.0E-3D);
            Quaternion q = Quaternion.axisAngle(axis, angle);
            assertEquals(angle, q.angle(), 1.0E-12D);
            assertVector(axis, q.axis(), 1.0E-9D);
            assertEquals(angle, q.negate().angle(), 1.0E-12D);
            assertVector(axis, q.negate().axis(), 1.0E-9D);
            Quaternion longWay = Quaternion.axisAngle(axis, 2.0D * Math.PI - angle);
            assertEquals(angle, longWay.angle(), 1.0E-9D);
            assertVector(axis.negate(), longWay.axis(), 1.0E-9D);
        }
        assertEquals(0.0D, Quaternion.IDENTITY.angle(), 0.0D);
        assertEquals(Vec3d.UNIT_Y, Quaternion.IDENTITY.axis());
    }

    @Test
    void normalizeAndPredicatesBehave() {
        Quaternion doubled = new Quaternion(0.0D, 0.0D, 0.0D, 2.0D);
        assertEquals(4.0D, doubled.lengthSquared(), 0.0D);
        assertEquals(2.0D, doubled.length(), 0.0D);
        assertEquals(Quaternion.IDENTITY, doubled.normalize());
        assertFalse(doubled.isNormalized(1.0E-9D));
        assertTrue(Quaternion.IDENTITY.isNormalized(0.0D));
        assertTrue(new Quaternion(0.0D, 0.0D, 0.0D, 1.0D + 1.0E-10D).isNormalized(1.0E-9D));
        assertTrue(Quaternion.IDENTITY.isFinite());
        assertFalse(new Quaternion(Double.NaN, 0.0D, 0.0D, 1.0D).isFinite());
        assertFalse(new Quaternion(0.0D, 0.0D, Double.POSITIVE_INFINITY, 1.0D).isFinite());
        assertThrows(IllegalStateException.class, () -> new Quaternion(0.0D, 0.0D, 0.0D, 0.0D).normalize());
        assertThrows(IllegalStateException.class, () -> new Quaternion(0.0D, 0.0D, 0.0D, 0.0D).inverse());
        assertQuaternion(new Quaternion(-1.0D, -2.0D, -3.0D, 4.0D), new Quaternion(1.0D, 2.0D, 3.0D, 4.0D).conjugate(), 0.0D);
        assertQuaternion(new Quaternion(-1.0D, -2.0D, -3.0D, -4.0D), new Quaternion(1.0D, 2.0D, 3.0D, 4.0D).negate(), 0.0D);
        assertEquals(1.0D * 5.0D + 2.0D * 6.0D + 3.0D * 7.0D + 4.0D * 8.0D,
            new Quaternion(1.0D, 2.0D, 3.0D, 4.0D).dot(new Quaternion(5.0D, 6.0D, 7.0D, 8.0D)), 0.0D);
    }

    @Test
    void fromToMapsTheFirstDirectionOntoTheSecond() {
        SplittableRandom random = new SplittableRandom(8L);
        for (int sample = 0; sample < 1_000; sample++) {
            Vec3d from = TransformFixtures.vector(random, 5.0D);
            Vec3d to = TransformFixtures.vector(random, 5.0D);
            Quaternion q = Quaternion.fromTo(from, to);
            assertTrue(q.isNormalized(1.0E-12D));
            assertVector(to.normalize(), q.rotate(from.normalize()), 1.0E-9D);
            double cosine = from.normalize().dot(to.normalize());
            assertEquals(Math.acos(Math.max(-1.0D, Math.min(1.0D, cosine))), q.angle(), 1.0E-7D);
        }
        assertQuaternion(Quaternion.IDENTITY, Quaternion.fromTo(Vec3d.UNIT_X, new Vec3d(3.0D, 0.0D, 0.0D)), EPSILON);
    }

    @Test
    void fromToPicksADeterministicAxisForOppositeDirections() {
        SplittableRandom random = new SplittableRandom(9L);
        Vec3d[] directions = {Vec3d.UNIT_X, Vec3d.UNIT_Y, Vec3d.UNIT_Z, Vec3d.UNIT_X.negate(), Vec3d.UNIT_Y.negate(), Vec3d.UNIT_Z.negate(),
            TransformFixtures.direction(random), TransformFixtures.direction(random)};
        for (Vec3d direction : directions) {
            Quaternion q = Quaternion.fromTo(direction, direction.negate());
            assertEquals(Math.PI, q.angle(), 1.0E-12D);
            assertVector(direction.negate(), q.rotate(direction), 1.0E-12D);
            assertEquals(0.0D, q.axis().dot(direction), 1.0E-12D);
            assertEquals(q, Quaternion.fromTo(direction, direction.negate()));
        }
    }

    @Test
    void lookAlongAlignsForwardAndUp() {
        assertQuaternion(Quaternion.IDENTITY, Quaternion.lookAlong(Vec3d.UNIT_Z, Vec3d.UNIT_Y), EPSILON);
        SplittableRandom random = new SplittableRandom(10L);
        for (int sample = 0; sample < 1_000; sample++) {
            Vec3d forward = TransformFixtures.vector(random, 3.0D);
            Vec3d up = TransformFixtures.vector(random, 3.0D);
            Quaternion q = Quaternion.lookAlong(forward, up);
            Vec3d f = forward.normalize();
            Vec3d expectedUp = up.subtract(f.multiply(up.dot(f))).normalize();
            assertTrue(q.isNormalized(1.0E-12D));
            assertVector(f, q.rotate(Vec3d.UNIT_Z), 1.0E-9D);
            assertVector(expectedUp, q.rotate(Vec3d.UNIT_Y), 1.0E-9D);
            assertVector(expectedUp.cross(f), q.rotate(Vec3d.UNIT_X), 1.0E-9D);
        }
        Quaternion parallel = Quaternion.lookAlong(Vec3d.UNIT_Y, Vec3d.UNIT_Y);
        assertVector(Vec3d.UNIT_Y, parallel.rotate(Vec3d.UNIT_Z), EPSILON);
        assertSameRotation(Quaternion.fromTo(Vec3d.UNIT_Z, Vec3d.UNIT_Y), parallel, EPSILON);
        assertThrows(IllegalArgumentException.class, () -> Quaternion.lookAlong(Vec3d.ZERO, Vec3d.UNIT_Y));
    }

    private static void assertQuaternion(Quaternion expected, Quaternion actual, double tolerance) {
        assertEquals(expected.x(), actual.x(), tolerance, "x of " + actual);
        assertEquals(expected.y(), actual.y(), tolerance, "y of " + actual);
        assertEquals(expected.z(), actual.z(), tolerance, "z of " + actual);
        assertEquals(expected.w(), actual.w(), tolerance, "w of " + actual);
    }
}
