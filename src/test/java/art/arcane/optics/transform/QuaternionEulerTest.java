package art.arcane.optics.transform;

import static art.arcane.optics.transform.TransformFixtures.assertAffine;
import static art.arcane.optics.transform.TransformFixtures.assertSameRotation;
import static art.arcane.optics.transform.TransformFixtures.assertVector;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import art.arcane.optics.math.Angles;
import art.arcane.optics.math.Vec3d;

final class QuaternionEulerTest {
    @Test
    void everyOrderRoundTripsRandomAngles() {
        SplittableRandom random = new SplittableRandom(21L);
        for (EulerOrder order : EulerOrder.values()) {
            int middle = order.name().charAt(1) - 'X';
            for (int sample = 0; sample < 200; sample++) {
                double[] degrees = new double[3];
                for (int axis = 0; axis < 3; axis++) {
                    degrees[axis] = axis == middle ? random.nextDouble(-89.0D, 89.0D) : random.nextDouble(-180.0D, 180.0D);
                }
                EulerAngles angles = new EulerAngles(degrees[0], degrees[1], degrees[2], order).wrapped();
                EulerAngles recovered = Quaternion.euler(angles).euler(order).wrapped();
                assertSame(order, recovered.order());
                assertAngle(angles.x(), recovered.x(), order + " x");
                assertAngle(angles.y(), recovered.y(), order + " y");
                assertAngle(angles.z(), recovered.z(), order + " z");
            }
        }
    }

    @Test
    void orderNamesTheIntrinsicRotationSequence() {
        SplittableRandom random = new SplittableRandom(22L);
        Vec3d[] axes = {Vec3d.UNIT_X, Vec3d.UNIT_Y, Vec3d.UNIT_Z};
        for (EulerOrder order : EulerOrder.values()) {
            for (int sample = 0; sample < 50; sample++) {
                double[] degrees = {random.nextDouble(-180.0D, 180.0D), random.nextDouble(-180.0D, 180.0D), random.nextDouble(-180.0D, 180.0D)};
                Quaternion expected = Quaternion.IDENTITY;
                for (int position = 0; position < 3; position++) {
                    int axis = order.name().charAt(position) - 'X';
                    expected = expected.multiply(Quaternion.axisAngleDegrees(axes[axis], degrees[axis]));
                }
                EulerAngles angles = new EulerAngles(degrees[0], degrees[1], degrees[2], order);
                assertSameRotation(expected, Quaternion.euler(angles), 1.0E-12D);
                assertSameRotation(expected, angles.quaternion(), 1.0E-12D);
                assertAffine(Affine.rotation(expected), Affine.rotation(angles), 1.0E-12D);
            }
        }
    }

    @Test
    void yawPitchRollFollowsTheMinecraftLookConvention() {
        SplittableRandom random = new SplittableRandom(23L);
        for (int sample = 0; sample < 1_000; sample++) {
            float yaw = (float) random.nextDouble(-180.0D, 180.0D);
            float pitch = (float) random.nextDouble(-90.0D, 90.0D);
            double roll = random.nextDouble(-180.0D, 180.0D);
            EulerAngles look = EulerAngles.yawPitchRoll(yaw, pitch, roll);
            assertSame(EulerOrder.YXZ, look.order());
            Quaternion q = look.quaternion();
            Vec3d forward = Angles.direction(yaw, pitch);
            assertVector(forward, q.rotate(Vec3d.UNIT_Z), 1.0E-9D);
            Vec3d up = EulerAngles.yawPitchRoll(yaw, pitch, 0.0D).quaternion().rotate(Vec3d.UNIT_Y);
            assertEquals(0.0D, up.dot(forward), 1.0E-12D);
            assertTrue(up.y() >= -1.0E-12D, "zero roll keeps the camera upright: " + up);
            Vec3d rolledUp = q.rotate(Vec3d.UNIT_Y);
            assertEquals(Math.cos(Math.toRadians(roll)), rolledUp.dot(up), 1.0E-9D);
        }
        assertVector(Vec3d.UNIT_X.negate(), EulerAngles.yawPitchRoll(90.0D, 0.0D, 0.0D).quaternion().rotate(Vec3d.UNIT_Z), 1.0E-12D);
        assertVector(Vec3d.UNIT_Y.negate(), EulerAngles.yawPitchRoll(0.0D, 90.0D, 0.0D).quaternion().rotate(Vec3d.UNIT_Z), 1.0E-12D);
    }

    @Test
    void wrappedFoldsEachAngleIntoTheHalfOpenRange() {
        EulerAngles wrapped = new EulerAngles(190.0D, -190.0D, 540.0D, EulerOrder.ZXY).wrapped();
        assertEquals(-170.0D, wrapped.x(), 1.0E-12D);
        assertEquals(170.0D, wrapped.y(), 1.0E-12D);
        assertEquals(180.0D, wrapped.z(), 1.0E-12D);
        assertSame(EulerOrder.ZXY, wrapped.order());
        EulerAngles edges = new EulerAngles(-180.0D, 180.0D, -720.0D, EulerOrder.XYZ).wrapped();
        assertEquals(180.0D, edges.x(), 0.0D);
        assertEquals(180.0D, edges.y(), 0.0D);
        assertEquals(0.0D, edges.z(), 0.0D);
    }

    @Test
    void gimbalLockedRotationsStillRecoverTheSameRotation() {
        SplittableRandom random = new SplittableRandom(24L);
        for (EulerOrder order : EulerOrder.values()) {
            int middle = order.name().charAt(1) - 'X';
            for (double lock : new double[] {90.0D, -90.0D}) {
                for (int sample = 0; sample < 20; sample++) {
                    double[] degrees = {random.nextDouble(-180.0D, 180.0D), random.nextDouble(-180.0D, 180.0D), random.nextDouble(-180.0D, 180.0D)};
                    degrees[middle] = lock;
                    Quaternion q = Quaternion.euler(new EulerAngles(degrees[0], degrees[1], degrees[2], order));
                    EulerAngles recovered = q.euler(order);
                    assertTrue(Double.isFinite(recovered.x()) && Double.isFinite(recovered.y()) && Double.isFinite(recovered.z()));
                    assertSameRotation(q, recovered.quaternion(), 1.0E-7D);
                }
            }
        }
    }

    private static void assertAngle(double expected, double actual, String label) {
        double difference = expected - actual;
        double folded = difference - 360.0D * Math.rint(difference / 360.0D);
        assertEquals(0.0D, folded, 1.0E-9D, label + ": expected " + expected + " but was " + actual);
    }
}
