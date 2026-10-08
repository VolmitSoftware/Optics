package art.arcane.optics.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class Bounds2Test {
    @Test
    void ofSpansEveryPoint() {
        Bounds2 bounds = Bounds2.of(new double[] {1.0D, -2.0D, -3.0D, 4.0D, 0.5D, 0.5D});
        assertEquals(new Bounds2(-3.0D, -2.0D, 1.0D, 4.0D), bounds);
        assertEquals(4.0D, bounds.width());
        assertEquals(6.0D, bounds.height());
        assertEquals(-1.0D, bounds.centerU());
        assertEquals(1.0D, bounds.centerV());
        assertTrue(bounds.contains(0.0D, 0.0D));
        assertTrue(bounds.contains(1.0D, 4.0D));
        assertFalse(bounds.contains(1.1D, 0.0D));
        assertSame(Bounds2.EMPTY, Bounds2.of(new double[0]));
    }

    @Test
    void emptyBoundsAreNeutralForUnionAndAbsorbingForIntersection() {
        assertTrue(Bounds2.EMPTY.isEmpty());
        assertEquals(0.0D, Bounds2.EMPTY.width());
        assertEquals(Bounds2.UNIT, Bounds2.EMPTY.union(Bounds2.UNIT));
        assertEquals(Bounds2.UNIT, Bounds2.UNIT.union(Bounds2.EMPTY));
        assertTrue(Bounds2.UNIT.intersect(Bounds2.EMPTY).isEmpty());
        assertTrue(Bounds2.UNIT.intersect(new Bounds2(2.0D, 2.0D, 3.0D, 3.0D)).isEmpty());
        assertEquals(new Bounds2(0.0D, 0.0D, 1.0D, 1.0D), Bounds2.UNIT.intersect(new Bounds2(0.0D, 0.0D, 3.0D, 3.0D)));
        assertTrue(Bounds2.EMPTY.grown(1.0D).isEmpty());
        assertEquals(new Bounds2(-2.0D, -2.0D, 2.0D, 2.0D), Bounds2.UNIT.grown(1.0D));
    }

    @Test
    void transformedBoundsCoverTheMappedCorners() {
        Bounds2 rotated = new Bounds2(0.0D, 0.0D, 2.0D, 1.0D).transformed(PlaneTransform.rotation(90.0D));
        assertEquals(new Bounds2(-1.0D, 0.0D, 0.0D, 2.0D), rotated);
        assertTrue(Bounds2.EMPTY.transformed(PlaneTransform.rotation(30.0D)).isEmpty());
    }
}
