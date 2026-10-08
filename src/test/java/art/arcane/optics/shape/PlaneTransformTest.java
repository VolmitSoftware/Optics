package art.arcane.optics.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

final class PlaneTransformTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void composeWithInverseIsIdentity() {
        Random random = new Random(41L);
        for (int trial = 0; trial < 500; trial++) {
            PlaneTransform transform = PlaneTransform.translation(random.nextDouble() * 4.0D - 2.0D, random.nextDouble() * 4.0D - 2.0D)
                .compose(PlaneTransform.rotation(random.nextDouble() * 720.0D - 360.0D))
                .compose(PlaneTransform.scale(0.25D + random.nextDouble() * 3.0D, (random.nextBoolean() ? -1.0D : 1.0D)
                    * (0.25D + random.nextDouble() * 3.0D)));
            assertClose(PlaneTransform.IDENTITY, transform.compose(transform.inverse()));
            assertClose(PlaneTransform.IDENTITY, transform.inverse().compose(transform));
        }
        assertThrows(IllegalStateException.class, () -> PlaneTransform.scale(0.0D, 1.0D).inverse());
    }

    @Test
    void composeAppliesTheInnerTransformFirst() {
        PlaneTransform transform = PlaneTransform.translation(1.0D, 0.0D).compose(PlaneTransform.rotation(90.0D));
        double[] out = new double[2];
        transform.pointInto(1.0D, 0.0D, out);
        assertEquals(1.0D, out[0], EPSILON);
        assertEquals(1.0D, out[1], EPSILON);
    }

    @Test
    void quarterTurnsAreExact() {
        assertEquals(new PlaneTransform(0.0D, -1.0D, 1.0D, 0.0D, 0.0D, 0.0D), PlaneTransform.rotation(90.0D));
        assertEquals(new PlaneTransform(-1.0D, 0.0D, 0.0D, -1.0D, 0.0D, 0.0D), PlaneTransform.rotation(-180.0D));
        assertEquals(new PlaneTransform(0.0D, 1.0D, -1.0D, 0.0D, 0.0D, 0.0D), PlaneTransform.rotation(270.0D));
        assertEquals(PlaneTransform.IDENTITY, PlaneTransform.rotation(720.0D));
    }

    @Test
    void mappingFitsOneBoxOntoAnother() {
        PlaneTransform mapping = PlaneTransform.mapping(Bounds2.UNIT, new Bounds2(0.0D, 0.0D, 7.0D, 5.0D));
        double[] out = new double[2];
        mapping.pointInto(-1.0D, -1.0D, out);
        assertEquals(0.0D, out[0], EPSILON);
        assertEquals(0.0D, out[1], EPSILON);
        mapping.pointInto(1.0D, 1.0D, out);
        assertEquals(7.0D, out[0], EPSILON);
        assertEquals(5.0D, out[1], EPSILON);
        assertThrows(IllegalArgumentException.class, () -> PlaneTransform.mapping(Bounds2.EMPTY, Bounds2.UNIT));
    }

    @Test
    void decompositionReportsScaleRotationAndReflection() {
        PlaneTransform transform = PlaneTransform.rotation(30.0D).compose(PlaneTransform.scale(2.0D, 3.0D));
        assertEquals(2.0D, transform.scaleU(), EPSILON);
        assertEquals(3.0D, transform.scaleV(), EPSILON);
        assertEquals(30.0D, transform.rotationDegrees(), EPSILON);
        assertEquals(6.0D, transform.determinant(), EPSILON);
        assertFalse(transform.reflects());
        assertTrue(PlaneTransform.flipU().reflects());
        assertTrue(PlaneTransform.rotation(30.0D).compose(PlaneTransform.scale(2.0D)).isConformal(EPSILON));
        assertTrue(PlaneTransform.flipV().compose(PlaneTransform.rotation(10.0D)).isConformal(EPSILON));
        assertFalse(transform.isConformal(EPSILON));
    }

    @Test
    void lerpInterpolatesTheDecomposition() {
        PlaneTransform from = PlaneTransform.translation(1.0D, 2.0D).compose(PlaneTransform.rotation(10.0D))
            .compose(PlaneTransform.scale(1.0D, 2.0D));
        PlaneTransform to = PlaneTransform.translation(-3.0D, 0.5D).compose(PlaneTransform.rotation(80.0D))
            .compose(PlaneTransform.scale(3.0D, 0.5D));
        assertClose(from, from.lerp(to, 0.0D));
        assertClose(to, from.lerp(to, 1.0D));
        assertClose(PlaneTransform.rotation(45.0D), PlaneTransform.IDENTITY.lerp(PlaneTransform.rotation(90.0D), 0.5D));
        assertClose(PlaneTransform.rotation(175.0D), PlaneTransform.rotation(170.0D).lerp(PlaneTransform.rotation(-180.0D), 0.5D));
        assertClose(PlaneTransform.scale(2.0D), PlaneTransform.scale(1.0D).lerp(PlaneTransform.scale(3.0D), 0.5D));
    }

    @Test
    void nonFiniteComponentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PlaneTransform(Double.NaN, 0.0D, 0.0D, 1.0D, 0.0D, 0.0D));
        assertThrows(IllegalArgumentException.class, () -> PlaneTransform.rotation(Double.POSITIVE_INFINITY));
    }

    private static void assertClose(PlaneTransform expected, PlaneTransform actual) {
        assertEquals(expected.a(), actual.a(), EPSILON, "a");
        assertEquals(expected.b(), actual.b(), EPSILON, "b");
        assertEquals(expected.c(), actual.c(), EPSILON, "c");
        assertEquals(expected.d(), actual.d(), EPSILON, "d");
        assertEquals(expected.tu(), actual.tu(), EPSILON, "tu");
        assertEquals(expected.tv(), actual.tv(), EPSILON, "tv");
    }
}
