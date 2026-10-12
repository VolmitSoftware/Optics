package art.arcane.optics.demo.scene;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import art.arcane.optics.math.Vec3d;
import art.arcane.optics.portal.PortalCrossing;
import art.arcane.optics.shape.Shape;
import art.arcane.optics.transform.Affine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoScenesTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void catalogHasUniqueStableIdsAndBoundedFrames() {
        Set<String> ids = new HashSet<String>();
        assertEquals(12, DemoScenes.all().size());
        for (DemoScene scene : DemoScenes.all()) {
            assertTrue(ids.add(scene.id()), scene.id());
            assertEquals(scene, DemoScenes.find(scene.id()).orElseThrow());
            assertEquals((int) Math.ceil(scene.durationSeconds() * 20.0D), scene.durationTicks());
            for (int tick = 0; tick <= scene.durationTicks(); tick++) {
                double seconds = tick / 20.0D;
                SceneFrame frame = scene.sample(seconds);
                assertTrue(frame.portals().size() <= 2, scene.id());
                assertEquals(scene.camera(), frame.camera());
                for (ScenePortal portal : frame.portals()) {
                    assertTrue(portal.pose().isFinite(), scene.id());
                    assertTrue(portal.viewTransform().isFinite(), scene.id());
                    assertFalse(portal.aperture().bounds().isEmpty(), scene.id());
                    if (portal.visible()) {
                        assertTrue(Math.abs(portal.pose().determinant()) >= 1.0E-12D, scene.id());
                    }
                }
            }
        }
        assertTrue(DemoScenes.find("missing-scene").isEmpty());
    }

    @Test
    void samplingIsIndependentOfFrameOrderAndPriorRuns() {
        for (DemoScene scene : DemoScenes.all()) {
            double seconds = scene.durationSeconds() * 0.37D;
            SceneFrame first = scene.sample(seconds);
            scene.sample(scene.durationSeconds());
            scene.sample(0.0D);
            assertEquals(first, scene.sample(seconds), scene.id());
            assertEquals(scene.sample(0.0D), scene.sample(-10.0D), scene.id());
            assertEquals(scene.sample(scene.durationSeconds()), scene.sample(scene.durationSeconds() + 10.0D), scene.id());
            assertThrows(IllegalArgumentException.class, () -> scene.sample(Double.NaN));
            assertThrows(IllegalArgumentException.class, () -> scene.sample(Double.POSITIVE_INFINITY));
            assertThrows(IllegalArgumentException.class, () -> scene.sample(Double.NEGATIVE_INFINITY));
        }
    }

    @Test
    void growingApertureReachesFullSizeAndVanishesWithoutDefinitionResizing() {
        DemoScene scene = scene("grow-vanish");
        ScenePortal small = portal(scene, 0.0D);
        ScenePortal big = portal(scene, 5.0D);
        ScenePortal closing = portal(scene, 8.0D);
        ScenePortal vanished = portal(scene, 9.0D);
        assertTrue(small.visible());
        assertEquals(0.255D, small.pose().vector(new Vec3d(1.0D, 0.0D, 0.0D)).length(), EPSILON);
        assertEquals(1.7D, big.pose().vector(new Vec3d(1.0D, 0.0D, 0.0D)).length(), EPSILON);
        assertTrue(closing.pose().determinant() < big.pose().determinant());
        assertFalse(vanished.visible());
        assertEquals(0.0D, vanished.pose().determinant());
        assertFalse(portal(scene, scene.durationSeconds()).visible());
        assertFalse(portal(scene, 8.999999D).visible());
        assertTrue(scene.crossingRoute().isEmpty());
        assertEquals(CrossingMode.RENDER_ONLY, big.crossingMode());
    }

    @Test
    void axisScaleChangesWidthHeightAndDestinationDepthIndependently() {
        DemoScene scene = scene("axis-scale");
        ScenePortal wide = portal(scene, 3.0D);
        ScenePortal tall = portal(scene, 6.0D);
        ScenePortal depth = portal(scene, 9.0D);
        assertEquals(1.8D, wide.pose().element(0, 0), EPSILON);
        assertEquals(0.55D, wide.pose().element(1, 1), EPSILON);
        assertEquals(0.55D, tall.pose().element(0, 0), EPSILON);
        assertEquals(1.8D, tall.pose().element(1, 1), EPSILON);
        assertEquals(1.0D, depth.pose().element(2, 2), EPSILON);
        assertEquals(2.0D, depth.viewTransform().element(2, 2), EPSILON);
    }

    @Test
    void spinPassesHalfTurnAndContinuesPastACompleteTurn() {
        DemoScene scene = scene("continuous-spin");
        Vec3d up = new Vec3d(0.0D, 1.0D, 0.0D);
        assertVector(new Vec3d(-1.6D, 0.0D, 0.0D), portal(scene, 1.0D).pose().vector(up));
        assertVector(new Vec3d(0.0D, -1.6D, 0.0D), portal(scene, 2.0D).pose().vector(up));
        assertVector(new Vec3d(1.6D, 0.0D, 0.0D), portal(scene, 3.0D).pose().vector(up));
        assertVector(portal(scene, 0.0D).pose().vector(up), portal(scene, 4.0D).pose().vector(up));
        assertVector(portal(scene, 1.0D).pose().vector(up), portal(scene, 5.0D).pose().vector(up));
        assertVector(portal(scene, 1.0D).pose().vector(up), portal(scene, 13.0D).pose().vector(up));
    }

    @Test
    void easingComparisonHasMatchedEndpointsAndDifferentAcceleration() {
        DemoScene scene = scene("easing-comparison");
        List<ScenePortal> start = scene.sample(0.0D).portals();
        List<ScenePortal> early = scene.sample(0.75D).portals();
        List<ScenePortal> end = scene.sample(3.0D).portals();
        assertEquals(start.get(0).pose().element(1, 1), start.get(1).pose().element(1, 1), EPSILON);
        assertEquals(end.get(0).pose().element(1, 1), end.get(1).pose().element(1, 1), EPSILON);
        assertTrue(early.get(1).pose().element(1, 1) < early.get(0).pose().element(1, 1));
    }

    @Test
    void loopsReturnToKeyframesAndPingPongReversesWithoutJumping() {
        DemoScene keyframes = scene("keyframe-loop");
        assertEquals(portal(keyframes, 0.0D).pose(), portal(keyframes, 6.0D).pose());
        assertEquals(portal(keyframes, 2.0D).pose(), portal(keyframes, 8.0D).pose());
        assertEquals(portal(keyframes, 2.0D).pose(), portal(keyframes, 14.0D).pose());
        DemoScene motion = scene("eased-motion");
        assertEquals(portal(motion, 0.0D).pose(), portal(motion, 8.0D).pose());
        assertVector(portal(motion, 3.999D).pose().translation(), portal(motion, 4.001D).pose().translation());
        assertEquals(portal(motion, 4.0D).pose(), portal(motion, 12.0D).pose());
    }

    @Test
    void booleanMasksPreserveUnionOverlapSubtractionAndHole() {
        DemoScene scene = scene("boolean-masks");
        Shape union = portal(scene, 0.0D).aperture();
        Shape intersection = portal(scene, 4.0D).aperture();
        Shape difference = portal(scene, 8.0D).aperture();
        Shape ring = portal(scene, 12.0D).aperture();
        assertTrue(union.contains(-0.8D, 0.0D));
        assertTrue(union.contains(0.8D, 0.0D));
        assertTrue(intersection.contains(0.0D, 0.0D));
        assertFalse(intersection.contains(-0.8D, 0.0D));
        assertTrue(difference.contains(-0.8D, 0.0D));
        assertFalse(difference.contains(0.0D, 0.0D));
        assertFalse(ring.contains(0.0D, 0.0D));
        assertTrue(ring.contains(0.7D, 0.0D));
    }

    @Test
    void compatibleMorphChangesInteriorContinuouslyAndReverses() {
        DemoScene scene = scene("shape-morph");
        double angle = Math.toRadians(126.0D);
        double u = Math.cos(angle) * 0.6D;
        double v = Math.sin(angle) * 0.6D;
        assertFalse(portal(scene, 0.0D).aperture().contains(u, v));
        assertTrue(portal(scene, 4.0D).aperture().contains(u, v));
        assertEquals(portal(scene, 0.0D).aperture(), portal(scene, 8.0D).aperture());
        Shape before = portal(scene, 1.99D).aperture();
        Shape after = portal(scene, 2.01D).aperture();
        assertTrue(Math.abs(before.signedDistance(u, v) - after.signedDistance(u, v)) < 0.02D);
    }

    @Test
    void reflectedViewReversesLateralOrientationAndShearRoundTrips() {
        SceneFrame reflected = scene("mirror-transform").sample(0.0D);
        assertFalse(reflected.portals().get(0).viewTransform().reflects());
        assertTrue(reflected.portals().get(1).viewTransform().reflects());
        assertEquals(CrossingMode.MIRROR_VIEW, reflected.portals().get(1).crossingMode());
        assertVector(new Vec3d(-1.0D, 0.0D, 0.0D), reflected.portals().get(1).viewTransform().vector(new Vec3d(1.0D, 0.0D, 0.0D)));
        Affine shear = portal(scene("affine-shear"), 2.7D).viewTransform();
        Vec3d point = new Vec3d(0.3D, 1.7D, -4.0D);
        assertVector(point, shear.inverse().point(shear.point(point)));
    }

    @Test
    void linkedDefinitionMapsAnAnchorIntoItsDestination() {
        ScenePortal portal = portal(scene("linked-transform"), 0.0D);
        assertVector(new Vec3d(2.0D, 2.0D, -3.5D), portal.viewTransform().point(new Vec3d(-2.0D, 2.0D, 0.5D)));
        assertEquals(CrossingMode.RENDER_ONLY, portal.crossingMode());
        assertTrue(scene("linked-transform").crossingRoute().isEmpty());
    }

    @Test
    void exactCrossingAcceptsTheRingRejectsItsHoleAndMapsTheActualEndPoint() {
        DemoScene scene = scene("masked-crossing");
        CrossingRoute route = scene.crossingRoute().orElseThrow();
        Affine pose = route.source().affine();
        Vec3d start = pose.point(new Vec3d(0.65D, 0.0D, 1.0D));
        Vec3d end = pose.point(new Vec3d(0.65D, 0.0D, -1.0D));
        Vec3d velocity = end.subtract(start);
        PortalCrossing crossing = route.crossing(start, end, velocity, velocity);
        assertNotNull(crossing);
        assertEquals(0.5D, crossing.fraction(), EPSILON);
        assertVector(end.add(route.destination().origin().subtract(route.source().origin())), route.arrival(crossing));
        assertNull(route.crossing(pose.point(new Vec3d(0.0D, 0.0D, 1.0D)), pose.point(new Vec3d(0.0D, 0.0D, -1.0D)), velocity, velocity));
        assertNull(route.crossing(pose.point(new Vec3d(1.1D, 0.0D, 1.0D)), pose.point(new Vec3d(1.1D, 0.0D, -1.0D)), velocity, velocity));
        assertNull(route.crossing(start, start.add(new Vec3d(0.0D, 0.0D, 0.2D)), velocity, velocity));
        assertEquals(CrossingMode.BLOCK_ALIGNED, portal(scene, 0.0D).crossingMode());
        assertEquals("Open ring: crossing accepted", scene.sample(4.0D).label());
        assertEquals("Closed center: crossing rejected", scene.sample(10.0D).label());
    }

    private static DemoScene scene(String id) {
        return DemoScenes.find(id).orElseThrow();
    }

    private static ScenePortal portal(DemoScene scene, double seconds) {
        return scene.sample(seconds).portals().get(0);
    }

    private static void assertVector(Vec3d expected, Vec3d actual) {
        assertEquals(expected.x(), actual.x(), EPSILON);
        assertEquals(expected.y(), actual.y(), EPSILON);
        assertEquals(expected.z(), actual.z(), EPSILON);
    }
}
