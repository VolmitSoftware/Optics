package art.arcane.optics.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TransformedShapeTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void queriesMapThroughTheInverse() {
        Shape bar = new Rectangle(1.6D, 0.2D).rotated(90.0D);
        assertTrue(bar.contains(0.0D, 0.7D));
        assertFalse(bar.contains(0.7D, 0.0D));
        Shape moved = Ellipse.circle(0.25D).translated(0.5D, -0.5D);
        assertTrue(moved.contains(0.5D, -0.5D));
        assertFalse(moved.contains(0.0D, 0.0D));
    }

    @Test
    void distancesScaleByTheMeanScale() {
        Shape scaled = Ellipse.circle(0.5D).scaled(2.0D);
        Shape reference = Ellipse.circle(1.0D);
        for (double u = -1.5D; u <= 1.5D; u += 0.25D) {
            assertEquals(reference.signedDistance(u, 0.3D), scaled.signedDistance(u, 0.3D), EPSILON);
        }
        assertEquals(new Bounds2(-1.0D, -1.0D, 1.0D, 1.0D), scaled.bounds());
    }

    @Test
    void transformsFoldIntoOneNode() {
        Shape folded = new Rectangle(1.0D, 0.5D).rotated(30.0D).rotated(60.0D).translated(0.1D, 0.0D);
        Transformed node = assertInstanceOf(Transformed.class, folded);
        assertInstanceOf(Rectangle.class, node.shape());
        assertEquals(0.0D, node.transform().a(), EPSILON);
        assertEquals(1.0D, node.transform().c(), EPSILON);
        assertEquals(0.1D, node.transform().tu(), EPSILON);
    }

    @Test
    void reflectingTransformsKeepOuterLoopsCounterClockwise() {
        Shape heart = new Heart(1.0D, 0.0D).flippedV();
        assertFalse(heart.outlines(0.01D).getFirst().clockwise());
        assertTrue(heart.contains(0.0D, 0.85D));
        Shape ring = new Ring(1.0D, 0.5D).flippedU();
        assertFalse(ring.outlines(0.01D).get(0).clockwise());
        assertTrue(ring.outlines(0.01D).get(1).clockwise());
    }

    @Test
    void outlinesFollowTheTransform() {
        Shape shape = new Rectangle(1.0D, 1.0D).scaled(0.5D, 1.0D).translated(0.25D, 0.0D);
        Outline outline = shape.outlines(0.01D).getFirst();
        assertEquals(new Bounds2(0.0D, -0.5D, 0.5D, 0.5D), outline.bounds());
    }

    @Test
    void singularTransformsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Transformed(Ellipse.circle(1.0D), PlaneTransform.scale(0.0D, 1.0D)));
    }
}
