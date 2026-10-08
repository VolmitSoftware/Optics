package art.arcane.optics.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import art.arcane.optics.crossing.PlaneCrossing;
import art.arcane.optics.crossing.ScaleRule;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.frame.QuarterTurn;
import art.arcane.optics.frame.Similarity;
import art.arcane.optics.frame.ViewWindow;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

final class PortalDefinitionCrossingTest {
    private static final Vec3d FORWARD = new Vec3d(0.0D, 0.0D, -1.0D);

    @Test
    void segmentThroughTheCenterCrosses() {
        PortalDefinition portal = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);
        Vec3d start = new Vec3d(1.5D, 65.5D, 1.5D);
        Vec3d end = new Vec3d(1.5D, 65.5D, -0.5D);

        PortalCrossing crossing = portal.crossing(start, end, FORWARD, FORWARD);

        assertNotNull(crossing);
        assertEquals(portal, crossing.portal());
        assertEquals(0.5D, crossing.fraction(), 1.0E-12D);
        assertEquals(1.5D, crossing.column(), 1.0E-12D);
        assertEquals(1.5D, crossing.row(), 1.0E-12D);
        assertEquals(portal.origin(), crossing.point());
        assertFalse(crossing.crossing().frontSide());
        assertEquals(portal.frame().view(false), crossing.crossing().frame());
        assertEquals(portal.origin(), crossing.crossing().origin());
        assertEquals(end, crossing.crossing().point());
        assertEquals(FORWARD, crossing.crossing().velocity());
        assertEquals(FORWARD, crossing.crossing().look());
    }

    @Test
    void crossingMatchesThePlaneCrossingFactory() {
        for (Frame frame : PortalFixtures.frames()) {
            PortalDefinition portal = PortalFixtures.portal(frame, -4, 50, 9, 5, 3);
            Vec3d normal = frame.getNormal().toVector();
            Vec3d center = portal.origin();
            Vec3d start = center.add(normal.multiply(0.75D)).add(frame.getRight().toVector().multiply(0.4D));
            Vec3d end = center.subtract(normal.multiply(0.25D)).add(frame.getUp().toVector().multiply(0.2D));
            Vec3d velocity = end.subtract(start);

            PortalCrossing crossing = portal.crossing(start, end, velocity, velocity);
            PlaneCrossing expected = PlaneCrossing.create(frame, center, new PlaneCrossing.Motion(start, end, velocity, velocity));

            assertNotNull(crossing, frame.toString());
            assertEquals(expected, crossing.crossing());
            assertEquals(0.75D, crossing.fraction(), 1.0E-12D);
            Vec3d hit = start.add(end.subtract(start).multiply(0.75D));
            assertEquals(0.0D, hit.distance(crossing.point()), 1.0E-9D);
            double[] coordinates = new double[2];
            portal.cellCoordinates(hit.x(), hit.y(), hit.z(), coordinates);
            assertEquals(coordinates[0], crossing.column(), 1.0E-9D);
            assertEquals(coordinates[1], crossing.row(), 1.0E-9D);
        }
    }

    @Test
    void cornerOutsideACircleDoesNotCross() {
        PortalDefinition portal = PortalFixtures.wall(Face.N, 0, 64, 0, 7, 7).withShape("circle");

        assertNull(portal.crossing(new Vec3d(0.5D, 64.5D, 1.0D), new Vec3d(0.5D, 64.5D, 0.0D), FORWARD, FORWARD));
        assertNull(portal.crossing(new Vec3d(1.01D, 65.01D, 1.0D), new Vec3d(1.01D, 65.01D, 0.0D), FORWARD, FORWARD));
        assertNotNull(portal.crossing(new Vec3d(1.95D, 65.95D, 1.0D), new Vec3d(1.95D, 65.95D, 0.0D), FORWARD, FORWARD));
        assertNotNull(portal.crossing(new Vec3d(3.5D, 67.5D, 1.0D), new Vec3d(3.5D, 67.5D, 0.0D), FORWARD, FORWARD));
    }

    @Test
    void segmentsThatMissThePlaneOrTheRectangleDoNotCross() {
        PortalDefinition portal = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);

        assertNull(portal.crossing(new Vec3d(1.5D, 65.5D, 3.0D), new Vec3d(1.5D, 65.5D, 1.0D), FORWARD, FORWARD));
        assertNull(portal.crossing(new Vec3d(1.5D, 65.5D, 0.5D), new Vec3d(2.5D, 65.5D, 0.5D), FORWARD, FORWARD));
        assertNull(portal.crossing(new Vec3d(5.5D, 65.5D, 1.0D), new Vec3d(5.5D, 65.5D, 0.0D), FORWARD, FORWARD));
        assertNull(portal.crossing(new Vec3d(1.5D, 63.9D, 1.0D), new Vec3d(1.5D, 63.9D, 0.0D), FORWARD, FORWARD));
        assertNull(portal.crossing(new Vec3d(3.0D, 65.5D, 1.0D), new Vec3d(3.0D, 65.5D, 0.0D), FORWARD, FORWARD));
    }

    @Test
    void fractionColumnAndRowFollowTheSegment() {
        PortalDefinition portal = PortalFixtures.wall(Face.E, 10, 64, 20, 4, 4);
        Vec3d start = new Vec3d(10.0D, 65.25D, 21.75D);
        Vec3d end = new Vec3d(12.0D, 65.25D, 21.75D);

        PortalCrossing crossing = portal.crossing(start, end, null, null);

        assertNotNull(crossing);
        assertEquals(0.25D, crossing.fraction(), 1.0E-12D);
        assertEquals(1.75D, crossing.column(), 1.0E-12D);
        assertEquals(1.25D, crossing.row(), 1.0E-12D);
        assertEquals(new Vec3d(10.5D, 65.25D, 21.75D), crossing.point());
        assertFalse(crossing.crossing().frontSide());
        assertEquals(end.subtract(start), crossing.crossing().velocity());
        assertEquals(end.subtract(start), crossing.crossing().look());
    }

    @Test
    void towardMatchesThePlaneCrossingForLinkedPairs() {
        for (Frame from : PortalFixtures.frames()) {
            for (Frame to : PortalFixtures.frames()) {
                PortalDefinition destination = PortalFixtures.portal(to, 40, 80, -30, 3, 3);
                PortalDefinition portal = PortalFixtures.portal(from, 0, 64, 0, 3, 3).linked(PortalLink.to(destination.id()));
                Vec3d normal = from.getNormal().toVector();
                for (int side = 0; side < 2; side++) {
                    double sign = side == 0 ? 1.0D : -1.0D;
                    Vec3d start = portal.origin().add(normal.multiply(sign * 0.5D));
                    Vec3d end = portal.origin().subtract(normal.multiply(sign * 0.5D));
                    PortalCrossing crossing = portal.crossing(start, end, end.subtract(start), normal);
                    assertNotNull(crossing);
                    Similarity toward = portal.toward(destination, crossing.crossing().frontSide());
                    assertTrue(toward.isRigid());
                    assertEquals(crossing.crossing().toward(destination.frame(), destination.origin()), toward.rigid(), from + " -> " + to);
                    assertEquals(crossing.crossing().toward(destination.frame(), destination.origin(), 1.0D), toward);
                }
            }
        }
    }

    @Test
    void towardScalesByTheLinkRule() {
        PortalDefinition destination = PortalFixtures.wall(Face.S, 100, 64, 100, 9, 9);
        PortalDefinition portal = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3).linked(PortalLink.to(destination.id()).withScale(ScaleRule.ratio(0.25D, 4.0D)));
        PortalCrossing crossing = portal.crossing(new Vec3d(1.5D, 65.5D, 1.0D), new Vec3d(1.5D, 65.5D, 0.0D), FORWARD, FORWARD);

        Similarity toward = portal.toward(destination, crossing.crossing().frontSide());

        assertEquals(3.0D, toward.scale(), 0.0D);
        assertEquals(crossing.crossing().toward(destination.frame(), destination.origin(), 3.0D), toward);
        assertEquals(3.0D, portal.linked(PortalLink.to(destination.id()).withScale(ScaleRule.motion())).toward(destination, true).scale(), 0.0D);
        assertEquals(1.0D, PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3).toward(destination, true).scale(), 0.0D);
    }

    @Test
    void mirrorLinksReflectAboutTheirOwnPlaneLikeTheViewWindow() {
        for (Frame frame : PortalFixtures.frames()) {
            for (QuarterTurn turn : QuarterTurn.values()) {
                PortalDefinition mirror = PortalFixtures.portal(frame, 5, 64, 5, 3, 3).linked(PortalLink.mirror(turn));
                for (int side = 0; side < 2; side++) {
                    boolean front = side == 0;
                    Similarity expected = Similarity.of(ViewWindow.mirror(mirror.origin(), frame, turn, front, 0.0D).toward(), 1.0D);

                    assertEquals(expected, mirror.toward(mirror, front));
                    assertEquals(expected, mirror.toward(PortalFixtures.wall(Face.U, 0, 0, 0, 1, 1), front));
                }
                if (turn == QuarterTurn.DEGREES_0 || turn == QuarterTurn.DEGREES_180) {
                    assertEquals(OpticTransform.mirror(frame, mirror.origin(), turn).permutation(), mirror.toward(mirror, true).rigid().permutation());
                }
            }
        }
    }

    @Test
    void linkedTowardMatchesTheViewWindow() {
        for (Frame from : PortalFixtures.frames()) {
            for (Frame to : PortalFixtures.frames()) {
                PortalDefinition destination = PortalFixtures.portal(to, -12, 70, 33, 2, 3);
                PortalDefinition portal = PortalFixtures.portal(from, 0, 64, 0, 2, 3).linked(PortalLink.to(destination.id()));
                for (int side = 0; side < 2; side++) {
                    boolean front = side == 0;
                    OpticTransform expected = ViewWindow.of(false, QuarterTurn.DEGREES_0, portal.origin(), from, destination.origin(), to, front, 0.0D)
                        .toward();

                    assertEquals(expected, portal.toward(destination, front).rigid());
                }
            }
        }
    }

    @Test
    void crossingResultsAreIndependentOfTheLinkTarget() {
        UUID target = UUID.randomUUID();
        PortalDefinition portal = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);

        PortalCrossing unlinked = portal.crossing(new Vec3d(1.5D, 65.5D, 1.0D), new Vec3d(1.5D, 65.5D, 0.0D), FORWARD, FORWARD);
        PortalCrossing linked = portal.linked(PortalLink.to(target)).crossing(new Vec3d(1.5D, 65.5D, 1.0D), new Vec3d(1.5D, 65.5D, 0.0D), FORWARD, FORWARD);

        assertEquals(unlinked.crossing(), linked.crossing());
        assertEquals(unlinked.fraction(), linked.fraction(), 0.0D);
    }
}
