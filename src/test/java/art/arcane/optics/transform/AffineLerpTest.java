package art.arcane.optics.transform;

import static art.arcane.optics.transform.TransformFixtures.assertAffine;
import static art.arcane.optics.transform.TransformFixtures.assertVector;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.math.Vec3d;

final class AffineLerpTest {
    @Test
    void lerpHitsBothEndpoints() {
        SplittableRandom random = new SplittableRandom(71L);
        for (int sample = 0; sample < 1_000; sample++) {
            Affine from = TransformFixtures.general(random);
            Affine to = TransformFixtures.general(random);
            assertAffine(from, from.lerp(to, 0.0D), 1.0E-9D);
            assertAffine(to, from.lerp(to, 1.0D), 1.0E-9D);
            assertAffine(from, from.decompose().lerp(to.decompose(), 0.0D).affine(), 1.0E-9D);
            assertAffine(to, from.decompose().lerp(to.decompose(), 1.0D).affine(), 1.0E-9D);
        }
        Affine a = Affine.translation(1.0D, 2.0D, 3.0D);
        Affine b = Affine.scale(4.0D);
        assertSame(a, a.lerp(b, 0.0D));
        assertSame(b, a.lerp(b, 1.0D));
    }

    @Test
    void rigidToRigidLerpStaysRigid() {
        SplittableRandom random = new SplittableRandom(72L);
        for (int sample = 0; sample < 1_000; sample++) {
            Affine from = Affine.trs(TransformFixtures.vector(random, 50.0D), TransformFixtures.rotation(random), new Vec3d(1.0D, 1.0D, 1.0D));
            Affine to = Affine.trs(TransformFixtures.vector(random, 50.0D), TransformFixtures.rotation(random), new Vec3d(1.0D, 1.0D, 1.0D));
            Affine between = from.lerp(to, random.nextDouble());
            assertTrue(between.isRigid(1.0E-9D), between.toString());
            assertTrue(!between.reflects());
        }
        for (int sample = 0; sample < 200; sample++) {
            Affine from = Affine.of(OpticTransform.of(AxisPermutation.ofIndex(random.nextInt(48)), 1.0D, 2.0D, 3.0D));
            Affine to = Affine.of(OpticTransform.of(AxisPermutation.ofIndex(random.nextInt(48)), -4.0D, 5.0D, 6.0D));
            if (from.reflects() != to.reflects()) {
                continue;
            }
            Affine between = from.lerp(to, random.nextDouble());
            assertTrue(between.isRigid(1.0E-9D), between.toString());
        }
    }

    @Test
    void lerpInterpolatesEachComponent() {
        assertAffine(Affine.translation(5.0D, -5.0D, 10.0D), Affine.translation(0.0D, 0.0D, 0.0D).lerp(Affine.translation(10.0D, -10.0D, 20.0D), 0.5D),
            1.0E-12D);
        assertAffine(Affine.scale(0.5D), Affine.scale(0.0D).lerp(Affine.IDENTITY, 0.5D), 1.0E-12D);
        assertAffine(Affine.scale(1.5D), Affine.IDENTITY.lerp(Affine.scale(2.0D), 0.5D), 1.0E-12D);
        Affine quarter = Affine.rotation(Vec3d.UNIT_Y, Math.PI / 2.0D);
        assertAffine(Affine.rotation(Vec3d.UNIT_Y, Math.PI / 4.0D), Affine.IDENTITY.lerp(quarter, 0.5D), 1.0E-12D);
        Affine spin = Affine.about(new Vec3d(4.0D, 0.0D, 0.0D), quarter);
        assertVector(spin.translation().multiply(0.3D), Affine.IDENTITY.lerp(spin, 0.3D).translation(), 1.0E-12D);
    }
}
