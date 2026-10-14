package art.arcane.optics.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.portal.PortalDefinition;
import org.junit.jupiter.api.Test;

final class PlaneShapeCertificationTest {
    @Test
    void sampledFullAnisotropicCellKeepsItsExactBoundary() {
        PlaneShape plane = PlaneShape.fit(strip(), FitMode.STRETCH, 20, 20);
        assertFalse(plane.contains(10.0D, 9.01D));
        assertTrue(plane.contains(10.0D, 9.5D));
        ShapeRaster raster = plane.raster(4);
        assertTrue(raster.inside(10, 9));
        assertEquals(ShapeRaster.Coverage.PARTIAL, raster.coverage(10, 9));
        assertFalse(plane.contains(10.0D, 9.01D));
        assertTrue(plane.contains(10.0D, 9.5D));
    }

    @Test
    void unsampledThinFeatureRemainsAnalyticallyContained() {
        PlaneShape plane = PlaneShape.fit(new Rectangle(2.0D, 2.0D).scaled(1.0D, 0.01D), FitMode.STRETCH, 20, 20);
        assertTrue(plane.contains(10.0D, 9.99D));
        ShapeRaster raster = plane.raster(4);
        assertEquals(0.0D, raster.coverageFraction(10, 9), 0.0D);
        assertEquals(ShapeRaster.Coverage.PARTIAL, raster.coverage(10, 9));
        assertTrue(plane.contains(10.0D, 9.99D));
    }

    @Test
    void nestedTransformsAndBooleanShapesKeepRasterQueriesAnalytic() {
        Shape nested = new Transformed(new Transformed(new Rectangle(2.0D, 2.0D), PlaneTransform.scale(1.0D, 0.2D)),
            PlaneTransform.scale(1.0D, 0.2D)).translated(0.0D, -0.05D);
        List<Shape> shapes = List.of(strip(), nested, strip().union(Ellipse.circle(0.2D).translated(0.5D, 0.5D)),
            strip().intersect(new Rectangle(1.5D, 1.8D)), new Rectangle(2.0D, 2.0D).subtract(strip()),
            strip().transformed(new PlaneTransform(1.0D, 0.7D, 0.0D, 1.0D, 0.0D, 0.0D)),
            new Ellipse(1.0D, 0.04D).translated(0.0D, -0.05D), new Heart(1.0D, 0.0D).scaled(1.0D, 0.1D),
            new Flower(5, 1.0D, 0.7D, 15.0D).scaled(1.0D, 0.1D));
        double[] unit = new double[2];
        for (Shape shape : shapes) {
            for (int subsamples : new int[] {1, 4, 8}) {
                PlaneShape plane = PlaneShape.fit(shape, FitMode.STRETCH, 20, 20, PlaneTransform.rotation(90.0D));
                plane.raster(subsamples);
                for (int row = 0; row < 100; row++) {
                    for (int column = 0; column < 100; column++) {
                        double u = (column + 0.05D) * 0.2D;
                        double v = (row + 0.05D) * 0.2D;
                        plane.unitCoordinates(u, v, unit);
                        boolean expected = shape.contains(unit[0], unit[1]);
                        assertEquals(expected, plane.contains(u, v), () -> shape + " at " + u + "," + v);
                    }
                }
            }
        }
    }

    @Test
    void conservativeCertificationKeepsThePublicDistanceField() {
        Shape shape = strip();
        assertEquals(-0.2D, shape.signedDistance(0.0D, -0.05D), 1.0E-12D);
        assertEquals(-0.04D, ShapeMath.distanceBound(shape).applyAsDouble(0.0D, -0.05D), 1.0E-12D);
        Shape nested = new Transformed(new Transformed(new Rectangle(2.0D, 2.0D), PlaneTransform.scale(1.0D, 0.2D)),
            PlaneTransform.scale(1.0D, 0.2D));
        assertEquals(-0.04D, ShapeMath.distanceBound(nested).applyAsDouble(0.0D, 0.0D), 1.0E-12D);
        Ellipse ellipse = new Ellipse(1.0D, 0.04D);
        assertEquals(-0.038D, ShapeMath.distanceBound(ellipse).applyAsDouble(0.05D, 0.0D), 1.0E-12D);
        assertTrue(ellipse.signedDistance(0.05D, 0.0D) < -1.0D);
    }

    @Test
    void portalCrossingsRespectTheContinuousShapeWithinAnOpenCell() {
        PortalDefinition portal = PortalDefinition.builder().facing(Face.N).origin(0, 0, 0).size(20, 20)
            .shape(ShapeDescriptor.of(strip(), FitMode.STRETCH)).build();
        assertTrue(portal.containsCell(10, 9, 0));
        Vec3d closed = new Vec3d(10.0D, 9.01D, 0.5D);
        Vec3d open = new Vec3d(10.0D, 9.5D, 0.5D);
        Vec3d velocity = new Vec3d(0.0D, 0.0D, -1.0D);
        assertFalse(portal.containsPoint(closed, 0.001D));
        assertTrue(portal.containsPoint(open, 0.001D));
        assertNull(portal.crossing(new Vec3d(10.0D, 9.01D, 1.0D), new Vec3d(10.0D, 9.01D, 0.0D), velocity, velocity));
        assertNotNull(portal.crossing(new Vec3d(10.0D, 9.5D, 1.0D), new Vec3d(10.0D, 9.5D, 0.0D), velocity, velocity));
    }

    @Test
    void aPlayerWidthOpeningRejectsTheClosedEdgeOfItsRasterCell() {
        Shape shape = new Rectangle(2.0D, 2.0D).scaled(0.8D / 7.0D, 1.0D);
        PortalDefinition portal = PortalDefinition.builder().facing(Face.N).origin(0, 0, 0).size(7, 7)
            .shape(ShapeDescriptor.of(shape, FitMode.STRETCH)).build();
        assertTrue(portal.containsCell(3, 3, 0));
        assertTrue(portal.containsPoint(new Vec3d(3.5D, 3.5D, 0.5D), 0.001D));
        assertFalse(portal.containsPoint(new Vec3d(3.01D, 3.5D, 0.5D), 0.001D));
        Vec3d velocity = new Vec3d(0.0D, 0.0D, -1.0D);
        assertNotNull(portal.crossing(new Vec3d(3.5D, 3.5D, 1.0D), new Vec3d(3.5D, 3.5D, 0.0D), velocity, velocity));
        assertNull(portal.crossing(new Vec3d(3.01D, 3.5D, 1.0D), new Vec3d(3.01D, 3.5D, 0.0D), velocity, velocity));
    }

    private static Shape strip() {
        return new Rectangle(2.0D, 2.0D).scaled(1.0D, 0.04D).translated(0.0D, -0.05D);
    }
}
