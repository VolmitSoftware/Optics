package art.arcane.optics.transform;

import static art.arcane.optics.transform.TransformFixtures.assertAffine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import com.sun.management.ThreadMXBean;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

final class AffineOpticParityTest {
    private static final int PERMUTATIONS = 48;

    @Test
    void pointsAndVectorsMatchTheRigidTransformExactly() {
        SplittableRandom random = new SplittableRandom(51L);
        double[] expected = new double[3];
        double[] actual = new double[3];
        for (int index = 0; index < PERMUTATIONS; index++) {
            AxisPermutation permutation = AxisPermutation.ofIndex(index);
            for (int translation = 0; translation < 100; translation++) {
                OpticTransform transform = OpticTransform.of(permutation, random.nextDouble(-1.0E4D, 1.0E4D), random.nextDouble(-320.0D, 320.0D),
                    random.nextDouble(-1.0E4D, 1.0E4D));
                Affine affine = Affine.of(transform);
                for (int point = 0; point < 100; point++) {
                    double x = random.nextDouble(-1.0E4D, 1.0E4D);
                    double y = random.nextDouble(-320.0D, 320.0D);
                    double z = random.nextDouble(-1.0E4D, 1.0E4D);
                    transform.pointInto(x, y, z, expected);
                    affine.pointInto(x, y, z, actual);
                    assertEquals(expected[0], actual[0], 0.0D);
                    assertEquals(expected[1], actual[1], 0.0D);
                    assertEquals(expected[2], actual[2], 0.0D);
                    transform.vectorInto(x, y, z, expected);
                    affine.vectorInto(x, y, z, actual);
                    assertEquals(expected[0], actual[0], 0.0D);
                    assertEquals(expected[1], actual[1], 0.0D);
                    assertEquals(expected[2], actual[2], 0.0D);
                }
                assertEquals(transform.normalized(), affine.rigid(1.0E-9D));
            }
        }
    }

    @Test
    void framePairTransformsMatchWithinRounding() {
        SplittableRandom random = new SplittableRandom(52L);
        Face[] faces = Face.values();
        double[] expected = new double[3];
        double[] actual = new double[3];
        for (int sample = 0; sample < 2_000; sample++) {
            Frame from = frame(random, faces);
            Frame to = frame(random, faces);
            OpticTransform transform = OpticTransform.between(from, TransformFixtures.vector(random, 1.0E4D), to, TransformFixtures.vector(random, 1.0E4D));
            Affine affine = Affine.of(transform);
            double x = random.nextDouble(-1.0E4D, 1.0E4D);
            double y = random.nextDouble(-1.0E4D, 1.0E4D);
            double z = random.nextDouble(-1.0E4D, 1.0E4D);
            transform.pointInto(x, y, z, expected);
            affine.pointInto(x, y, z, actual);
            assertEquals(expected[0], actual[0], 1.0E-9D);
            assertEquals(expected[1], actual[1], 1.0E-9D);
            assertEquals(expected[2], actual[2], 1.0E-9D);
            assertEquals(transform.normalized(), affine.rigid(1.0E-9D));
            assertTrue(affine.isRigid(1.0E-12D));
            assertEquals(transform.reflects(), affine.reflects());
        }
    }

    @Test
    void rigidRefusesMatricesThatAreNotAxisPermutations() {
        assertNull(Affine.scale(2.0D).rigid(1.0E-9D));
        assertNull(Affine.rotation(Vec3d.UNIT_Y, Math.toRadians(30.0D)).rigid(1.0E-9D));
        assertNull(Affine.shear(0.1D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D).rigid(1.0E-9D));
        assertNull(Affine.scale(1.0D, 1.0D, 0.0D).rigid(1.0E-9D));
        assertNull(Affine.of(1.0D, 0.0D, 0.0D, 0.0D, 1.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 1.0D, 0.0D).rigid(1.0E-9D));
        Affine nearlyQuarter = Affine.rotation(Vec3d.UNIT_Y, Math.toRadians(90.0D) + 1.0E-12D).compose(Affine.translation(1.0D, 2.0D, 3.0D));
        OpticTransform snapped = nearlyQuarter.rigid(1.0E-9D);
        assertEquals(Face.S, snapped.face(Face.W));
        assertNull(nearlyQuarter.rigid(1.0E-14D));
    }

    @Test
    void ofRigidRoundTripsThroughAffineAndKeepsTheMatrix() {
        for (int index = 0; index < PERMUTATIONS; index++) {
            AxisPermutation permutation = AxisPermutation.ofIndex(index);
            OpticTransform transform = OpticTransform.of(permutation, 3.0D, -4.0D, 5.0D);
            Affine affine = Affine.of(transform);
            assertAffine(affine, Affine.of(affine.rigid(0.0D)), 0.0D);
            assertEquals(permutation.reflects() ? -1.0D : 1.0D, affine.determinant(), 0.0D);
            if (!permutation.reflects()) {
                assertAffine(Affine.translation(3.0D, -4.0D, 5.0D).compose(Affine.rotation(Quaternion.of(permutation))), affine, 1.0E-12D);
            }
        }
    }

    @Test
    void pointIntoAndVectorIntoDoNotAllocate() {
        Affine affine = TransformFixtures.general(new SplittableRandom(53L));
        double[] out = new double[3];
        double sink = 0.0D;
        for (int warm = 0; warm < 20_000; warm++) {
            affine.pointInto(warm, 1.0D, 2.0D, out);
            affine.vectorInto(warm, 1.0D, 2.0D, out);
            sink += out[0];
        }
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int call = 0; call < 100_000; call++) {
            affine.pointInto(call, 1.0D, 2.0D, out);
            affine.vectorInto(call, 1.0D, 2.0D, out);
            sink += out[0];
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        assertTrue(allocated < 1_024L, allocated + " bytes allocated by pointInto/vectorInto");
        assertTrue(Double.isFinite(sink));
    }

    private static Frame frame(SplittableRandom random, Face[] faces) {
        Face normal = faces[random.nextInt(faces.length)];
        Face up = faces[random.nextInt(faces.length)];
        while (up.getAxis() == normal.getAxis()) {
            up = faces[random.nextInt(faces.length)];
        }
        return Frame.fromNormalUp(normal, up);
    }
}
