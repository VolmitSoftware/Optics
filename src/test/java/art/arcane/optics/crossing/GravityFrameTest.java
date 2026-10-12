package art.arcane.optics.crossing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.math.Angles;
import art.arcane.optics.math.Box;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.transform.Quaternion;

final class GravityFrameTest {
    private static final double TOLERANCE = 1.0E-6D;
    private static final Vec3d FEET = new Vec3d(12.0D, 64.0D, -7.0D);

    @Test
    void allProperFramesMapTheirAxesAndRoundTripVectors() {
        List<AxisPermutation> rotations = rotations();
        assertEquals(24, rotations.size());
        assertEquals(Face.D, GravityFrame.WORLD.down());
        assertEquals(Face.U, GravityFrame.WORLD.up());
        Vec3d local = new Vec3d(0.75D, -2.0D, 4.5D);
        for (AxisPermutation rotation : rotations) {
            GravityFrame frame = new GravityFrame(rotation);
            assertEquals(rotation.face(Face.D), frame.down());
            assertEquals(rotation.face(Face.U), frame.up());
            assertVector(local, frame.toLocal(frame.toWorld(local)));
            assertVector(local, new GravityFrame(rotation.inverse()).toWorld(frame.toWorld(local)));
            for (Face face : Face.values()) {
                assertVector(rotation.face(face).toVector(), frame.toWorld(face.toVector()));
            }
            double[] shared = {local.x(), local.y(), local.z()};
            frame.toWorldInto(shared[0], shared[1], shared[2], shared);
            assertVector(frame.toWorld(local), Vec3d.of(shared));
            frame.toLocalInto(shared[0], shared[1], shared[2], shared);
            assertVector(local, Vec3d.of(shared));
        }
    }

    @Test
    void portalRotationsComposeAndUndoWithoutLosingTheMovementBasis() {
        for (AxisPermutation body : rotations()) {
            GravityFrame frame = new GravityFrame(body);
            for (AxisPermutation portal : rotations()) {
                GravityFrame crossed = frame.transformed(portal);
                assertEquals(portal.compose(body), crossed.orientation());
                assertEquals(frame, crossed.transformed(portal.inverse()));
                assertVector(portal.face(frame.up()).toVector(), crossed.up().toVector());
            }
        }
    }

    @Test
    void walkingJumpingAndAccelerationFollowEveryGravityDirection() {
        Vec3d movement = new Vec3d(0.5D, -0.75D, 1.25D);
        for (AxisPermutation rotation : rotations()) {
            GravityFrame frame = new GravityFrame(rotation);
            Vec3d walking = frame.projectMovement(movement);
            assertEquals(0.0D, walking.dot(frame.up().toVector()), 0.0D);
            Vec3d local = frame.toLocal(movement);
            assertVector(frame.toWorld(new Vec3d(local.x(), 0.0D, local.z())), walking);
            assertVector(frame.down().toVector().multiply(0.08D), frame.acceleration(0.08D));
            assertVector(frame.up().toVector().multiply(0.42D), frame.jump(0.42D));
            assertVector(Vec3d.ZERO, frame.acceleration(0.0D));
            assertVector(Vec3d.ZERO, frame.jump(0.0D));
        }
    }

    @Test
    void cameraAndPlayerModelShareTheGravityBasis() {
        float[] yaws = {-270.0F, -45.0F, 0.0F, 90.0F, 245.0F};
        float[] pitches = {-90.0F, -35.0F, 0.0F, 65.0F, 90.0F};
        for (AxisPermutation rotation : rotations()) {
            GravityFrame frame = new GravityFrame(rotation);
            for (float yaw : yaws) {
                Quaternion model = frame.modelRotation(yaw);
                assertVector(frame.up().toVector(), model.rotate(Vec3d.UNIT_Y));
                assertVector(frame.toWorld(Angles.direction(yaw, 0.0F)), model.rotate(Vec3d.UNIT_Z));
                for (float pitch : pitches) {
                    LookTransfer look = frame.look(yaw, pitch);
                    Vec3d forward = Angles.direction(look.yaw(), look.pitch());
                    Vec3d unrolledUp = LookTransfer.cameraUp(look.yaw(), look.pitch());
                    assertVector(frame.toWorld(Angles.direction(yaw, pitch)), forward);
                    Vec3d cameraUp = Quaternion.axisAngleDegrees(forward, look.roll()).rotate(unrolledUp);
                    assertVector(frame.toWorld(LookTransfer.cameraUp(yaw, pitch)), cameraUp);
                }
            }
        }
    }

    @Test
    void eyesAndCollisionBoundsExtendFromFeetAlongBodyUp() {
        double width = 0.6D;
        double height = 1.8D;
        double eyeHeight = 1.62D;
        for (AxisPermutation rotation : rotations()) {
            GravityFrame frame = new GravityFrame(rotation);
            assertVector(FEET.add(frame.up().toVector().multiply(eyeHeight)), frame.eyePosition(FEET, eyeHeight));
            assertVector(FEET, frame.eyePosition(FEET, 0.0D));
            Box box = frame.bounds(FEET, width, height);
            assertVector(FEET.add(frame.up().toVector().multiply(height * 0.5D)), box.center());
            int verticalAxis = frame.up().axisIndex();
            for (int axis = 0; axis < 3; axis++) {
                assertEquals(axis == verticalAxis ? height : width, box.max(axis) - box.min(axis), 1.0E-12D);
            }
            assertEquals(FEET.component(verticalAxis), frame.up().sign() > 0 ? box.min(verticalAxis) : box.max(verticalAxis), 0.0D);
            assertTrue(box.contains(frame.eyePosition(FEET, eyeHeight)));
            for (int xSign : new int[] {-1, 1}) {
                for (int ySign : new int[] {0, 1}) {
                    for (int zSign : new int[] {-1, 1}) {
                        Vec3d corner = FEET.add(frame.toWorld(new Vec3d(xSign * width * 0.5D, ySign * height, zSign * width * 0.5D)));
                        assertTrue(box.contains(corner));
                    }
                }
            }
        }
    }

    @Test
    void reflectionFramesAndNonfiniteInputsAreRejected() {
        for (int index = 0; index < 48; index++) {
            AxisPermutation permutation = AxisPermutation.ofIndex(index);
            if (permutation.reflects()) {
                assertThrows(IllegalArgumentException.class, () -> new GravityFrame(permutation));
                assertThrows(IllegalArgumentException.class, () -> GravityFrame.WORLD.transformed(permutation));
            }
        }
        assertThrows(NullPointerException.class, () -> new GravityFrame(null));
        assertThrows(NullPointerException.class, () -> GravityFrame.WORLD.transformed(null));
        assertThrows(NullPointerException.class, () -> GravityFrame.WORLD.toWorld(null));
        for (double invalid : new double[] {-1.0D, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> GravityFrame.WORLD.acceleration(invalid));
            assertThrows(IllegalArgumentException.class, () -> GravityFrame.WORLD.jump(invalid));
            assertThrows(IllegalArgumentException.class, () -> GravityFrame.WORLD.eyePosition(FEET, invalid));
            assertThrows(IllegalArgumentException.class, () -> GravityFrame.WORLD.bounds(FEET, invalid, 1.8D));
            assertThrows(IllegalArgumentException.class, () -> GravityFrame.WORLD.bounds(FEET, 0.6D, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> GravityFrame.WORLD.bounds(FEET, 0.0D, 1.8D));
        assertThrows(IllegalArgumentException.class, () -> GravityFrame.WORLD.bounds(FEET, 0.6D, 0.0D));
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            Vec3d vector = new Vec3d(invalid, 1.0D, 0.0D);
            assertThrows(IllegalArgumentException.class, () -> GravityFrame.WORLD.toWorld(vector));
            assertThrows(IllegalArgumentException.class, () -> GravityFrame.WORLD.toLocal(vector));
            assertThrows(IllegalArgumentException.class, () -> GravityFrame.WORLD.projectMovement(vector));
            assertThrows(IllegalArgumentException.class, () -> GravityFrame.WORLD.eyePosition(vector, 1.62D));
            assertThrows(IllegalArgumentException.class, () -> GravityFrame.WORLD.bounds(vector, 0.6D, 1.8D));
            assertThrows(IllegalArgumentException.class, () -> GravityFrame.WORLD.look((float) invalid, 0.0F));
            assertThrows(IllegalArgumentException.class, () -> GravityFrame.WORLD.look(0.0F, (float) invalid));
            assertThrows(IllegalArgumentException.class, () -> GravityFrame.WORLD.modelRotation((float) invalid));
        }
    }

    private static List<AxisPermutation> rotations() {
        List<AxisPermutation> rotations = new ArrayList<AxisPermutation>(24);
        for (int index = 0; index < 48; index++) {
            AxisPermutation rotation = AxisPermutation.ofIndex(index);
            if (!rotation.reflects()) {
                rotations.add(rotation);
            }
        }
        return rotations;
    }

    private static void assertVector(Vec3d expected, Vec3d actual) {
        assertEquals(expected.x(), actual.x(), TOLERANCE);
        assertEquals(expected.y(), actual.y(), TOLERANCE);
        assertEquals(expected.z(), actual.z(), TOLERANCE);
    }
}
