package art.arcane.optics.transform;

import static art.arcane.optics.transform.TransformFixtures.assertAffine;
import static art.arcane.optics.transform.TransformFixtures.assertVector;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import art.arcane.optics.math.Axis;
import art.arcane.optics.math.Vec3d;

final class AffineFactoryTest {
    @Test
    void ofStoresRowMajorElementsWithTranslationLast() {
        Affine affine = Affine.of(1.0D, 2.0D, 3.0D, 4.0D, 5.0D, 6.0D, 7.0D, 8.0D, 9.0D, 10.0D, 11.0D, 12.0D);
        double value = 1.0D;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 4; column++) {
                assertEquals(value, affine.element(row, column), 0.0D);
                value += 1.0D;
            }
        }
        assertEquals(new Vec3d(4.0D, 8.0D, 12.0D), affine.translation());
        assertVector(new Vec3d(10.0D, 26.0D, 42.0D), affine.point(new Vec3d(1.0D, 1.0D, 1.0D)), 0.0D);
        assertVector(new Vec3d(6.0D, 18.0D, 30.0D), affine.vector(new Vec3d(1.0D, 1.0D, 1.0D)), 0.0D);
        assertThrows(IndexOutOfBoundsException.class, () -> affine.element(3, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> affine.element(0, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> affine.element(-1, 0));
    }

    @Test
    void arrayExportsUseTheDocumentedLayouts() {
        Affine affine = Affine.of(1.0D, 2.0D, 3.0D, 4.0D, 5.0D, 6.0D, 7.0D, 8.0D, 9.0D, 10.0D, 11.0D, 12.0D);
        double[] rowMajor = new double[12];
        assertSame(rowMajor, affine.rowMajor12(rowMajor));
        assertArrayEquals(new double[] {1.0D, 2.0D, 3.0D, 4.0D, 5.0D, 6.0D, 7.0D, 8.0D, 9.0D, 10.0D, 11.0D, 12.0D}, rowMajor, 0.0D);
        double[] columnMajor = new double[16];
        assertSame(columnMajor, affine.columnMajor16(columnMajor));
        assertArrayEquals(new double[] {1.0D, 5.0D, 9.0D, 0.0D, 2.0D, 6.0D, 10.0D, 0.0D, 3.0D, 7.0D, 11.0D, 0.0D, 4.0D, 8.0D, 12.0D, 1.0D},
            columnMajor, 0.0D);
    }

    @Test
    void translationScaleAndShearFactoriesActAsDocumented() {
        Vec3d point = new Vec3d(2.0D, 3.0D, 5.0D);
        assertVector(new Vec3d(3.0D, 1.0D, 8.0D), Affine.translation(1.0D, -2.0D, 3.0D).point(point), 0.0D);
        assertAffine(Affine.translation(1.0D, -2.0D, 3.0D), Affine.translation(new Vec3d(1.0D, -2.0D, 3.0D)), 0.0D);
        assertVector(point, Affine.translation(1.0D, -2.0D, 3.0D).vector(point), 0.0D);
        assertVector(new Vec3d(4.0D, 6.0D, 10.0D), Affine.scale(2.0D).point(point), 0.0D);
        assertVector(new Vec3d(2.0D, -3.0D, 15.0D), Affine.scale(1.0D, -1.0D, 3.0D).point(point), 0.0D);
        assertAffine(Affine.scale(1.0D, -1.0D, 3.0D), Affine.scale(new Vec3d(1.0D, -1.0D, 3.0D)), 0.0D);
        Affine shear = Affine.shear(1.0D, 2.0D, 3.0D, 4.0D, 5.0D, 6.0D);
        assertVector(new Vec3d(2.0D + 1.0D * 3.0D + 2.0D * 5.0D, 3.0D * 2.0D + 3.0D + 4.0D * 5.0D, 5.0D * 2.0D + 6.0D * 3.0D + 5.0D),
            shear.point(point), 0.0D);
    }

    @Test
    void reflectionsMirrorThroughAxisPlanesAndArbitraryPlanes() {
        Vec3d point = new Vec3d(2.0D, 3.0D, 5.0D);
        assertVector(new Vec3d(-2.0D, 3.0D, 5.0D), Affine.reflection(Axis.X).point(point), 0.0D);
        assertVector(new Vec3d(2.0D, -3.0D, 5.0D), Affine.reflection(Axis.Y).point(point), 0.0D);
        assertVector(new Vec3d(2.0D, 3.0D, -5.0D), Affine.reflection(Axis.Z).point(point), 0.0D);
        assertTrue(Affine.reflection(Axis.X).reflects());
        Affine mirror = Affine.reflection(new Vec3d(0.0D, 2.0D, 0.0D), new Vec3d(7.0D, 10.0D, -1.0D));
        assertVector(new Vec3d(2.0D, 17.0D, 5.0D), mirror.point(point), 1.0E-12D);
        assertTrue(mirror.reflects());
        SplittableRandom random = new SplittableRandom(91L);
        for (int sample = 0; sample < 1_000; sample++) {
            Vec3d normal = TransformFixtures.direction(random);
            Vec3d anchor = TransformFixtures.vector(random, 10.0D);
            Affine reflection = Affine.reflection(normal.multiply(3.0D), anchor);
            Vec3d onPlane = anchor.add(normal.cross(TransformFixtures.direction(random)));
            assertVector(onPlane, reflection.point(onPlane), 1.0E-9D);
            Vec3d off = TransformFixtures.vector(random, 10.0D);
            assertVector(off, reflection.point(reflection.point(off)), 1.0E-9D);
            assertEquals(-off.subtract(anchor).dot(normal), reflection.point(off).subtract(anchor).dot(normal), 1.0E-9D);
            assertTrue(reflection.isRigid(1.0E-12D));
        }
        assertThrows(IllegalArgumentException.class, () -> Affine.reflection(Vec3d.ZERO, Vec3d.ZERO));
    }

    @Test
    void rotationFactoriesAgree() {
        SplittableRandom random = new SplittableRandom(92L);
        for (int sample = 0; sample < 200; sample++) {
            Vec3d axis = TransformFixtures.direction(random);
            double radians = random.nextDouble(-Math.PI, Math.PI);
            Quaternion q = Quaternion.axisAngle(axis, radians);
            assertAffine(Affine.rotation(q), Affine.rotation(axis, radians), 1.0E-12D);
            Quaternion unnormalized = new Quaternion(q.x() * 4.0D, q.y() * 4.0D, q.z() * 4.0D, q.w() * 4.0D);
            assertAffine(Affine.rotation(q), Affine.rotation(unnormalized), 1.0E-12D);
            assertTrue(Affine.rotation(q).isRigid(1.0E-12D));
            assertEquals(1.0D, Affine.rotation(q).determinant(), 1.0E-12D);
        }
        assertThrows(IllegalArgumentException.class, () -> Affine.rotation(new Quaternion(0.0D, 0.0D, 0.0D, 0.0D)));
    }

    @Test
    void trsAppliesScaleThenRotationThenTranslation() {
        Quaternion quarter = Quaternion.axisAngleDegrees(Vec3d.UNIT_Y, 90.0D);
        Affine trs = Affine.trs(new Vec3d(10.0D, 20.0D, 30.0D), quarter, new Vec3d(2.0D, 3.0D, 4.0D));
        assertVector(new Vec3d(10.0D, 20.0D, 28.0D), trs.point(Vec3d.UNIT_X), 1.0E-12D);
        assertVector(new Vec3d(14.0D, 20.0D, 30.0D), trs.point(Vec3d.UNIT_Z), 1.0E-12D);
        assertAffine(Affine.translation(10.0D, 20.0D, 30.0D).compose(Affine.rotation(quarter)).compose(Affine.scale(2.0D, 3.0D, 4.0D)), trs, 1.0E-12D);
    }

    @Test
    void aboutKeepsThePivotFixed() {
        SplittableRandom random = new SplittableRandom(93L);
        for (int sample = 0; sample < 1_000; sample++) {
            Vec3d pivot = TransformFixtures.vector(random, 50.0D);
            Affine local = Affine.rotation(TransformFixtures.rotation(random)).compose(Affine.scale(TransformFixtures.scale(random)));
            Affine about = Affine.about(pivot, local);
            assertVector(pivot, about.point(pivot), 1.0E-9D);
            Vec3d offset = TransformFixtures.vector(random, 5.0D);
            assertVector(pivot.add(local.vector(offset)), about.point(pivot.add(offset)), 1.0E-9D);
        }
    }

    @Test
    void lookAtPlacesTheFrameAtTheEyeFacingTheTarget() {
        SplittableRandom random = new SplittableRandom(94L);
        for (int sample = 0; sample < 1_000; sample++) {
            Vec3d eye = TransformFixtures.vector(random, 20.0D);
            Vec3d target = eye.add(TransformFixtures.vector(random, 20.0D));
            Vec3d up = TransformFixtures.vector(random, 1.0D);
            Affine look = Affine.lookAt(eye, target, up);
            Vec3d forward = target.subtract(eye).normalize();
            assertVector(eye, look.point(Vec3d.ZERO), 1.0E-12D);
            assertVector(forward, look.vector(Vec3d.UNIT_Z.negate()), 1.0E-9D);
            assertVector(up.subtract(forward.multiply(up.dot(forward))).normalize(), look.vector(Vec3d.UNIT_Y), 1.0E-9D);
            assertTrue(look.isRigid(1.0E-12D));
            assertFalse(look.reflects());
        }
        assertAffine(Affine.IDENTITY, Affine.lookAt(Vec3d.ZERO, Vec3d.UNIT_Z.negate(), Vec3d.UNIT_Y), 1.0E-12D);
        assertThrows(IllegalArgumentException.class, () -> Affine.lookAt(Vec3d.UNIT_X, Vec3d.UNIT_X, Vec3d.UNIT_Y));
    }

    @Test
    void predicatesDescribeTheMatrix() {
        assertTrue(Affine.IDENTITY.isIdentity());
        assertFalse(Affine.translation(0.0D, 1.0E-12D, 0.0D).isIdentity());
        assertTrue(Affine.IDENTITY.isRigid(0.0D));
        assertFalse(Affine.scale(1.0D, 1.0D, 1.001D).isRigid(1.0E-6D));
        assertTrue(Affine.scale(1.0D, 1.0D, 1.001D).isRigid(1.0E-2D));
        assertTrue(Affine.reflection(Axis.Y).isRigid(0.0D));
        assertFalse(Affine.shear(0.5D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D).isRigid(1.0E-6D));
        assertTrue(Affine.IDENTITY.isFinite());
        assertFalse(Affine.translation(Double.NaN, 0.0D, 0.0D).isFinite());
        assertFalse(Affine.scale(Double.POSITIVE_INFINITY).isFinite());
        assertEquals(24.0D, Affine.scale(2.0D, 3.0D, 4.0D).determinant(), 0.0D);
        assertEquals(-24.0D, Affine.scale(-2.0D, 3.0D, 4.0D).determinant(), 0.0D);
        assertTrue(Affine.scale(-2.0D, 3.0D, 4.0D).reflects());
        assertFalse(Affine.scale(-2.0D, -3.0D, 4.0D).reflects());
        assertEquals(1.0D, Affine.shear(1.0D, 2.0D, 0.0D, 3.0D, 0.0D, 0.0D).determinant(), 0.0D);
    }

    @Test
    void equalityIsElementWise() {
        Affine a = Affine.of(1.0D, 2.0D, 3.0D, 4.0D, 5.0D, 6.0D, 7.0D, 8.0D, 9.0D, 10.0D, 11.0D, 12.0D);
        Affine b = Affine.of(1.0D, 2.0D, 3.0D, 4.0D, 5.0D, 6.0D, 7.0D, 8.0D, 9.0D, 10.0D, 11.0D, 12.0D);
        Affine c = Affine.of(1.0D, 2.0D, 3.0D, 4.0D, 5.0D, 6.0D, 7.0D, 8.0D, 9.0D, 10.0D, 11.0D, 12.5D);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertEquals(Affine.IDENTITY, Affine.scale(1.0D));
        assertTrue(a.toString().contains("12.0"));
    }
}
