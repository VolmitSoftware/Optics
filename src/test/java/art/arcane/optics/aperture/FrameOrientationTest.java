package art.arcane.optics.aperture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import art.arcane.optics.frame.Frame;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.shape.FitMode;
import art.arcane.optics.shape.PlaneShape;
import art.arcane.optics.shape.PlaneTransform;
import art.arcane.optics.shape.ShapeDescriptor;
import art.arcane.optics.shape.ShapeRaster;
import art.arcane.optics.shape.Shapes;
import org.junit.jupiter.api.Test;

final class FrameOrientationTest {
    @Test
    void unitAxesLandOnTheFrameAxesForEveryFrame() {
        ShapeDescriptor towardUp = ShapeDescriptor.of(Shapes.circle(0.25D).translated(0.0D, 0.6D), FitMode.CONTAIN);
        ShapeDescriptor towardRight = ShapeDescriptor.of(Shapes.circle(0.25D).translated(0.6D, 0.0D), FitMode.CONTAIN);
        for (Frame frame : ShapedApertures.frames()) {
            assertPointsAlong(ShapedApertures.descriptor(frame, 7, 7, towardUp), frame.getUp(), frame);
            assertPointsAlong(ShapedApertures.descriptor(frame, 7, 7, towardRight), frame.getRight(), frame);
            assertPointsAlong(ShapedApertures.descriptor(frame, 9, 5, towardUp.withFit(FitMode.STRETCH)), frame.getUp(), frame);
        }
    }

    @Test
    void orientationIsASignedPermutationOfTheFrameAxes() {
        for (Frame frame : ShapedApertures.frames()) {
            ApertureDescriptor geometry = ShapedApertures.descriptor(frame, 5, 5, ShapeDescriptor.FULL);
            PlaneTransform orientation = geometry.frameOrientation();
            assertEquals(1.0D, Math.abs(orientation.determinant()), 0.0D);
            assertEquals(0.0D, orientation.tu());
            assertEquals(0.0D, orientation.tv());
            AperturePolygon polygon = AperturePolygon.from(geometry);
            Vec3d origin = polygon.point(0.0D, 0.0D);
            assertEquals(frame.getRight().toVector(), polygon.point(orientation.a(), orientation.c()).subtract(origin));
            assertEquals(frame.getUp().toVector(), polygon.point(orientation.b(), orientation.d()).subtract(origin));
        }
    }

    private static void assertPointsAlong(ApertureDescriptor geometry, Face expected, Frame frame) {
        PlaneShape plane = geometry.planeShape();
        ShapeRaster raster = plane.raster(4);
        AperturePolygon polygon = AperturePolygon.from(geometry);
        Vec3d center = polygon.point(plane.columns() * 0.5D, plane.rows() * 0.5D);
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        int count = 0;
        for (int row = 0; row < plane.rows(); row++) {
            for (int column = 0; column < plane.columns(); column++) {
                if (raster.coverageFraction(column, row) > 0.0D) {
                    Vec3d point = polygon.point(column + 0.5D, row + 0.5D).subtract(center);
                    x += point.x() * raster.coverageFraction(column, row);
                    y += point.y() * raster.coverageFraction(column, row);
                    z += point.z() * raster.coverageFraction(column, row);
                    count++;
                }
            }
        }
        assertTrue(count > 0, frame.toString());
        Vec3d direction = new Vec3d(x, y, z).normalize();
        assertTrue(direction.dot(expected.toVector()) > 0.95D, "frame " + frame.getNormal() + "/" + frame.getUp() + " gave " + direction);
    }
}
