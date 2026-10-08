package art.arcane.optics.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import art.arcane.optics.frame.Frame;
import art.arcane.optics.math.Box;
import art.arcane.optics.math.CellKeys;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

final class PortalFixtures {
    private PortalFixtures() {
    }

    static List<Frame> frames() {
        ArrayList<Frame> frames = new ArrayList<Frame>(24);
        for (Face normal : Face.values()) {
            Frame frame = Frame.canonical(normal);
            for (int turn = 0; turn < 4; turn++) {
                frames.add(frame);
                frame = frame.rotateClockwise();
            }
        }
        return List.copyOf(frames);
    }

    static PortalDefinition portal(Frame frame, int x, int y, int z, int columns, int rows) {
        return PortalDefinition.builder().frame(frame).origin(x, y, z).size(columns, rows).build();
    }

    static PortalDefinition wall(Face normal, int x, int y, int z, int columns, int rows) {
        return PortalDefinition.builder().facing(normal).origin(x, y, z).size(columns, rows).build();
    }

    static Set<Long> cells(PortalDefinition portal) {
        Set<Long> cells = new HashSet<Long>();
        for (Vec3d cell : portal.aperture().getBlockPositions()) {
            cells.add(CellKeys.pack(cell.blockX(), cell.blockY(), cell.blockZ()));
        }
        return cells;
    }

    static int columnAxis(Face normal) {
        return Frame.canonical(normal).getRight().axisIndex();
    }

    static int rowAxis(Face normal) {
        return Frame.canonical(normal).getUp().axisIndex();
    }

    static void assertConsistent(PortalDefinition portal) {
        Face normal = portal.frame().getNormal();
        int normalAxis = normal.axisIndex();
        int columnAxis = columnAxis(normal);
        int rowAxis = rowAxis(normal);
        int[] origin = {portal.originX(), portal.originY(), portal.originZ()};
        int[] max = origin.clone();
        max[columnAxis] += portal.columns() - 1;
        max[rowAxis] += portal.rows() - 1;
        Box area = portal.area();
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(origin[axis], area.min(axis), 0.0D);
            assertEquals(max[axis] + 0.999D, area.max(axis), 0.0D);
        }
        Vec3d center = portal.origin();
        assertEquals(origin[columnAxis] + portal.columns() * 0.5D, center.component(columnAxis), 0.0D);
        assertEquals(origin[rowAxis] + portal.rows() * 0.5D, center.component(rowAxis), 0.0D);
        assertEquals(origin[normalAxis] + 0.5D + normal.sign() * portal.planeOffset(), center.component(normalAxis), 1.0E-12D);
        assertEquals(center, portal.center());
        assertEquals(portal.columns(), portal.width(), 0.0D);
        assertEquals(portal.rows(), portal.height(), 0.0D);
    }
}
