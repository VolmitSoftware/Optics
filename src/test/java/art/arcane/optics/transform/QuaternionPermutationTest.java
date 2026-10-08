package art.arcane.optics.transform;

import static art.arcane.optics.transform.TransformFixtures.assertVector;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

final class QuaternionPermutationTest {
    private static final int PERMUTATIONS = 48;

    @Test
    void everyRotationRoundTripsThroughPermutation() {
        int rotations = 0;
        int reflections = 0;
        for (int index = 0; index < PERMUTATIONS; index++) {
            AxisPermutation permutation = AxisPermutation.ofIndex(index);
            if (permutation.reflects()) {
                reflections++;
                assertThrows(IllegalArgumentException.class, () -> Quaternion.of(permutation));
                continue;
            }
            rotations++;
            Quaternion q = Quaternion.of(permutation);
            assertTrue(q.isNormalized(1.0E-12D), q.toString());
            assertSame(permutation, q.permutation(1.0E-9D));
            assertSame(permutation, q.negate().permutation(1.0E-9D));
            for (Face face : Face.values()) {
                assertVector(permutation.face(face).toVector(), q.rotate(face.toVector()), 1.0E-12D);
            }
        }
        assertEquals(24, rotations);
        assertEquals(24, reflections);
    }

    @Test
    void betweenRotatesOneFrameOntoTheOther() {
        List<Frame> frames = frames();
        for (Frame from : frames) {
            for (Frame to : frames) {
                Quaternion q = Quaternion.between(from, to);
                assertVector(to.getNormal().toVector(), q.rotate(from.getNormal().toVector()), 1.0E-12D);
                assertVector(to.getUp().toVector(), q.rotate(from.getUp().toVector()), 1.0E-12D);
                assertVector(to.getRight().toVector(), q.rotate(from.getRight().toVector()), 1.0E-12D);
                assertSame(AxisPermutation.between(from, to), q.permutation(1.0E-9D));
            }
        }
    }

    @Test
    void permutationRefusesRotationsOutsideTheTolerance() {
        for (int index = 0; index < PERMUTATIONS; index++) {
            AxisPermutation permutation = AxisPermutation.ofIndex(index);
            if (permutation.reflects()) {
                continue;
            }
            Quaternion nudged = Quaternion.of(permutation).multiply(Quaternion.axisAngleDegrees(new Vec3d(1.0D, 2.0D, 3.0D), 1.0D));
            assertNull(nudged.permutation(1.0E-3D));
            assertSame(permutation, nudged.permutation(Math.toRadians(1.5D)));
        }
        assertNull(Quaternion.axisAngleDegrees(Vec3d.UNIT_Y, 45.0D).permutation(0.1D));
        assertSame(AxisPermutation.IDENTITY, Quaternion.axisAngleDegrees(Vec3d.UNIT_Y, 44.0D).permutation(Math.toRadians(45.0D)));
    }

    private static List<Frame> frames() {
        List<Frame> frames = new ArrayList<Frame>(24);
        for (Face normal : Face.values()) {
            for (Face up : Face.values()) {
                if (normal.getAxis() != up.getAxis()) {
                    frames.add(Frame.fromNormalUp(normal, up));
                }
            }
        }
        return frames;
    }
}
