package art.arcane.optics.transform;

import static art.arcane.optics.transform.TransformFixtures.assertVector;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.math.Axis;
import art.arcane.optics.math.Box;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

final class AffineFrameBoxNormalTest {
    @Test
    void frameMatchesTheRigidTransformForEveryPermutation() {
        List<Frame> frames = frames();
        for (int index = 0; index < 48; index++) {
            OpticTransform transform = OpticTransform.of(AxisPermutation.ofIndex(index), 7.0D, -3.0D, 2.0D);
            Affine affine = Affine.of(transform);
            for (Frame frame : frames) {
                assertEquals(transform.frame(frame), affine.frame(frame), transform + " on " + frame.getNormal() + "/" + frame.getUp());
            }
        }
    }

    @Test
    void frameSnapsNearlyAxisAlignedImagesAndFollowsShear() {
        Frame wall = Frame.fromNormalUp(Face.S, Face.U);
        Affine tilted = Affine.rotation(Vec3d.UNIT_Y, Math.toRadians(10.0D));
        assertEquals(wall, tilted.frame(wall));
        Affine quarter = Affine.rotation(Vec3d.UNIT_Y, Math.toRadians(80.0D));
        assertEquals(Frame.fromNormalUp(Face.E, Face.U), quarter.frame(wall));
        Affine leaning = Affine.shear(0.0D, 0.0D, 0.0D, 3.0D, 0.0D, 0.0D);
        assertEquals(Frame.fromNormalUp(Face.S, Face.U), leaning.frame(wall));
        Affine stretched = Affine.scale(5.0D, 0.25D, 3.0D);
        assertEquals(wall, stretched.frame(wall));
    }

    @Test
    void frameThrowsWhenNormalAndUpCollapse() {
        Frame wall = Frame.fromNormalUp(Face.S, Face.U);
        assertThrows(IllegalArgumentException.class, () -> Affine.scale(1.0D, 0.0D, 1.0D).frame(wall));
        assertThrows(IllegalArgumentException.class, () -> Affine.scale(0.0D).frame(wall));
        assertThrows(IllegalArgumentException.class, () -> Affine.of(1.0D, 0.0D, 0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.0D).frame(wall));
    }

    @Test
    void boxBoundsEveryMappedCorner() {
        SplittableRandom random = new SplittableRandom(81L);
        for (int sample = 0; sample < 1_000; sample++) {
            Affine affine = TransformFixtures.general(random);
            Box box = new Box(TransformFixtures.vector(random, 10.0D), TransformFixtures.vector(random, 10.0D));
            Box mapped = affine.box(box);
            double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
            double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (int corner = 0; corner < 8; corner++) {
                Vec3d point = affine.point(new Vec3d((corner & 1) == 0 ? box.getXa() : box.getXb(), (corner & 2) == 0 ? box.getYa() : box.getYb(),
                    (corner & 4) == 0 ? box.getZa() : box.getZb()));
                for (int axis = 0; axis < 3; axis++) {
                    min[axis] = Math.min(min[axis], point.component(axis));
                    max[axis] = Math.max(max[axis], point.component(axis));
                }
            }
            for (int axis = 0; axis < 3; axis++) {
                assertEquals(min[axis], mapped.min(axis), 0.0D);
                assertEquals(max[axis], mapped.max(axis), 0.0D);
            }
        }
        for (int index = 0; index < 48; index++) {
            OpticTransform transform = OpticTransform.of(AxisPermutation.ofIndex(index), 1.5D, -2.0D, 8.0D);
            Box box = new Box(-1.0D, 3.0D, 0.0D, 2.0D, 5.0D, 9.0D);
            Box expected = transform.box(box);
            Box actual = Affine.of(transform).box(box);
            for (int axis = 0; axis < 3; axis++) {
                assertEquals(expected.min(axis), actual.min(axis), 0.0D);
                assertEquals(expected.max(axis), actual.max(axis), 0.0D);
            }
        }
    }

    @Test
    void normalStaysPerpendicularToMappedSurfaces() {
        SplittableRandom random = new SplittableRandom(82L);
        for (int sample = 0; sample < 1_000; sample++) {
            Affine affine = TransformFixtures.general(random);
            if (random.nextBoolean()) {
                affine = affine.compose(Affine.reflection(Axis.Z));
            }
            Vec3d tangentA = TransformFixtures.direction(random);
            Vec3d tangentB = TransformFixtures.direction(random);
            Vec3d normal = tangentA.cross(tangentB).normalize();
            Vec3d mapped = affine.normal(normal);
            assertEquals(1.0D, mapped.length(), 1.0E-12D);
            assertEquals(0.0D, mapped.dot(affine.vector(tangentA)), 1.0E-9D);
            assertEquals(0.0D, mapped.dot(affine.vector(tangentB)), 1.0E-9D);
            Vec3d expectedSide = affine.vector(tangentA).cross(affine.vector(tangentB));
            double orientation = affine.reflects() ? -1.0D : 1.0D;
            assertEquals(orientation, Math.signum(mapped.dot(expectedSide)), 0.0D);
        }
        assertVector(Vec3d.UNIT_Y, Affine.scale(5.0D, 0.1D, 2.0D).normal(Vec3d.UNIT_Y), 1.0E-12D);
        assertVector(Vec3d.UNIT_Y, Affine.shear(3.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D).normal(Vec3d.UNIT_Y), 1.0E-12D);
        assertVector(new Vec3d(1.0D, -3.0D, 0.0D).normalize(), Affine.shear(3.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D).normal(Vec3d.UNIT_X), 1.0E-12D);
        assertVector(Vec3d.UNIT_Z.negate(), Affine.reflection(Axis.Z).normal(Vec3d.UNIT_Z), 1.0E-12D);
        for (int index = 0; index < 48; index++) {
            OpticTransform transform = OpticTransform.of(AxisPermutation.ofIndex(index), 0.0D, 0.0D, 0.0D);
            for (Face face : Face.values()) {
                assertVector(transform.face(face).toVector(), Affine.of(transform).normal(face.toVector()), 0.0D);
            }
        }
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
