package art.arcane.optics.aperture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import art.arcane.optics.frame.Frame;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.shape.FitMode;
import art.arcane.optics.shape.ShapeDescriptor;
import art.arcane.optics.shape.Shapes;
import org.junit.jupiter.api.Test;

final class ShapeBoundarySamplesTest {
    @Test
    void samplesFollowTheShapeOnThePlane() {
        ApertureDescriptor geometry = ApertureDescriptor.fromPortal(ShapedApertures.source(ShapedApertures.flat(Face.S, 9, 9),
            Frame.canonical(Face.S), ShapeDescriptor.of(Shapes.circle(1.0D), FitMode.CONTAIN), 0.25D)).orElseThrow();
        List<Vec3d> out = new ArrayList<Vec3d>();
        out.add(new Vec3d(0.0D, 0.0D, 0.0D));
        int added = ShapeBoundarySamples.append(out, geometry, 1.0D / 3.0D, Vec3d::new);
        assertEquals(added + 1, out.size());
        double perimeter = 2.0D * Math.PI * 4.5D;
        assertEquals(perimeter * 3.0D, added, 6.0D);
        Vec3d center = AperturePolygon.from(geometry).point(4.5D, 4.5D);
        for (int index = 1; index < out.size(); index++) {
            Vec3d sample = out.get(index);
            assertEquals(geometry.planeCoordinate(), sample.z(), 1.0E-12D);
            assertEquals(4.5D, Math.hypot(sample.x() - center.x(), sample.y() - center.y()), 0.05D);
            if (index > 1) {
                assertEquals(1.0D / 3.0D, sample.distance(out.get(index - 1)), 0.01D);
            }
        }
    }

    @Test
    void fullShapesLeaveTheCellOutline() {
        ApertureDescriptor geometry = ShapedApertures.descriptor(Frame.canonical(Face.U), 4, 4, ShapeDescriptor.FULL);
        List<Vec3d> out = new ArrayList<Vec3d>();
        assertEquals(0, ShapeBoundarySamples.append(out, geometry, 0.5D, Vec3d::new));
        assertTrue(out.isEmpty());
    }
}
