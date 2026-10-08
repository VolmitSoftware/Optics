package art.arcane.optics.transform;

import static art.arcane.optics.transform.TransformFixtures.assertAffine;
import static art.arcane.optics.transform.TransformFixtures.assertSameRotation;
import static art.arcane.optics.transform.TransformFixtures.assertVector;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import art.arcane.optics.math.Axis;
import art.arcane.optics.math.Vec3d;

final class AffineDecompositionTest {
    @Test
    void recomposingReproducesTheOriginalMatrix() {
        SplittableRandom random = new SplittableRandom(61L);
        for (int sample = 0; sample < 1_000; sample++) {
            Affine affine = TransformFixtures.general(random);
            Affine.Decomposition parts = affine.decompose();
            assertTrue(parts.rotation().isNormalized(1.0E-12D));
            assertTrue(parts.scale().x() > 0.0D && parts.scale().y() > 0.0D && parts.scale().z() > 0.0D, parts.toString());
            assertAffine(affine, parts.affine(), 1.0E-9D);
        }
    }

    @Test
    void reflectingMatricesYieldANegativeScaleX() {
        SplittableRandom random = new SplittableRandom(62L);
        for (int sample = 0; sample < 1_000; sample++) {
            Affine affine = TransformFixtures.general(random).compose(Affine.reflection(Axis.values()[random.nextInt(3)]));
            assertTrue(affine.reflects());
            Affine.Decomposition parts = affine.decompose();
            assertTrue(parts.scale().x() < 0.0D, parts.toString());
            assertTrue(parts.scale().y() > 0.0D && parts.scale().z() > 0.0D, parts.toString());
            assertTrue(parts.rotation().isNormalized(1.0E-12D));
            assertAffine(affine, parts.affine(), 1.0E-9D);
        }
        Affine.Decomposition mirror = Affine.reflection(Axis.X).decompose();
        assertVector(new Vec3d(-1.0D, 1.0D, 1.0D), mirror.scale(), 1.0E-12D);
        assertSameRotation(Quaternion.IDENTITY, mirror.rotation(), 1.0E-12D);
    }

    @Test
    void trsMatricesDecomposeIntoTheirParts() {
        SplittableRandom random = new SplittableRandom(63L);
        for (int sample = 0; sample < 1_000; sample++) {
            Vec3d translation = TransformFixtures.vector(random, 100.0D);
            Quaternion rotation = TransformFixtures.rotation(random);
            Vec3d scale = TransformFixtures.scale(random);
            Affine.Decomposition parts = Affine.trs(translation, rotation, scale).decompose();
            assertVector(translation, parts.translation(), 1.0E-12D);
            assertSameRotation(rotation, parts.rotation(), 1.0E-9D);
            assertVector(scale, parts.scale(), 1.0E-9D);
            assertVector(Vec3d.ZERO, parts.shear(), 1.0E-9D);
        }
    }

    @Test
    void shearIsRecoveredAsTheUpperTriangle() {
        Affine.Decomposition parts = Affine.shear(0.25D, -0.5D, 0.0D, 0.75D, 0.0D, 0.0D).decompose();
        assertVector(new Vec3d(0.25D, -0.5D, 0.75D), parts.shear(), 1.0E-12D);
        assertVector(new Vec3d(1.0D, 1.0D, 1.0D), parts.scale(), 1.0E-12D);
        assertSameRotation(Quaternion.IDENTITY, parts.rotation(), 1.0E-12D);
        Affine.Decomposition rebuilt = new Affine.Decomposition(new Vec3d(1.0D, 2.0D, 3.0D), Quaternion.axisAngleDegrees(Vec3d.UNIT_Z, 30.0D),
            new Vec3d(2.0D, 3.0D, 4.0D), new Vec3d(0.1D, 0.2D, 0.3D));
        Affine.Decomposition again = rebuilt.affine().decompose();
        assertVector(rebuilt.translation(), again.translation(), 1.0E-12D);
        assertSameRotation(rebuilt.rotation(), again.rotation(), 1.0E-12D);
        assertVector(rebuilt.scale(), again.scale(), 1.0E-12D);
        assertVector(rebuilt.shear(), again.shear(), 1.0E-12D);
    }

    @Test
    void singularMatricesDecomposeWithoutNan() {
        Affine[] singular = {Affine.scale(0.0D), Affine.scale(0.0D, 1.0D, 1.0D), Affine.scale(1.0D, 0.0D, 2.0D),
            Affine.rotation(Vec3d.UNIT_Y, 0.7D).compose(Affine.scale(1.0D, 1.0D, 0.0D)),
            Affine.rotation(new Vec3d(1.0D, 2.0D, 3.0D), 1.1D).compose(Affine.scale(0.0D, 2.0D, 3.0D)),
            Affine.of(1.0D, 1.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.0D, 0.0D, 0.0D, 0.0D, 1.0D, 0.0D)};
        for (Affine affine : singular) {
            Affine.Decomposition parts = affine.decompose();
            assertTrue(parts.translation().isFinite() && parts.scale().isFinite() && parts.shear().isFinite() && parts.rotation().isFinite(),
                parts.toString());
            assertTrue(parts.rotation().isNormalized(1.0E-12D));
        }
        for (int index = 0; index < 5; index++) {
            assertAffine(singular[index], singular[index].decompose().affine(), 1.0E-12D);
        }
        assertEquals(0.0D, Affine.scale(0.0D).decompose().scale().length(), 0.0D);
    }
}
