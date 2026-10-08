package art.arcane.optics.transform;

import static art.arcane.optics.transform.TransformFixtures.assertAffine;
import static art.arcane.optics.transform.TransformFixtures.assertVector;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.math.Vec3d;

final class AffineComposeInverseTest {
    @Test
    void composeWithTheInverseIsTheIdentity() {
        SplittableRandom random = new SplittableRandom(41L);
        for (int sample = 0; sample < 1_000; sample++) {
            Affine affine = TransformFixtures.general(random);
            Affine inverse = affine.inverse();
            assertAffine(Affine.IDENTITY, affine.compose(inverse), 1.0E-9D);
            assertAffine(Affine.IDENTITY, inverse.compose(affine), 1.0E-9D);
            Vec3d point = TransformFixtures.vector(random, 20.0D);
            assertVector(point, inverse.point(affine.point(point)), 1.0E-9D);
        }
    }

    @Test
    void singularMatricesRefuseToInvert() {
        assertThrows(IllegalStateException.class, () -> Affine.scale(0.0D).inverse());
        assertThrows(IllegalStateException.class, () -> Affine.scale(1.0D, 1.0E-13D, 1.0D).inverse());
        assertThrows(IllegalStateException.class, () -> Affine.of(1.0D, 2.0D, 3.0D, 0.0D, 2.0D, 4.0D, 6.0D, 0.0D, 0.0D, 0.0D, 1.0D, 0.0D).inverse());
        assertAffine(Affine.scale(1.0D, 1.0E6D, 1.0D), Affine.scale(1.0D, 1.0E-6D, 1.0D).inverse(), 1.0E-6D);
    }

    @Test
    void composeAppliesTheInnerTransformFirst() {
        SplittableRandom random = new SplittableRandom(42L);
        for (int sample = 0; sample < 1_000; sample++) {
            Affine outer = TransformFixtures.general(random);
            Affine inner = TransformFixtures.general(random);
            Vec3d point = TransformFixtures.vector(random, 20.0D);
            assertVector(outer.point(inner.point(point)), outer.compose(inner).point(point), 1.0E-9D);
            assertVector(outer.point(inner.point(point)), inner.then(outer).point(point), 1.0E-9D);
            Vec3d vector = TransformFixtures.vector(random, 5.0D);
            assertVector(outer.vector(inner.vector(vector)), outer.compose(inner).vector(vector), 1.0E-9D);
        }
        Affine move = Affine.translation(10.0D, 0.0D, 0.0D);
        Affine grow = Affine.scale(2.0D);
        assertVector(new Vec3d(12.0D, 0.0D, 0.0D), move.compose(grow).point(Vec3d.UNIT_X), 0.0D);
        assertVector(new Vec3d(22.0D, 0.0D, 0.0D), move.then(grow).point(Vec3d.UNIT_X), 0.0D);
    }

    @Test
    void compositionIsAssociative() {
        SplittableRandom random = new SplittableRandom(43L);
        for (int sample = 0; sample < 1_000; sample++) {
            Affine a = TransformFixtures.general(random);
            Affine b = TransformFixtures.general(random);
            Affine c = TransformFixtures.general(random);
            assertAffine(a.compose(b).compose(c), a.compose(b.compose(c)), 1.0E-9D);
        }
    }

    @Test
    void composeMatchesOpticTransformComposition() {
        SplittableRandom random = new SplittableRandom(44L);
        for (int sample = 0; sample < 1_000; sample++) {
            OpticTransform outer = OpticTransform.of(AxisPermutation.ofIndex(random.nextInt(48)), random.nextDouble(-100.0D, 100.0D),
                random.nextDouble(-100.0D, 100.0D), random.nextDouble(-100.0D, 100.0D));
            OpticTransform inner = OpticTransform.of(AxisPermutation.ofIndex(random.nextInt(48)), random.nextDouble(-100.0D, 100.0D),
                random.nextDouble(-100.0D, 100.0D), random.nextDouble(-100.0D, 100.0D));
            assertAffine(Affine.of(outer.compose(inner)), Affine.of(outer).compose(Affine.of(inner)), 1.0E-9D);
            assertAffine(Affine.of(outer.inverse()), Affine.of(outer).inverse(), 1.0E-9D);
        }
    }
}
