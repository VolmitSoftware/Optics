package art.arcane.optics.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import art.arcane.optics.animation.Clip;
import art.arcane.optics.animation.Easing;
import art.arcane.optics.animation.Interpolators;
import art.arcane.optics.animation.Timeline;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Rgba;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.shape.ShapeDescriptor;
import art.arcane.optics.transform.Affine;

final class PortalTracksTest {
    @Test
    void openingAndClosingRunBetweenZeroAndOne() {
        Clip<Double> opening = PortalTracks.opening(0.5D, Easing.CUBIC_OUT);
        Clip<Double> closing = PortalTracks.closing(2.0D, Easing.LINEAR);

        assertEquals(0.5D, opening.duration(), 0.0D);
        assertEquals(0.0D, opening.sample(0.0D), 0.0D);
        assertEquals(1.0D, opening.sample(0.5D), 0.0D);
        assertEquals(1.0D, opening.sample(9.0D), 0.0D);
        assertEquals(Easing.CUBIC_OUT.apply(0.5D), opening.sample(0.25D), 1.0E-12D);
        assertEquals(1.0D, closing.sample(0.0D), 0.0D);
        assertEquals(0.75D, closing.sample(0.5D), 1.0E-12D);
        assertEquals(0.0D, closing.sample(2.0D), 0.0D);
    }

    @Test
    void morphTweensBetweenShapes() {
        ShapeDescriptor from = ShapeDescriptor.parse("circle(radius=0.5)");
        ShapeDescriptor to = ShapeDescriptor.parse("circle(radius=1)");

        Clip<ShapeDescriptor> morph = PortalTracks.morph(from, to, 1.0D, Easing.LINEAR);

        assertEquals(1.0D, morph.duration(), 0.0D);
        assertEquals(from, morph.sample(0.0D));
        assertEquals(to, morph.sample(1.0D));
        assertEquals(Interpolators.shapes().interpolate(from, to, 0.5D), morph.sample(0.5D));
    }

    @Test
    void spinRotatesEndlesslyAboutTheLocalAxis() {
        Clip<Affine> spin = PortalTracks.spin(Vec3d.UNIT_Z, 90.0D);

        assertTrue(spin.endless());
        assertClose(Vec3d.UNIT_X, spin.sample(0.0D).point(Vec3d.UNIT_X));
        assertClose(Vec3d.UNIT_Y, spin.sample(1.0D).point(Vec3d.UNIT_X));
        assertClose(Vec3d.UNIT_X.negate(), spin.sample(2.0D).point(Vec3d.UNIT_X));
        assertClose(Vec3d.UNIT_X, spin.sample(4.0D).point(Vec3d.UNIT_X));
        assertClose(Vec3d.UNIT_Y, spin.sample(1.0E9D + 1.0D).point(Vec3d.UNIT_X));
        assertClose(Vec3d.UNIT_X, spin.sample(-3.0D).point(Vec3d.UNIT_X));
        assertClose(Vec3d.ZERO, spin.sample(1.5D).point(Vec3d.ZERO));
        assertClose(Vec3d.UNIT_Y.negate(), PortalTracks.spin(new Vec3d(0.0D, 0.0D, 3.0D), -90.0D).sample(1.0D).point(Vec3d.UNIT_X));
        assertThrows(IllegalArgumentException.class, () -> PortalTracks.spin(Vec3d.ZERO, 90.0D));
        assertThrows(IllegalArgumentException.class, () -> PortalTracks.spin(Vec3d.UNIT_Y, Double.POSITIVE_INFINITY));
    }

    @Test
    void spinComposesOnTopOfThePortalAffine() {
        PortalDefinition portal = PortalFixtures.wall(Face.N, 0, 64, 0, 4, 4);
        Affine local = PortalTracks.spin(Vec3d.UNIT_Z, 90.0D).sample(1.0D);

        Affine world = portal.affine().compose(local);

        assertClose(portal.origin(), world.point(Vec3d.ZERO));
        assertClose(portal.affine().point(Vec3d.UNIT_Y), world.point(Vec3d.UNIT_X));
    }

    @Test
    void trackKeysAreDistinctAndTyped() {
        List<Timeline.TrackKey<?>> keys = List.of(PortalTracks.TRANSFORM, PortalTracks.SHAPE, PortalTracks.TINT, PortalTracks.OPENING,
            PortalTracks.EDGE_SOFTNESS);
        Set<String> names = new HashSet<String>();
        for (Timeline.TrackKey<?> key : keys) {
            names.add(key.name());
        }

        assertEquals(5, names.size());
        assertEquals(Affine.class, PortalTracks.TRANSFORM.type());
        assertEquals(ShapeDescriptor.class, PortalTracks.SHAPE.type());
        assertEquals(Rgba.class, PortalTracks.TINT.type());
        assertEquals(Double.class, PortalTracks.OPENING.type());
        assertEquals(Double.class, PortalTracks.EDGE_SOFTNESS.type());
    }

    @Test
    void tracksPlugIntoATimeline() {
        Timeline timeline = Timeline.builder()
            .track(PortalTracks.OPENING, PortalTracks.opening(1.0D, Easing.LINEAR))
            .track(PortalTracks.TRANSFORM, PortalTracks.spin(Vec3d.UNIT_Z, 45.0D))
            .marker("open", 1.0D)
            .build();

        assertTrue(timeline.endless());
        assertEquals(0.5D, timeline.sample(PortalTracks.OPENING, 0.5D), 1.0E-12D);
        assertClose(Vec3d.UNIT_Y, timeline.sample(PortalTracks.TRANSFORM, 2.0D).point(Vec3d.UNIT_X));
        assertFalse(timeline.keys().contains(PortalTracks.SHAPE));
    }

    private static void assertClose(Vec3d expected, Vec3d actual) {
        assertEquals(0.0D, expected.distance(actual), 1.0E-9D, expected + " vs " + actual);
    }
}
