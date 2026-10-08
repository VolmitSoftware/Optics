package art.arcane.optics.portal;

import static art.arcane.optics.portal.PortalFixtures.assertConsistent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.frame.QuarterTurn;
import art.arcane.optics.math.Axis;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.shape.PlaneTransform;
import art.arcane.optics.shape.ShapeDescriptor;

final class PortalDefinitionOpsTest {
    @Test
    void movesShiftCellsAndCenterTogether() {
        PortalDefinition portal = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 5);

        PortalDefinition moved = portal.moved(2, -3, 7);
        assertEquals(2, moved.originX());
        assertEquals(61, moved.originY());
        assertEquals(7, moved.originZ());
        assertEquals(portal.origin().add(new Vec3d(2.0D, -3.0D, 7.0D)), moved.origin());
        assertConsistent(moved);

        PortalDefinition placed = portal.movedTo(-40, 12, 9);
        assertEquals(-40, placed.originX());
        assertEquals(12, placed.originY());
        assertEquals(9, placed.originZ());
        assertConsistent(placed);

        PortalDefinition centered = portal.centeredAt(new Vec3d(100.5D, 70.5D, -4.5D));
        assertEquals(new Vec3d(100.5D, 70.5D, -4.5D), centered.origin());
        assertEquals(99, centered.originX());
        assertEquals(68, centered.originY());
        assertEquals(-5, centered.originZ());
        assertConsistent(centered);
        assertEquals(new Vec3d(1.5D, 66.5D, 0.5D), portal.origin());
    }

    @Test
    void rotatedTurnsTheFrameAboutTheNormalAndKeepsTheCenter() {
        for (Frame frame : PortalFixtures.frames()) {
            PortalDefinition portal = PortalFixtures.portal(frame, 10, 64, -7, 3, 5);
            Frame expected = frame;
            for (QuarterTurn turn : QuarterTurn.values()) {
                PortalDefinition rotated = portal.rotated(turn);
                assertEquals(expected, rotated.frame(), frame + " " + turn);
                assertEquals(portal.origin(), rotated.origin(), frame + " " + turn);
                boolean odd = (turn.getQuarterTurns() & 1) == 1;
                assertEquals(odd ? portal.rows() : portal.columns(), rotated.columns(), frame + " " + turn);
                assertEquals(odd ? portal.columns() : portal.rows(), rotated.rows(), frame + " " + turn);
                assertEquals(portal.shape(), rotated.shape());
                assertConsistent(rotated);
                if (!odd) {
                    assertEquals(PortalFixtures.cells(portal), PortalFixtures.cells(rotated));
                }
                expected = expected.rotateClockwise();
            }
        }
    }

    @Test
    void rotatedWithMixedParityStaysWithinHalfABlockOfTheCenter() {
        PortalDefinition portal = PortalFixtures.wall(Face.E, 3, 64, 2, 2, 3);

        PortalDefinition rotated = portal.rotated(QuarterTurn.DEGREES_90);

        assertEquals(3, rotated.columns());
        assertEquals(2, rotated.rows());
        assertTrue(rotated.origin().distance(portal.origin()) <= Math.sqrt(0.5D) + 1.0E-12D);
        assertConsistent(rotated);
    }

    @Test
    void rotatedAboutAWorldAxisTurnsCounterClockwiseByTheRightHandRule() {
        PortalDefinition portal = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 5);

        PortalDefinition turned = portal.rotatedAbout(Axis.Y, QuarterTurn.DEGREES_90);

        assertEquals(Face.W, turned.frame().getNormal());
        assertEquals(Face.U, turned.frame().getUp());
        assertEquals(portal.origin(), turned.origin());
        assertEquals(3, turned.columns());
        assertEquals(5, turned.rows());
        assertConsistent(turned);
    }

    @Test
    void rotatedAboutMatchesTransformedByTheRigidRotationAboutTheCenter() {
        for (Frame frame : PortalFixtures.frames()) {
            PortalDefinition portal = PortalFixtures.portal(frame, -9, 40, 13, 3, 5);
            Vec3d pivot = portal.origin();
            for (Axis axis : Axis.values()) {
                for (QuarterTurn turn : QuarterTurn.values()) {
                    OpticTransform rotation = about(pivot, rotation(axis, turn));
                    PortalDefinition expected = portal.transformed(rotation);
                    PortalDefinition actual = portal.rotatedAbout(axis, turn);
                    assertEquals(expected, actual, frame + " " + axis + " " + turn);
                    assertEquals(rotation.frame(frame), actual.frame());
                    assertEquals(pivot, actual.origin());
                    assertCellsMap(portal, actual, rotation);
                }
            }
        }
    }

    @Test
    void transformedByIntegralRigidMapsEveryCellOntoACell() {
        SplittableRandom random = new SplittableRandom(311L);
        for (Frame frame : PortalFixtures.frames()) {
            PortalDefinition portal = PortalFixtures.portal(frame, random.nextInt(-50, 50), random.nextInt(0, 100), random.nextInt(-50, 50),
                random.nextInt(3, 7), random.nextInt(3, 7)).withShape("circle");
            for (int sample = 0; sample < 24; sample++) {
                OpticTransform rigid = OpticTransform.of(AxisPermutation.ofIndex(random.nextInt(48)), random.nextInt(-64, 64),
                    random.nextInt(-64, 64), random.nextInt(-64, 64));
                PortalDefinition mapped = portal.transformed(rigid);
                assertEquals(rigid.frame(frame), mapped.frame());
                assertEquals(portal.shape(), mapped.shape());
                assertEquals(rigid.point(portal.origin()).distance(mapped.origin()), 0.0D, 1.0E-9D);
                assertConsistent(mapped);
                int[] cell = new int[3];
                int[] image = new int[3];
                for (Vec3d block : portal.aperture().getBlockPositions()) {
                    cell[0] = block.blockX();
                    cell[1] = block.blockY();
                    cell[2] = block.blockZ();
                    rigid.cellInto(cell[0], cell[1], cell[2], image);
                    assertTrue(mapped.area().containsPrimitive(image[0] + 0.5D, image[1] + 0.5D, image[2] + 0.5D), frame + " " + rigid);
                }
            }
        }
    }

    @Test
    void facingKeepsTheSizeAlongRightAndUp() {
        PortalDefinition portal = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 5);

        PortalDefinition floor = portal.facing(Face.U);

        assertEquals(Frame.canonical(Face.N).withNormal(Face.U), floor.frame());
        assertEquals(portal.origin(), floor.origin());
        assertEquals(3, frameWidth(floor));
        assertEquals(5, frameHeight(floor));
        assertConsistent(floor);
        assertEquals(portal, portal.facing(Face.N));
    }

    @Test
    void flippedKeepsTheCellsAndReversesTheNormal() {
        for (Frame frame : PortalFixtures.frames()) {
            PortalDefinition portal = PortalFixtures.portal(frame, 5, 70, 5, 4, 3).withShape("feather");

            PortalDefinition flipped = portal.flipped();

            assertEquals(frame.flipNormal(), flipped.frame());
            assertEquals(portal.originX(), flipped.originX());
            assertEquals(portal.originY(), flipped.originY());
            assertEquals(portal.originZ(), flipped.originZ());
            assertEquals(portal.columns(), flipped.columns());
            assertEquals(portal.rows(), flipped.rows());
            assertEquals(portal.shape(), flipped.shape());
            assertEquals(portal.origin(), flipped.origin());
            assertConsistent(flipped);
        }
    }

    @Test
    void flippedKeepsAnOffsetPlaneInPlace() {
        PortalDefinition portal = PortalFixtures.wall(Face.E, 2, 64, 2, 3, 3).withPlaneOffset(0.25D);

        PortalDefinition flipped = portal.flipped();

        assertEquals(-0.25D, flipped.planeOffset(), 0.0D);
        assertEquals(portal.origin(), flipped.origin());
        assertEquals(PortalFixtures.cells(portal), PortalFixtures.cells(flipped));
        assertEquals(portal, flipped.flipped());
    }

    @Test
    void mirroringFlipsTheShapeOnly() {
        PortalDefinition portal = PortalFixtures.wall(Face.S, 0, 64, 0, 7, 7).withShape("feather");

        assertEquals(portal.shape().transformed(PlaneTransform.flipU()), portal.mirroredU().shape());
        assertEquals(portal.shape().transformed(PlaneTransform.flipV()), portal.mirroredV().shape());
        assertEquals(portal.frame(), portal.mirroredU().frame());
        assertEquals(portal.area().min(), portal.mirroredV().area().min());
        PortalDefinition full = PortalFixtures.wall(Face.S, 0, 64, 0, 7, 7);
        assertEquals(ShapeDescriptor.FULL, full.mirroredU().shape());
        assertEquals(ShapeDescriptor.FULL, full.mirroredV().shape());
    }

    @Test
    void scaledResizesAboutTheCenterWithAOneCellMinimum() {
        PortalDefinition portal = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);

        PortalDefinition doubled = portal.scaled(2.0D);
        assertEquals(6, doubled.columns());
        assertEquals(6, doubled.rows());
        assertTrue(doubled.origin().distance(portal.origin()) <= Math.sqrt(0.5D) + 1.0E-12D);
        assertConsistent(doubled);

        PortalDefinition tripled = portal.scaled(3.0D);
        assertEquals(9, tripled.columns());
        assertEquals(portal.origin(), tripled.origin());

        PortalDefinition tiny = portal.scaled(0.01D);
        assertEquals(1, tiny.columns());
        assertEquals(1, tiny.rows());
        assertEquals(portal.origin(), tiny.origin());

        assertThrows(IllegalArgumentException.class, () -> portal.scaled(0.0D));
        assertThrows(IllegalArgumentException.class, () -> portal.scaled(Double.NaN));
    }

    @Test
    void scaledPerAxisFollowsTheFrameRightAndUp() {
        PortalDefinition wall = PortalFixtures.wall(Face.N, 0, 64, 0, 4, 2);
        PortalDefinition stretched = wall.scaled(0.5D, 2.0D);
        assertEquals(2, stretched.columns());
        assertEquals(4, stretched.rows());
        assertConsistent(stretched);

        Frame quarter = Frame.canonical(Face.U).rotateClockwise();
        PortalDefinition floor = PortalFixtures.portal(quarter, 0, 64, 0, 4, 2);
        assertEquals(2, frameWidth(floor));
        PortalDefinition widened = floor.scaled(3.0D, 1.0D);
        assertEquals(6, frameWidth(widened));
        assertEquals(4, frameHeight(widened));
        assertEquals(4, widened.columns());
        assertEquals(6, widened.rows());
    }

    @Test
    void resizedKeepsTheCenter() {
        PortalDefinition portal = PortalFixtures.wall(Face.E, 8, 64, 8, 3, 3);

        PortalDefinition resized = portal.resized(5, 7);

        assertEquals(5, resized.columns());
        assertEquals(7, resized.rows());
        assertEquals(portal.origin(), resized.origin());
        assertConsistent(resized);
        assertThrows(IllegalArgumentException.class, () -> portal.resized(0, 3));
    }

    @Test
    void stretchedGrowsTowardThePositiveFrameAxes() {
        PortalDefinition north = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);
        PortalDefinition grownNorth = north.stretched(1, 2);
        assertEquals(0, grownNorth.originX());
        assertEquals(64, grownNorth.originY());
        assertEquals(4, grownNorth.columns());
        assertEquals(5, grownNorth.rows());

        PortalDefinition south = PortalFixtures.wall(Face.S, 0, 64, 0, 3, 3);
        PortalDefinition grownSouth = south.stretched(1, 0);
        assertEquals(-1, grownSouth.originX());
        assertEquals(4, grownSouth.columns());
        assertEquals(Face.W, south.frame().getRight());

        PortalDefinition shrunk = north.stretched(-1, -1);
        assertEquals(0, shrunk.originX());
        assertEquals(64, shrunk.originY());
        assertEquals(2, shrunk.columns());
        assertEquals(2, shrunk.rows());

        PortalDefinition floor = PortalFixtures.wall(Face.D, 0, 64, 0, 3, 3);
        PortalDefinition grownFloor = floor.stretched(0, 2);
        assertEquals(Face.N, floor.frame().getUp());
        assertEquals(-2, grownFloor.originZ());
        assertEquals(5, grownFloor.rows());

        PortalDefinition clamped = north.stretched(-10, 0);
        assertEquals(1, clamped.columns());
        assertEquals(0, clamped.originX());
    }

    @Test
    void shapeOperationsChangeOnlyTheDescriptor() {
        PortalDefinition portal = PortalFixtures.wall(Face.N, 0, 64, 0, 7, 7).withShape("heart");

        PortalDefinition rotated = portal.shapeRotated(45.0D);
        assertEquals(portal.shape().transformed(PlaneTransform.rotation(45.0D)), rotated.shape());
        assertEquals(portal.frame(), rotated.frame());
        assertEquals(portal.origin(), rotated.origin());
        assertEquals(portal.columns(), rotated.columns());

        PortalDefinition scaled = portal.shapeScaled(0.5D);
        assertEquals(portal.shape().transformed(PlaneTransform.scale(0.5D)), scaled.shape());

        assertEquals(ShapeDescriptor.parse("ring(inner=0.5)"), portal.withShape("ring(inner=0.5)").shape());
        assertEquals(ShapeDescriptor.FULL, portal.withShape(ShapeDescriptor.FULL).shape());
        assertThrows(IllegalArgumentException.class, () -> portal.withShape("circle(radius=0.01)"));
    }

    @Test
    void planeOffsetMovesThePlaneButNotTheCells() {
        PortalDefinition portal = PortalFixtures.wall(Face.W, 4, 64, 4, 3, 3);

        PortalDefinition offset = portal.withPlaneOffset(0.375D);

        assertEquals(0.375D, offset.planeOffset(), 0.0D);
        assertEquals(portal.originX(), offset.originX());
        assertEquals(4.5D - 0.375D, offset.origin().x(), 1.0E-12D);
        assertEquals(PortalFixtures.cells(portal), PortalFixtures.cells(offset));
        assertConsistent(offset);
    }

    @Test
    void linksAndMetadataAreReplacedImmutably() {
        PortalDefinition portal = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3).withMetadata("name", "Hub");
        UUID target = UUID.randomUUID();

        PortalDefinition linked = portal.linked(PortalLink.to(target));
        assertEquals(PortalLink.to(target), linked.link());
        assertNull(portal.link());
        assertNull(linked.unlinked().link());
        assertEquals("Hub", linked.metadata("name"));
        assertNull(linked.withoutMetadata("name").metadata("name"));
        assertEquals("Spawn", linked.withMetadata("name", "Spawn").metadata("name"));
        assertEquals("Hub", linked.metadata("name"));
        assertThrows(IllegalArgumentException.class, () -> portal.linked(PortalLink.to(portal.id())));
        assertNotSame(portal, portal.withMetadata("name", "Hub2"));
    }

    @Test
    void idPreservingOperationsKeepTheId() {
        PortalDefinition portal = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);
        UUID id = portal.id();

        assertEquals(id, portal.moved(1, 1, 1).id());
        assertEquals(id, portal.rotated(QuarterTurn.DEGREES_90).id());
        assertEquals(id, portal.rotatedAbout(Axis.X, QuarterTurn.DEGREES_270).id());
        assertEquals(id, portal.flipped().id());
        assertEquals(id, portal.scaled(2.0D).id());
        assertEquals(id, portal.withShape("circle").id());
    }

    private static void assertCellsMap(PortalDefinition portal, PortalDefinition mapped, OpticTransform rigid) {
        double[] center = new double[3];
        for (Vec3d block : portal.aperture().getBlockPositions()) {
            rigid.pointInto(block.x() + 0.5D, block.y() + 0.5D, block.z() + 0.5D, center);
            assertTrue(mapped.containsCell((int) Math.floor(center[0]), (int) Math.floor(center[1]), (int) Math.floor(center[2])),
                portal + " -> " + mapped);
        }
    }

    private static AxisPermutation rotation(Axis axis, QuarterTurn turn) {
        Face east = Face.E;
        Face up = Face.U;
        Face south = Face.S;
        for (int step = 0; step < turn.getQuarterTurns(); step++) {
            east = quarter(axis, east);
            up = quarter(axis, up);
            south = quarter(axis, south);
        }
        return AxisPermutation.of(east, up, south);
    }

    private static Face quarter(Axis axis, Face face) {
        Vec3d v = face.toVector();
        Vec3d turned = switch (axis) {
            case X -> new Vec3d(v.x(), -v.z(), v.y());
            case Y -> new Vec3d(v.z(), v.y(), -v.x());
            case Z -> new Vec3d(-v.y(), v.x(), v.z());
        };
        return Face.closest(turned);
    }

    private static OpticTransform about(Vec3d pivot, AxisPermutation permutation) {
        double[] rotated = new double[3];
        permutation.vectorInto(pivot.x(), pivot.y(), pivot.z(), rotated);
        return OpticTransform.of(permutation, pivot.x() - rotated[0], pivot.y() - rotated[1], pivot.z() - rotated[2]);
    }

    private static int frameWidth(PortalDefinition portal) {
        return portal.frame().getRight().axisIndex() == PortalFixtures.columnAxis(portal.frame().getNormal()) ? portal.columns() : portal.rows();
    }

    private static int frameHeight(PortalDefinition portal) {
        return portal.frame().getUp().axisIndex() == PortalFixtures.rowAxis(portal.frame().getNormal()) ? portal.rows() : portal.columns();
    }
}
