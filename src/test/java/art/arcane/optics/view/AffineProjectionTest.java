package art.arcane.optics.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import art.arcane.optics.math.Vec3d;
import art.arcane.optics.shape.Shapes;
import art.arcane.optics.transform.Affine;
import org.junit.jupiter.api.Test;

final class AffineProjectionTest {
    @Test
    void clipsSourceVolumeThroughAnExactRing() {
        AffineProjection projection = new AffineProjection(new AffineProjection.Options(Shapes.ring(2.0D, 1.0D),
            Affine.IDENTITY, Affine.IDENTITY, new Vec3d(0.0D, 0.0D, 6.0D)));
        assertFalse(projection.contains(0.0D, 0.0D, -6.0D));
        assertTrue(projection.contains(3.0D, 0.0D, -6.0D));
        assertFalse(projection.contains(5.0D, 0.0D, -6.0D));
        assertFalse(projection.contains(1.5D, 0.0D, 3.0D));
        assertFalse(projection.contains(3.0D, 0.0D, 6.0D));
    }

    @Test
    void appliesIndependentDestinationDepthAndShear() {
        AffineProjection projection = new AffineProjection(new AffineProjection.Options(Shapes.rectangle(2.0D, 2.0D),
            Affine.translation(4.0D, 2.0D, 0.0D), Affine.of(1, 0, .5D, 0, 0, 1, 0, 0, 0, 0, 2, 0),
            new Vec3d(4.0D, 2.0D, 6.0D)));
        assertTrue(projection.contains(4.0D, 2.0D, -3.0D));
        assertFalse(projection.contains(8.0D, 2.0D, -3.0D));
        double[] destination = new double[3];
        projection.destinationInto(4.0D, 2.0D, -3.0D, destination);
        assertArrayEquals(new double[] {2.5D, 2.0D, -6.0D}, destination, 1.0E-12D);
    }

    @Test
    void rejectsUndefinedPlaneAndSingularPose() {
        assertThrows(IllegalArgumentException.class, () -> new AffineProjection(new AffineProjection.Options(
            Shapes.FULL, Affine.IDENTITY, Affine.IDENTITY, Vec3d.ZERO)));
        assertThrows(RuntimeException.class, () -> new AffineProjection(new AffineProjection.Options(
            Shapes.FULL, Affine.scale(0.0D), Affine.IDENTITY, new Vec3d(0, 0, 6))));
    }
}
