package art.arcane.optics.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

final class TrackSampleTest {
    @Test
    void sampleClampsToTheKeyframeRange() {
        Track<Double> track = Track.of(Interpolators.doubles(), List.of(new Keyframe<Double>(1.0D, 5.0D), new Keyframe<Double>(3.0D, 9.0D)));
        assertEquals(3.0D, track.duration(), 0.0D);
        assertEquals(5.0D, track.sample(-10.0D), 0.0D);
        assertEquals(5.0D, track.sample(0.0D), 0.0D);
        assertEquals(5.0D, track.sample(1.0D), 0.0D);
        assertEquals(7.0D, track.sample(2.0D), 0.0D);
        assertEquals(9.0D, track.sample(3.0D), 0.0D);
        assertEquals(9.0D, track.sample(100.0D), 0.0D);
        assertEquals(5.0D, track.sample(Double.NaN), 0.0D);
    }

    @Test
    void eachSpanUsesTheEasingOfTheKeyframeItApproaches() {
        Track<Double> track = Track.of(Interpolators.doubles(), List.of(new Keyframe<Double>(0.0D, 0.0D, Easing.BOUNCE_OUT),
            new Keyframe<Double>(1.0D, 10.0D, Easing.QUAD_IN), new Keyframe<Double>(2.0D, 20.0D, Easing.QUAD_OUT)));
        assertEquals(2.5D, track.sample(0.5D), 1.0E-12D);
        assertEquals(17.5D, track.sample(1.5D), 1.0E-12D);
        assertEquals(10.0D, track.sample(1.0D), 0.0D);
        assertEquals(10.0D * 0.0625D, track.sample(0.25D), 1.0E-12D);
    }

    @Test
    void keyframesAreSortedAndExactAtTheirTimes() {
        Track<Double> track = Track.of(Interpolators.doubles(), List.of(new Keyframe<Double>(2.0D, 0.3D), new Keyframe<Double>(0.0D, 0.1D),
            new Keyframe<Double>(1.0D, 0.7D)));
        assertEquals(List.of(0.0D, 1.0D, 2.0D), times(track.keyframes()));
        assertEquals(0.1D, track.sample(0.0D), 0.0D);
        assertEquals(0.7D, track.sample(1.0D), 0.0D);
        assertEquals(0.3D, track.sample(2.0D), 0.0D);
        assertThrows(UnsupportedOperationException.class, () -> track.keyframes().add(new Keyframe<Double>(5.0D, 1.0D)));
    }

    @Test
    void invalidKeyframeSetsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Track.of(Interpolators.doubles(), List.<Keyframe<Double>>of()));
        assertThrows(IllegalArgumentException.class, () -> Track.of(Interpolators.doubles(), List.of(new Keyframe<Double>(1.0D, 1.0D),
            new Keyframe<Double>(1.0D, 2.0D))));
        assertThrows(IllegalArgumentException.class, () -> new Keyframe<Double>(-1.0D, 1.0D));
        assertThrows(IllegalArgumentException.class, () -> new Keyframe<Double>(Double.NaN, 1.0D));
        assertThrows(IllegalArgumentException.class, () -> new Keyframe<Double>(Double.POSITIVE_INFINITY, 1.0D));
        assertThrows(NullPointerException.class, () -> new Keyframe<Double>(0.0D, null));
        assertThrows(NullPointerException.class, () -> new Keyframe<Double>(0.0D, 1.0D, null));
    }

    @Test
    void defaultKeyframeEasingIsLinear() {
        assertSame(Easing.LINEAR, new Keyframe<String>(0.0D, "a").easing());
    }

    @Test
    void withAddsOrReplacesAKeyframe() {
        Track<Double> track = Track.of(Interpolators.doubles(), List.of(new Keyframe<Double>(0.0D, 0.0D), new Keyframe<Double>(2.0D, 2.0D)));
        Track<Double> extended = track.with(new Keyframe<Double>(4.0D, 0.0D));
        assertEquals(List.of(0.0D, 2.0D, 4.0D), times(extended.keyframes()));
        assertEquals(4.0D, extended.duration(), 0.0D);
        assertEquals(1.0D, extended.sample(3.0D), 1.0E-12D);
        Track<Double> replaced = track.with(new Keyframe<Double>(2.0D, 8.0D));
        assertEquals(List.of(0.0D, 2.0D), times(replaced.keyframes()));
        assertEquals(4.0D, replaced.sample(1.0D), 1.0E-12D);
        Track<Double> inserted = track.with(new Keyframe<Double>(1.0D, 5.0D));
        assertEquals(5.0D, inserted.sample(1.0D), 0.0D);
        assertEquals(2.0D, track.sample(2.0D), 0.0D);
        assertEquals(2, track.keyframes().size());
    }

    @Test
    void singleKeyframeTracksHoldTheirValue() {
        Track<String> track = Track.of(Interpolators.step(), List.of(new Keyframe<String>(0.0D, "only")));
        assertEquals(0.0D, track.duration(), 0.0D);
        assertEquals("only", track.sample(0.0D));
        assertEquals("only", track.sample(5.0D));
    }

    private static List<Double> times(List<Keyframe<Double>> keyframes) {
        List<Double> times = new ArrayList<Double>(keyframes.size());
        for (Keyframe<Double> keyframe : keyframes) {
            times.add(keyframe.time());
        }
        return times;
    }
}
