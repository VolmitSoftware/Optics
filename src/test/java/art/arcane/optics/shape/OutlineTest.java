package art.arcane.optics.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class OutlineTest {
    private static final double EPSILON = 1.0E-12D;

    @Test
    void areaSignFollowsTheWinding() {
        Outline square = Outline.of(-1.0D, -1.0D, 1.0D, -1.0D, 1.0D, 1.0D, -1.0D, 1.0D);
        assertEquals(4.0D, square.signedArea(), EPSILON);
        assertFalse(square.clockwise());
        Outline reversed = square.reversed();
        assertEquals(-4.0D, reversed.signedArea(), EPSILON);
        assertTrue(reversed.clockwise());
        assertEquals(8.0D, square.length(), EPSILON);
        assertEquals(new Bounds2(-1.0D, -1.0D, 1.0D, 1.0D), square.bounds());
        assertEquals(4, square.size());
        assertEquals(1.0D, square.u(1));
        assertEquals(-1.0D, square.v(1));
    }

    @Test
    void containmentIsEvenOdd() {
        Outline square = Outline.of(-1.0D, -1.0D, 1.0D, -1.0D, 1.0D, 1.0D, -1.0D, 1.0D);
        assertTrue(square.contains(0.0D, 0.0D));
        assertFalse(square.contains(1.5D, 0.0D));
        Outline bowTie = Outline.of(-1.0D, -1.0D, 1.0D, 1.0D, 1.0D, -1.0D, -1.0D, 1.0D);
        assertTrue(bowTie.contains(0.8D, 0.0D));
        assertTrue(bowTie.contains(-0.8D, 0.0D));
        assertFalse(bowTie.contains(0.0D, 0.8D));
    }

    @Test
    void distanceIsToTheNearestEdge() {
        Outline square = Outline.of(-1.0D, -1.0D, 1.0D, -1.0D, 1.0D, 1.0D, -1.0D, 1.0D);
        assertEquals(1.0D, square.distance(0.0D, 0.0D), EPSILON);
        assertEquals(Math.sqrt(2.0D), square.distance(2.0D, 2.0D), EPSILON);
        assertEquals(0.5D, square.distance(0.0D, 1.5D), EPSILON);
    }

    @Test
    void samplingWalksTheLoopAtTheRequestedSpacing() {
        Outline square = Outline.of(-1.0D, -1.0D, 1.0D, -1.0D, 1.0D, 1.0D, -1.0D, 1.0D);
        double[] out = new double[2 * 16 + 2];
        int count = square.sample(0.5D, out);
        assertEquals(16, count);
        assertEquals(-1.0D, out[0], EPSILON);
        assertEquals(-1.0D, out[1], EPSILON);
        assertEquals(-0.5D, out[2], EPSILON);
        assertEquals(-1.0D, out[3], EPSILON);
        for (int index = 0; index < count; index++) {
            assertEquals(0.0D, square.distance(out[index << 1], out[(index << 1) + 1]), 1.0E-9D);
        }
        assertEquals(6, square.sample(1.5D, new double[12]));
        assertThrows(IllegalArgumentException.class, () -> square.sample(0.5D, new double[4]));
        assertThrows(IllegalArgumentException.class, () -> square.sample(0.0D, out));
    }

    @Test
    void transformedMapsEveryPoint() {
        Outline triangle = Outline.of(0.0D, 0.0D, 1.0D, 0.0D, 0.0D, 1.0D);
        Outline moved = triangle.transformed(PlaneTransform.translation(2.0D, 3.0D).compose(PlaneTransform.scale(2.0D)));
        assertEquals(2.0D, moved.u(0), EPSILON);
        assertEquals(3.0D, moved.v(0), EPSILON);
        assertEquals(4.0D, moved.u(1), EPSILON);
        assertEquals(5.0D, moved.v(2), EPSILON);
        assertEquals(2.0D, moved.signedArea(), EPSILON);
    }

    @Test
    void invalidLoopsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Outline.of(0.0D, 0.0D, 1.0D, 1.0D));
        assertThrows(IllegalArgumentException.class, () -> Outline.of(0.0D, 0.0D, 1.0D, 1.0D, 2.0D));
        assertThrows(IllegalArgumentException.class, () -> Outline.of(0.0D, 0.0D, 1.0D, Double.NaN, 2.0D, 0.0D));
    }
}
