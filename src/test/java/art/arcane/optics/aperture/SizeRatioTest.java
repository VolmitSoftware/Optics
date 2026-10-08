package art.arcane.optics.aperture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.Frame;
import art.arcane.optics.math.Box;
import art.arcane.optics.math.Face;
import art.arcane.optics.shape.ShapeDescriptor;

final class SizeRatioTest {
    @Test
    void threeByThreeIntoNineByNineIsExactlyThree() {
        Frame wall = Frame.canonical(Face.N);
        SizeRatio ratio = SizeRatio.between(wall, 3, 3, Frame.canonical(Face.E), 9, 9);
        assertEquals(3.0D, ratio.ratio(), 0.0D);
        assertTrue(ratio.exact());
        assertFalse(ratio.isUnit());
        SizeRatio back = SizeRatio.between(Frame.canonical(Face.E), 9, 9, wall, 3, 3);
        assertEquals(1.0D / 3.0D, back.ratio(), 0.0D);
        assertEquals(back, ratio.inverse());
    }

    @Test
    void aQuarterTurnSwapsColumnsAndRows() {
        Frame wall = Frame.canonical(Face.N);
        SizeRatio turned = SizeRatio.between(wall, 3, 5, wall.rotateClockwise(), 5, 3);
        assertEquals(1.0D, turned.ratio(), 0.0D);
        assertTrue(turned.exact());
        assertTrue(turned.isUnit());
        SizeRatio straight = SizeRatio.between(wall, 3, 5, wall, 5, 3);
        assertEquals(0.6D, straight.ratio(), 1.0E-12D);
        assertFalse(straight.exact());
    }

    @Test
    void unevenTargetsUseTheSmallerAxisRatio() {
        SizeRatio ratio = SizeRatio.between(Frame.canonical(Face.N), 3, 3, Frame.canonical(Face.N), 9, 6);
        assertEquals(2.0D, ratio.ratio(), 0.0D);
        assertFalse(ratio.exact());
        SizeRatio inverse = ratio.inverse();
        assertEquals(0.5D, inverse.ratio(), 0.0D);
        assertFalse(inverse.exact());
    }

    @Test
    void floorToWallMapsTheFloorAxesOntoTheWallAxes() {
        SizeRatio ratio = SizeRatio.between(Frame.canonical(Face.U), 3, 5, Frame.canonical(Face.N), 6, 10);
        assertEquals(2.0D, ratio.ratio(), 0.0D);
        assertTrue(ratio.exact());
    }

    @Test
    void descriptorsUseTheirCanonicalColumnsAndRows() {
        ApertureDescriptor small = descriptor(Face.N, 0, 3, 5);
        ApertureDescriptor turned = descriptor(Face.N, 1, 15, 9);
        SizeRatio ratio = SizeRatio.between(small, turned);
        assertEquals(3.0D, ratio.ratio(), 0.0D);
        assertTrue(ratio.exact());
        assertEquals(SizeRatio.between(small.frame(), 3, 5, turned.frame(), 15, 9), ratio);
    }

    @Test
    void areasMeasureCellSpansAlongTheFrameAxes() {
        Box small = new Box(10.0D, 12.999D, 64.0D, 66.999D, 20.0D, 20.999D);
        Box large = new Box(100.0D, 100.999D, 64.0D, 72.999D, 200.0D, 208.999D);
        SizeRatio ratio = SizeRatio.between(Frame.canonical(Face.N), small, Frame.canonical(Face.E), large);
        assertEquals(3.0D, ratio.ratio(), 0.0D);
        assertTrue(ratio.exact());
        assertEquals(ratio.inverse(), SizeRatio.between(Frame.canonical(Face.E), large, Frame.canonical(Face.N), small));
    }

    @Test
    void invalidSizesAreRejected() {
        assertTrue(SizeRatio.UNIT.isUnit());
        assertTrue(SizeRatio.UNIT.exact());
        assertThrows(IllegalArgumentException.class, () -> new SizeRatio(0.0D, true));
        assertThrows(IllegalArgumentException.class, () -> new SizeRatio(Double.NaN, true));
        assertThrows(IllegalArgumentException.class, () -> SizeRatio.between(Frame.canonical(Face.N), 0, 3, Frame.canonical(Face.N), 3, 3));
    }

    private static ApertureDescriptor descriptor(Face facing, int frameQuarterTurns, int width, int height) {
        boolean[] open = new boolean[width * height];
        Arrays.fill(open, true);
        return new ApertureDescriptor(0, 64, 0, facing.ordinal(), true, ApertureDescriptor.packQuarterTurns(frameQuarterTurns, 0), false,
            width, height, ApertureDescriptor.apertureMask(width, height, open), ShapeDescriptor.FULL, 0.0F, 0.0F, 1.0F, 64, 0, 0, 0, 0, 0, 0, 0, 0.0D, 0, 0L,
            List.of());
    }
}
