package art.arcane.optics.aperture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import art.arcane.optics.frame.Frame;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.shape.FitMode;
import art.arcane.optics.shape.ShapeDescriptor;
import art.arcane.optics.shape.ShapeMesh;
import art.arcane.optics.shape.Shapes;
import org.junit.jupiter.api.Test;

final class AperturePolygonMeshTest {
    private static final ShapeDescriptor CIRCLE = ShapeDescriptor.of(Shapes.circle(1.0D), FitMode.COVER);

    @Test
    void meshHonoursTheDescriptorMask() {
        ApertureDescriptor geometry = holed(Face.S);
        AperturePolygon polygon = AperturePolygon.from(geometry);
        assertTrue(polygon.hasShape());
        ShapeMesh mesh = polygon.mesh(4);
        assertSame(mesh, polygon.mesh(4));
        assertSame(polygon.planeShape(), polygon.planeShape());
        assertTrue(mesh.triangleCount() > 0);
        float[] positions = mesh.positions();
        int[] indices = mesh.indices();
        for (int triangle = 0; triangle < mesh.triangleCount(); triangle++) {
            double u = 0.0D;
            double v = 0.0D;
            for (int corner = 0; corner < 3; corner++) {
                u += positions[indices[triangle * 3 + corner] * 2] / 3.0D;
                v += positions[indices[triangle * 3 + corner] * 2 + 1] / 3.0D;
            }
            assertTrue(geometry.apertureOpen((int) u, (int) v), "triangle in closed cell " + (int) u + "," + (int) v);
        }
        assertThrows(IllegalArgumentException.class, () -> polygon.mesh(0));
    }

    @Test
    void containsIsShapeAware() {
        AperturePolygon polygon = AperturePolygon.from(holed(Face.W));
        assertFalse(polygon.contains(0.5D, 3.5D));
        assertFalse(polygon.contains(0.1D, 0.1D));
        assertTrue(polygon.contains(3.5D, 3.5D));
        assertTrue(polygon.contains(1.5D, 3.5D));
    }

    @Test
    void fullShapesKeepTheCellPath() {
        ApertureDescriptor geometry = holed(Face.N).withShape(ShapeDescriptor.FULL);
        AperturePolygon polygon = AperturePolygon.from(geometry);
        assertFalse(polygon.hasShape());
        assertTrue(polygon.contains(0.1D, 0.1D));
        assertFalse(polygon.contains(0.5D, 3.5D));
        assertTrue(polygon.planeShape().isFull());
        assertTrue(polygon.rectangles().contains(new AperturePolygon.Rectangle(1, 3, 7, 4)));
        assertEquals(3, polygon.rectangles().size());
    }

    private static ApertureDescriptor holed(Face normal) {
        Frame frame = Frame.canonical(normal);
        ApertureCells full = ShapedApertures.flat(normal, 7, 7);
        List<Vec3d> cells = new ArrayList<Vec3d>();
        int columnAxis = frame.getRight().axisIndex();
        int rowAxis = frame.getUp().axisIndex();
        for (Vec3d cell : full.getBlockPositions()) {
            int column = (int) (cell.component(columnAxis) - (columnAxis == 0 ? ShapedApertures.ORIGIN_X : columnAxis == 1
                ? ShapedApertures.ORIGIN_Y : ShapedApertures.ORIGIN_Z));
            int row = (int) (cell.component(rowAxis) - (rowAxis == 0 ? ShapedApertures.ORIGIN_X : rowAxis == 1
                ? ShapedApertures.ORIGIN_Y : ShapedApertures.ORIGIN_Z));
            if (column == 0 && row == 3) {
                continue;
            }
            cells.add(cell);
        }
        ApertureCells aperture = new ApertureCells();
        aperture.restore(full.getArea(), cells);
        return ApertureDescriptor.fromPortal(ShapedApertures.source(aperture, frame, CIRCLE, 0.0D)).orElseThrow();
    }
}
