package art.arcane.optics.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import art.arcane.optics.aperture.ApertureCells;
import art.arcane.optics.aperture.ApertureDescriptor;
import art.arcane.optics.aperture.SizeRatio;
import art.arcane.optics.claim.BlockClaim;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.QuarterTurn;
import art.arcane.optics.math.Box;
import art.arcane.optics.math.CellKeys;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.shape.PlaneShape;
import art.arcane.optics.shape.ShapeRaster;
import art.arcane.optics.transform.Affine;

final class PortalDefinitionApertureTest {
    @Test
    void apertureIsTheDefaultRasterInsideTheRectangle() {
        for (Frame frame : PortalFixtures.frames()) {
            PortalDefinition portal = PortalFixtures.portal(frame, 3, 60, -11, 9, 7).withShape("circle");
            ShapeRaster raster = ShapeRaster.of(portal.planeShape(), ShapeRaster.DEFAULT_SUBSAMPLES, ShapeRaster.DEFAULT_THRESHOLD);
            List<Vec3d> expected = new ArrayList<Vec3d>();
            raster.cells(portal.originX(), portal.originY(), portal.originZ(), frame.getNormal(), null, expected);

            ApertureCells aperture = portal.aperture();

            assertEquals(keys(expected), PortalFixtures.cells(portal), frame.toString());
            assertEquals(raster.insideCount(), aperture.getBlockPositions().size());
            assertTrue(raster.insideCount() < 63);
            assertEquals(portal.area().min(), aperture.getArea().min());
            assertEquals(portal.area().max(), aperture.getArea().max());
            assertSame(aperture, portal.aperture());
            assertSame(portal.planeShape(), portal.planeShape());
        }
    }

    @Test
    void fullPortalsOpenEveryCell() {
        PortalDefinition portal = PortalFixtures.wall(Face.U, -4, 70, 9, 4, 6);

        ApertureCells aperture = portal.aperture();

        assertTrue(aperture.isFullCuboid());
        assertEquals(24, aperture.getBlockPositions().size());
        assertTrue(portal.planeShape().isFull());
    }

    @Test
    void sourceBuildsAValidDescriptorWithTheDefinitionGeometry() {
        UUID target = UUID.randomUUID();
        for (Frame frame : PortalFixtures.frames()) {
            PortalDefinition portal = PortalFixtures.portal(frame, 3, 60, -11, 9, 7).withShape("star").withPlaneOffset(0.125D)
                .linked(PortalLink.to(target));

            ApertureDescriptor.Source source = portal.source(true, 48);
            ApertureDescriptor descriptor = ApertureDescriptor.fromPortal(source).orElseThrow();

            assertTrue(descriptor.valid(), frame.toString());
            assertEquals(frame, descriptor.frame());
            assertEquals(portal.columns(), descriptor.apertureWidth());
            assertEquals(portal.rows(), descriptor.apertureHeight());
            assertEquals(portal.originX(), descriptor.originX());
            assertEquals(portal.originY(), descriptor.originY());
            assertEquals(portal.originZ(), descriptor.originZ());
            assertEquals(portal.shape(), descriptor.shape());
            assertEquals(48, descriptor.depthBlocks());
            assertTrue(descriptor.frontSide());
            assertFalse(descriptor.mirror());
            assertEquals(0.125D, descriptor.planeOffset(), 0.0D);
            assertEquals(portal.origin().component(frame.getNormal().axisIndex()), descriptor.planeCoordinate(), 1.0E-12D);
            assertEquals(portal.aperture().getBlockPositions().size(), descriptor.openCellCount());
            assertEquals(target.getMostSignificantBits() ^ target.getLeastSignificantBits(), descriptor.targetIdentity());
            assertEquals(BlockClaim.LightingPolicy.LOCAL, source.lightingPolicy());
            assertEquals(ApertureDescriptor.BLACKOUT_OFF, source.blackoutPolicy());
            assertEquals(ApertureDescriptor.MASK_AIR_PROJECT, source.maskAirPolicy());
            assertEquals(1.0D, source.frustumCullingRatio(), 0.0D);
            assertEquals(List.of(), source.nested());
        }
    }

    @Test
    void mirrorLinksAndUnlinkedPortalsFillTheMirrorFields() {
        PortalDefinition mirror = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3).linked(PortalLink.mirror(QuarterTurn.DEGREES_180));
        ApertureDescriptor.Source source = mirror.source(false, 16);
        assertTrue(source.mirror());
        assertEquals(2, source.mirrorQuarterTurns());
        assertFalse(source.frontSide());
        assertEquals(0L, source.targetIdentity());

        ApertureDescriptor.Source unlinked = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3).source(true, 16);
        assertFalse(unlinked.mirror());
        assertEquals(0, unlinked.mirrorQuarterTurns());
        assertEquals(0L, unlinked.targetIdentity());
    }

    @Test
    void planeShapeOrientationFollowsTheFrame() {
        for (Frame frame : PortalFixtures.frames()) {
            PortalDefinition portal = PortalFixtures.portal(frame, 0, 64, 0, 9, 9).withShape("circle(radius=0.35)@offset(0,0.6)");
            ApertureDescriptor descriptor = ApertureDescriptor.fromPortal(portal.source(true, 8)).orElseThrow();
            PlaneShape expected = descriptor.planeShape();

            assertEquals(expected.placement(), portal.planeShape().placement(), frame.toString());
            double sum = 0.0D;
            Vec3d up = frame.getUp().toVector();
            for (Vec3d cell : portal.aperture().getBlockPositions()) {
                Vec3d center = cell.add(new Vec3d(0.5D, 0.5D, 0.5D));
                sum += center.subtract(portal.origin()).dot(up);
            }
            assertTrue(sum > 0.0D, frame + " shape +v must land on frame up");
            for (int row = 0; row < 9; row++) {
                for (int column = 0; column < 9; column++) {
                    assertEquals(expected.contains(column + 0.3D, row + 0.7D), portal.planeShape().contains(column + 0.3D, row + 0.7D));
                }
            }
        }
    }

    @Test
    void containsCellMatchesTheApertureCells() {
        for (Frame frame : PortalFixtures.frames()) {
            PortalDefinition portal = PortalFixtures.portal(frame, -3, 60, 5, 7, 5).withShape("heart");
            ApertureCells aperture = portal.aperture();
            Box area = portal.area();
            for (int x = (int) area.getXa() - 1; x <= (int) Math.floor(area.getXb()) + 1; x++) {
                for (int y = (int) area.getYa() - 1; y <= (int) Math.floor(area.getYb()) + 1; y++) {
                    for (int z = (int) area.getZa() - 1; z <= (int) Math.floor(area.getZb()) + 1; z++) {
                        assertEquals(aperture.containsBlock(x, y, z), portal.containsCell(x, y, z), frame + " " + x + "," + y + "," + z);
                    }
                }
            }
        }
    }

    @Test
    void containsPointIsExactInsideTheMaskAndHonoursThePlaneTolerance() {
        PortalDefinition portal = PortalFixtures.wall(Face.N, 0, 64, 0, 7, 7).withShape("circle");
        Vec3d center = portal.origin();

        assertTrue(portal.containsPoint(center, 0.0D));
        assertTrue(portal.containsPoint(center.add(new Vec3d(0.0D, 0.0D, 0.2D)), 0.25D));
        assertFalse(portal.containsPoint(center.add(new Vec3d(0.0D, 0.0D, 0.3D)), 0.25D));
        assertFalse(portal.containsPoint(new Vec3d(0.2D, 64.2D, 0.5D), 0.1D));
        assertFalse(portal.containsPoint(new Vec3d(-0.5D, 67.5D, 0.5D), 0.1D));
        assertTrue(portal.containsCell(1, 65, 0));
        assertFalse(portal.containsPoint(new Vec3d(1.01D, 65.01D, 0.5D), 0.1D));
        assertTrue(portal.containsPoint(new Vec3d(1.95D, 65.95D, 0.5D), 0.1D));
    }

    @Test
    void cellCoordinatesMatchTheDescriptor() {
        double[] expected = new double[2];
        double[] actual = new double[2];
        for (Frame frame : PortalFixtures.frames()) {
            PortalDefinition portal = PortalFixtures.portal(frame, -7, 30, 12, 5, 4);
            ApertureDescriptor descriptor = ApertureDescriptor.fromPortal(portal.source(true, 8)).orElseThrow();
            descriptor.cellCoordinates(-5.25D, 31.5D, 14.75D, expected);
            portal.cellCoordinates(-5.25D, 31.5D, 14.75D, actual);
            assertEquals(expected[0], actual[0], 0.0D);
            assertEquals(expected[1], actual[1], 0.0D);
        }
    }

    @Test
    void affineMapsTheUnitSquareOntoThePlane() {
        for (Frame frame : PortalFixtures.frames()) {
            PortalDefinition portal = PortalFixtures.portal(frame, 2, 64, -9, 5, 3);
            Affine affine = portal.affine();
            Vec3d right = frame.getRight().toVector();
            Vec3d up = frame.getUp().toVector();
            double halfWidth = (right.dot(axisVector(PortalFixtures.columnAxis(frame.getNormal()))) != 0.0D ? 5 : 3) * 0.5D;
            double halfHeight = (up.dot(axisVector(PortalFixtures.rowAxis(frame.getNormal()))) != 0.0D ? 3 : 5) * 0.5D;

            assertClose(portal.origin(), affine.point(Vec3d.ZERO));
            assertClose(portal.origin().add(right.multiply(halfWidth)), affine.point(Vec3d.UNIT_X));
            assertClose(portal.origin().add(up.multiply(halfHeight)), affine.point(Vec3d.UNIT_Y));
            assertClose(portal.origin().add(frame.getNormal().toVector()), affine.point(Vec3d.UNIT_Z));
            Set<Long> corners = new HashSet<Long>();
            for (int u = -1; u <= 1; u += 2) {
                for (int v = -1; v <= 1; v += 2) {
                    Vec3d corner = affine.point(new Vec3d(u * 0.999D, v * 0.999D, 0.0D));
                    corners.add(CellKeys.pack(corner.blockX(), corner.blockY(), corner.blockZ()));
                    assertTrue(portal.containsCell(corner.blockX(), corner.blockY(), corner.blockZ()), frame + " corner " + corner);
                }
            }
            assertEquals(4, corners.size());
        }
    }

    @Test
    void sizeRatioComparesTheRectangles() {
        PortalDefinition small = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);
        PortalDefinition large = PortalFixtures.wall(Face.U, 50, 64, 50, 9, 9);

        assertEquals(new SizeRatio(3.0D, true), small.sizeRatio(large));
        assertEquals(new SizeRatio(1.0D / 3.0D, true), large.sizeRatio(small));
    }

    private static Set<Long> keys(List<Vec3d> cells) {
        Set<Long> keys = new HashSet<Long>();
        for (Vec3d cell : cells) {
            keys.add(CellKeys.pack(cell.blockX(), cell.blockY(), cell.blockZ()));
        }
        return keys;
    }

    private static Vec3d axisVector(int axis) {
        return switch (axis) {
            case 0 -> Vec3d.UNIT_X;
            case 1 -> Vec3d.UNIT_Y;
            default -> Vec3d.UNIT_Z;
        };
    }

    private static void assertClose(Vec3d expected, Vec3d actual) {
        assertEquals(0.0D, expected.distance(actual), 1.0E-9D, expected + " vs " + actual);
    }
}
