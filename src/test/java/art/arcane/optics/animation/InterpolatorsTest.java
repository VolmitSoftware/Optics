package art.arcane.optics.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import art.arcane.optics.math.Rgba;
import art.arcane.optics.math.Vec2d;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.transform.Affine;
import art.arcane.optics.transform.Quaternion;

final class InterpolatorsTest {
    @Test
    void doublesInterpolateLinearly() {
        Interpolator<Double> doubles = Interpolators.doubles();
        assertEquals(2.0D, doubles.interpolate(2.0D, 6.0D, 0.0D), 0.0D);
        assertEquals(6.0D, doubles.interpolate(2.0D, 6.0D, 1.0D), 0.0D);
        assertEquals(3.0D, doubles.interpolate(2.0D, 6.0D, 0.25D), 0.0D);
        assertEquals(10.0D, doubles.interpolate(2.0D, 6.0D, 2.0D), 0.0D);
    }

    @Test
    void vectorsAndPointsInterpolateComponentWise() {
        assertEquals(new Vec3d(1.0D, 2.0D, 3.0D), Interpolators.vectors().interpolate(Vec3d.ZERO, new Vec3d(2.0D, 4.0D, 6.0D), 0.5D));
        assertEquals(new Vec2d(-1.0D, 3.0D), Interpolators.points().interpolate(new Vec2d(-2.0D, 2.0D), new Vec2d(0.0D, 4.0D), 0.5D));
    }

    @Test
    void rotationsSlerp() {
        Quaternion from = Quaternion.IDENTITY;
        Quaternion to = Quaternion.axisAngleDegrees(Vec3d.UNIT_Y, 120.0D);
        Quaternion middle = Interpolators.rotations().interpolate(from, to, 0.5D);
        assertEquals(Math.toRadians(60.0D), middle.angle(), 1.0E-12D);
        assertEquals(1.0D, middle.axis().y(), 1.0E-12D);
    }

    @Test
    void affinesInterpolateThroughTheDecomposition() {
        Affine from = Affine.translation(0.0D, 0.0D, 0.0D);
        Affine to = Affine.translation(4.0D, 0.0D, 0.0D).compose(Affine.scale(3.0D));
        Affine middle = Interpolators.affines().interpolate(from, to, 0.5D);
        assertEquals(from.lerp(to, 0.5D), middle);
        assertEquals(2.0D, middle.element(0, 3), 1.0E-12D);
        assertEquals(2.0D, middle.element(1, 1), 1.0E-12D);
    }

    @Test
    void colorsInterpolateInRgbOrHsv() {
        Rgba red = new Rgba(1.0F, 0.0F, 0.0F, 1.0F);
        Rgba green = new Rgba(0.0F, 1.0F, 0.0F, 1.0F);
        Rgba straight = Interpolators.colors().interpolate(red, green, 0.5D);
        assertEquals(0.5F, straight.red(), 1.0E-6F);
        assertEquals(0.5F, straight.green(), 1.0E-6F);
        Rgba hsv = Interpolators.colorsHsv().interpolate(red, green, 0.5D);
        assertEquals(60.0F, hsv.hue(), 1.0E-3F);
        assertEquals(1.0F, hsv.value(), 1.0E-6F);
    }

    @Test
    void stepHoldsTheStartUntilTheEnd() {
        Interpolator<String> step = Interpolators.step();
        assertSame("a", step.interpolate("a", "b", 0.0D));
        assertSame("a", step.interpolate("a", "b", 0.999D));
        assertSame("b", step.interpolate("a", "b", 1.0D));
        assertSame("b", step.interpolate("a", "b", 1.5D));
    }
}
