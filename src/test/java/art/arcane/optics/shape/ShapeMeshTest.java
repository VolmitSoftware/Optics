package art.arcane.optics.shape;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

final class ShapeMeshTest {
    @Test
    void trianglesAreCounterClockwiseAndInsideTheShape() {
        for (Shape shape : List.of(Ellipse.circle(1.0D), new Flower(5, 1.0D, 0.6D, 0.0D), new Heart(1.0D, 20.0D),
            new Ring(1.0D, 0.5D), new Star(5, 1.0D, 0.45D, 0.0D))) {
            PlaneShape plane = PlaneShape.fit(shape, FitMode.CONTAIN, 9, 7);
            ShapeMesh mesh = ShapeMesh.of(plane, 4, null);
            assertFalse(mesh.isEmpty());
            float[] positions = mesh.positions();
            int[] indices = mesh.indices();
            for (int triangle = 0; triangle < mesh.triangleCount(); triangle++) {
                int a = indices[triangle * 3];
                int b = indices[triangle * 3 + 1];
                int c = indices[triangle * 3 + 2];
                double area = (positions[b * 2] - positions[a * 2]) * (positions[c * 2 + 1] - positions[a * 2 + 1])
                    - (positions[c * 2] - positions[a * 2]) * (positions[b * 2 + 1] - positions[a * 2 + 1]);
                assertTrue(area > 0.0D, "triangle " + triangle);
            }
            for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
                assertTrue(mesh.distances()[vertex] <= 1.0E-6F, "distance " + vertex);
                double limit = shape instanceof Flower || shape instanceof Heart ? 0.05D : 1.0E-5D;
                assertTrue(plane.signedDistance(positions[vertex * 2], positions[vertex * 2 + 1]) <= limit, shape + " vertex " + vertex + " at " + positions[vertex * 2] + "," + positions[vertex * 2 + 1] + " sdf "
                    + plane.signedDistance(positions[vertex * 2], positions[vertex * 2 + 1]) + " distance " + mesh.distances()[vertex]);
            }
        }
    }

    @Test
    void trianglesCoverTheInsideSamples() {
        PlaneShape plane = PlaneShape.fit(new Flower(5, 1.0D, 0.6D, 10.0D), FitMode.CONTAIN, 9, 9);
        ShapeMesh mesh = ShapeMesh.of(plane, 4, null);
        for (int i = 0; i < 90; i++) {
            for (int j = 0; j < 90; j++) {
                double u = (i + 0.5D) / 10.0D;
                double v = (j + 0.5D) / 10.0D;
                if (plane.signedDistance(u, v) < -0.1D) {
                    assertTrue(covered(mesh, u, v), "sample " + u + "," + v);
                }
                if (plane.signedDistance(u, v) > 0.1D) {
                    assertFalse(covered(mesh, u, v), "sample " + u + "," + v);
                }
            }
        }
    }

    @Test
    void closedCellsProduceNoGeometry() {
        PlaneShape plane = PlaneShape.fit(Ellipse.circle(1.0D), FitMode.COVER, 7, 7);
        boolean[] open = new boolean[49];
        for (int cell = 0; cell < open.length; cell++) {
            open[cell] = cell != 24 && cell != 21;
        }
        long[] mask = maskOf(open);
        ShapeMesh mesh = ShapeMesh.of(plane, 4, mask);
        float[] positions = mesh.positions();
        int[] indices = mesh.indices();
        for (int triangle = 0; triangle < mesh.triangleCount(); triangle++) {
            double u = 0.0D;
            double v = 0.0D;
            for (int corner = 0; corner < 3; corner++) {
                u += positions[indices[triangle * 3 + corner] * 2] / 3.0D;
                v += positions[indices[triangle * 3 + corner] * 2 + 1] / 3.0D;
            }
            int cell = (int) v * 7 + (int) u;
            assertTrue(open[cell], "triangle " + triangle + " in closed cell " + cell);
        }
        assertTrue(covered(mesh, 3.5D, 2.5D));
        assertFalse(covered(mesh, 3.5D, 3.5D));
        assertTrue(covered(mesh, 2.99D, 3.5D));
        assertTrue(covered(mesh, 1.01D, 3.5D));
        assertFalse(covered(mesh, 0.5D, 3.5D));
    }

    @Test
    void outlinesAreClosedLoops() {
        PlaneShape plane = PlaneShape.fit(new Ring(1.0D, 0.5D), FitMode.CONTAIN, 9, 9);
        ShapeMesh mesh = ShapeMesh.of(plane, 4, null);
        List<Outline> outlines = mesh.outlines();
        assertEquals(2, outlines.size());
        for (Outline outline : outlines) {
            assertTrue(outline.size() >= 8);
            for (int index = 0; index < outline.size(); index++) {
                assertEquals(0.0D, plane.signedDistance(outline.u(index), outline.v(index)), 1.0E-5D);
            }
        }
        assertTrue(outlines.stream().anyMatch(Outline::clockwise));
        assertTrue(outlines.stream().anyMatch(outline -> !outline.clockwise()));
        assertEquals(4.5D * Math.PI * 2.0D, outlines.stream().mapToDouble(Outline::length).max().orElseThrow(), 0.2D);
    }

    @Test
    void meshesAreDeterministic() {
        PlaneShape plane = PlaneShape.fit(new Heart(1.0D, 0.0D), FitMode.STRETCH, 11, 6);
        ShapeMesh first = ShapeMesh.of(plane, 8, null);
        ShapeMesh second = ShapeMesh.of(PlaneShape.fit(new Heart(1.0D, 0.0D), FitMode.STRETCH, 11, 6), 8, null);
        assertArrayEquals(first.positions(), second.positions());
        assertArrayEquals(first.distances(), second.distances());
        assertArrayEquals(first.indices(), second.indices());
        assertEquals(first.outlines().size(), second.outlines().size());
        assertTrue(first.bounds().width() > 0.0D);
        assertThrows(IllegalArgumentException.class, () -> ShapeMesh.of(plane, 0, null));
        assertThrows(IllegalArgumentException.class, () -> ShapeMesh.of(plane, 17, null));
    }

    @Test
    void fullPlanesMeshTheOpenCells() {
        ShapeMesh mesh = PlaneShape.full(3, 2).mesh(2, null);
        assertEquals(1, mesh.outlines().size());
        assertEquals(6.0D, mesh.outlines().getFirst().signedArea(), 1.0E-6D);
        double area = 0.0D;
        float[] positions = mesh.positions();
        int[] indices = mesh.indices();
        for (int triangle = 0; triangle < mesh.triangleCount(); triangle++) {
            int a = indices[triangle * 3];
            int b = indices[triangle * 3 + 1];
            int c = indices[triangle * 3 + 2];
            area += 0.5D * ((positions[b * 2] - positions[a * 2]) * (positions[c * 2 + 1] - positions[a * 2 + 1])
                - (positions[c * 2] - positions[a * 2]) * (positions[b * 2 + 1] - positions[a * 2 + 1]));
        }
        assertEquals(6.0D, area, 1.0E-6D);
    }

    static boolean covered(ShapeMesh mesh, double u, double v) {
        float[] positions = mesh.positions();
        int[] indices = mesh.indices();
        for (int triangle = 0; triangle < mesh.triangleCount(); triangle++) {
            double ax = positions[indices[triangle * 3] * 2];
            double ay = positions[indices[triangle * 3] * 2 + 1];
            double bx = positions[indices[triangle * 3 + 1] * 2];
            double by = positions[indices[triangle * 3 + 1] * 2 + 1];
            double cx = positions[indices[triangle * 3 + 2] * 2];
            double cy = positions[indices[triangle * 3 + 2] * 2 + 1];
            double first = (bx - ax) * (v - ay) - (by - ay) * (u - ax);
            double second = (cx - bx) * (v - by) - (cy - by) * (u - bx);
            double third = (ax - cx) * (v - cy) - (ay - cy) * (u - cx);
            if (first >= 0.0D && second >= 0.0D && third >= 0.0D) {
                return true;
            }
        }
        return false;
    }

    static long[] maskOf(boolean[] open) {
        long[] mask = new long[(open.length + 63) >>> 6];
        for (int bit = 0; bit < open.length; bit++) {
            if (open[bit]) {
                mask[bit >>> 6] |= 1L << (bit & 63);
            }
        }
        return mask;
    }
}
