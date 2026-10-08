package art.arcane.optics.aperture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import art.arcane.optics.math.Box;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;
import org.junit.jupiter.api.Test;

final class ApertureCellsMergedFacesTest {
    @Test
    void mergedFacesCoverExactlyThePerCellFaces() {
        List<Vec3d> cells = new ArrayList<Vec3d>();
        for (int y = 60; y < 66; y++) {
            for (int x = -3; x < 4; x++) {
                boolean notch = y >= 63 && x >= 1;
                boolean hole = y == 61 && x == -1;
                if (!notch && !hole) {
                    cells.add(new Vec3d(x, y, 5));
                }
            }
        }
        ApertureCells aperture = new ApertureCells();
        aperture.setBlocks(cells);
        assertFalse(aperture.isFullCuboid());
        for (Face face : Face.values()) {
            List<Box> merged = aperture.getCachedApertureFaces(face);
            assertSame(merged, aperture.getCachedApertureFaces(face));
            assertTrue(merged.size() < cells.size(), face + " merged into " + merged.size());
            Set<Vec3d> covered = new HashSet<Vec3d>();
            for (Box box : merged) {
                assertEquals(0.0D, extent(box, face.axisIndex()), 0.0D);
                for (int x = (int) Math.floor(box.getXa()); x <= (int) Math.floor(box.getXb()); x++) {
                    for (int y = (int) Math.floor(box.getYa()); y <= (int) Math.floor(box.getYb()); y++) {
                        for (int z = (int) Math.floor(box.getZa()); z <= (int) Math.floor(box.getZb()); z++) {
                            Vec3d cell = new Vec3d(x, y, z);
                            assertTrue(cells.contains(cell), face + " covers missing cell " + cell);
                            assertTrue(covered.add(cell), face + " covers " + cell + " twice");
                        }
                    }
                }
            }
            assertEquals(new HashSet<Vec3d>(cells), covered, face.toString());
            for (Vec3d cell : cells) {
                Box single = new Box(cell.x(), cell.x() + 0.999D, cell.y(), cell.y() + 0.999D, cell.z(), cell.z() + 0.999D).getFace(face);
                long containing = merged.stream().filter(box -> encloses(box, single)).count();
                assertEquals(1L, containing, face + " cell " + cell);
            }
        }
    }

    @Test
    void cuboidAperturesKeepTheirSingleFace() {
        ApertureCells aperture = new ApertureCells();
        aperture.setArea(new Box(0.0D, 4.999D, 64.0D, 66.999D, 3.0D, 3.999D));
        assertTrue(aperture.isFullCuboid());
        for (Face face : Face.values()) {
            List<Box> faces = aperture.getCachedApertureFaces(face);
            assertEquals(1, faces.size());
            Box expected = aperture.getArea().getFace(face);
            assertTrue(encloses(expected, faces.getFirst()) && encloses(faces.getFirst(), expected), face.toString());
        }
    }

    private static double extent(Box box, int axis) {
        return switch (axis) {
            case 0 -> box.getXb() - box.getXa();
            case 1 -> box.getYb() - box.getYa();
            default -> box.getZb() - box.getZa();
        };
    }

    private static boolean encloses(Box outer, Box inner) {
        return outer.getXa() <= inner.getXa() && outer.getXb() >= inner.getXb() && outer.getYa() <= inner.getYa()
            && outer.getYb() >= inner.getYb() && outer.getZa() <= inner.getZa() && outer.getZb() >= inner.getZb();
    }
}
