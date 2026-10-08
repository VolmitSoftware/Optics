package art.arcane.optics.aperture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import art.arcane.optics.frame.Frame;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.shape.FitMode;
import art.arcane.optics.shape.ShapeDescriptor;
import art.arcane.optics.shape.Shapes;
import org.junit.jupiter.api.Test;

final class ApertureDescriptorShapeTest {
    private static final ShapeDescriptor CIRCLE = ShapeDescriptor.of(Shapes.circle(1.0D), FitMode.CONTAIN);

    @Test
    void containsPointRejectsCornersOutsideTheShapeOnEveryFacing() {
        for (Frame frame : ShapedApertures.frames()) {
            ApertureDescriptor geometry = ShapedApertures.descriptor(frame, 7, 7, CIRCLE);
            AperturePolygon polygon = AperturePolygon.from(geometry);
            assertEquals(49, geometry.openCellCount());
            assertFalse(contains(geometry, polygon.point(0.3D, 0.3D)), frame.toString());
            assertFalse(contains(geometry, polygon.point(6.7D, 0.2D)), frame.toString());
            assertTrue(contains(geometry, polygon.point(3.5D, 3.5D)), frame.toString());
            assertTrue(contains(geometry, polygon.point(0.2D, 3.5D)), frame.toString());
            assertFalse(contains(geometry, polygon.point(7.2D, 3.5D)), frame.toString());
            ApertureDescriptor full = geometry.withShape(ShapeDescriptor.FULL);
            for (int row = 0; row < 7; row++) {
                for (int column = 0; column < 7; column++) {
                    assertTrue(contains(full, polygon.point(column + 0.1D, row + 0.9D)));
                }
            }
        }
    }

    @Test
    void containsPointNeedsAnOpenCellOnThePlane() {
        ApertureDescriptor geometry = ShapedApertures.descriptor(Frame.canonical(Face.S), 7, 7, CIRCLE);
        long[] mask = geometry.apertureMask();
        int bit = 3 * 7 + 3;
        mask[bit >>> 6] &= ~(1L << (bit & 63));
        ApertureDescriptor holed = new ApertureDescriptor(geometry.originX(), geometry.originY(), geometry.originZ(), geometry.facing(),
            geometry.frontSide(), geometry.quarterTurns(), geometry.mirror(), geometry.apertureWidth(), geometry.apertureHeight(), mask,
            geometry.shape(), geometry.nearPlanePadding(), geometry.aperturePadding(), geometry.frustumCullingRatio(), geometry.depthBlocks(),
            geometry.recursionDepth(), geometry.blackoutPolicy(), geometry.blackoutState(), geometry.maskAirPolicy(),
            geometry.lightingPolicy(), geometry.fidelityFlags(), geometry.kind(), geometry.planeOffset(), geometry.parentPortalKey(),
            geometry.targetIdentity(), geometry.nested());
        AperturePolygon polygon = AperturePolygon.from(holed);
        assertFalse(contains(holed, polygon.point(3.5D, 3.5D)));
        assertTrue(contains(holed, polygon.point(2.5D, 3.5D)));
        Vec3d off = polygon.point(2.5D, 3.5D).add(new Vec3d(0.0D, 0.0D, 1.0D));
        assertFalse(holed.containsPoint(off.x(), off.y(), off.z()));
    }

    @Test
    void cellCoordinatesInvertThePolygonPoint() {
        for (Frame frame : ShapedApertures.frames()) {
            ApertureDescriptor geometry = ShapedApertures.descriptor(frame, 5, 3, CIRCLE);
            Vec3d point = AperturePolygon.from(geometry).point(2.25D, 1.75D);
            double[] out = new double[2];
            geometry.cellCoordinates(point.x(), point.y(), point.z(), out);
            assertEquals(2.25D, out[0], 1.0E-12D, frame.toString());
            assertEquals(1.75D, out[1], 1.0E-12D, frame.toString());
        }
    }

    @Test
    void shapeIsPartOfTheSurfaceIdentity() {
        ApertureDescriptor circle = ShapedApertures.descriptor(Frame.canonical(Face.N), 7, 5, CIRCLE);
        ApertureDescriptor full = circle.withShape(ShapeDescriptor.FULL);
        assertSame(CIRCLE, circle.shape());
        assertTrue(circle.valid());
        assertFalse(circle.sameSurface(full));
        assertNotEquals(circle, full);
        assertNotEquals(circle.hashCode(), full.hashCode());
        assertEquals(circle, full.withShape(ShapeDescriptor.decode(CIRCLE.encode())));
        assertTrue(circle.toString().contains("circle(radius=1)"));
        assertSame(CIRCLE, circle.withParent(4).shape());
        assertSame(CIRCLE, circle.withDepth(12).shape());
        assertSame(CIRCLE, circle.withNested(List.of(full)).shape());
        assertEquals(circle.openCellCount(), full.openCellCount());
    }

    @Test
    void sourcesWithoutAShapeAreFull() {
        ApertureDescriptor geometry = ApertureDescriptor.fromPortal(ShapedApertures.source(ShapedApertures.flat(Face.E, 3, 3),
            Frame.canonical(Face.E), null, 0.0D)).orElseThrow();
        assertSame(ShapeDescriptor.FULL, geometry.shape());
        ApertureDescriptor direct = new ApertureDescriptor(1, 2, 3, Face.E.ordinal(), true, 0, false, 1, 1, new long[] {1L}, null, 0.0F,
            0.0F, 1.0F, 8, 0, 0, 0, 0, 0, 0, 0, 0.0D, 0, 0L, List.of());
        assertSame(ShapeDescriptor.FULL, direct.shape());
        assertTrue(direct.planeShape().isFull());
    }

    @Test
    void planeShapeFollowsTheFitOnTheBoundingRectangle() {
        ApertureDescriptor geometry = ShapedApertures.descriptor(Frame.canonical(Face.S), 9, 5, CIRCLE.withFit(FitMode.STRETCH));
        assertEquals(9, geometry.planeShape().columns());
        assertEquals(5, geometry.planeShape().rows());
        assertTrue(geometry.planeShape().contains(0.6D, 2.5D));
        assertFalse(geometry.planeShape().contains(0.3D, 0.3D));
    }

    private static boolean contains(ApertureDescriptor geometry, Vec3d point) {
        return geometry.containsPoint(point.x(), point.y(), point.z());
    }
}
