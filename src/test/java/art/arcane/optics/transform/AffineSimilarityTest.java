package art.arcane.optics.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.frame.Similarity;
import art.arcane.optics.math.Face;

final class AffineSimilarityTest {
    @Test
    void pointsAndVectorsMatchTheSimilarity() {
        SplittableRandom random = new SplittableRandom(91L);
        Face[] faces = Face.values();
        double[] expected = new double[3];
        double[] actual = new double[3];
        for (int sample = 0; sample < 2_000; sample++) {
            Similarity similarity = Similarity.between(frame(random, faces), TransformFixtures.vector(random, 1.0E3D), frame(random, faces),
                TransformFixtures.vector(random, 1.0E3D), random.nextDouble(0.0625D, 16.0D));
            Affine affine = Affine.of(similarity);
            double x = random.nextDouble(-1.0E3D, 1.0E3D);
            double y = random.nextDouble(-1.0E3D, 1.0E3D);
            double z = random.nextDouble(-1.0E3D, 1.0E3D);
            similarity.pointInto(x, y, z, expected);
            affine.pointInto(x, y, z, actual);
            assertEquals(expected[0], actual[0], 1.0E-9D);
            assertEquals(expected[1], actual[1], 1.0E-9D);
            assertEquals(expected[2], actual[2], 1.0E-9D);
            similarity.vectorInto(x, y, z, expected);
            affine.vectorInto(x, y, z, actual);
            assertEquals(expected[0], actual[0], 1.0E-9D);
            assertEquals(expected[1], actual[1], 1.0E-9D);
            assertEquals(expected[2], actual[2], 1.0E-9D);
            assertEquals(Math.pow(similarity.scale(), 3.0D) * (similarity.rigid().reflects() ? -1.0D : 1.0D), affine.determinant(),
                1.0E-9D * Math.pow(similarity.scale(), 3.0D));
        }
    }

    @Test
    void unitScaleEqualsTheRigidAffine() {
        SplittableRandom random = new SplittableRandom(92L);
        for (int index = 0; index < 48; index++) {
            OpticTransform rigid = OpticTransform.of(AxisPermutation.ofIndex(index), random.nextDouble(-50.0D, 50.0D),
                random.nextDouble(-50.0D, 50.0D), random.nextDouble(-50.0D, 50.0D));
            assertEquals(Affine.of(rigid), Affine.of(Similarity.of(rigid, 1.0D)));
        }
        assertTrue(Affine.of(Similarity.IDENTITY).isIdentity());
    }

    @Test
    void scaleIsUniformAboutTheTargetAnchor() {
        Similarity similarity = Similarity.of(OpticTransform.IDENTITY, 2.0D);

        Affine affine = Affine.of(similarity);

        assertEquals(Affine.scale(2.0D), affine);
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
