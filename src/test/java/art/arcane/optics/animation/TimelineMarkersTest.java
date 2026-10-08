package art.arcane.optics.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sun.management.ThreadMXBean;

final class TimelineMarkersTest {
    private static final Timeline.TrackKey<Double> OPEN = new Timeline.TrackKey<Double>("open", Double.class);
    private static final Timeline.TrackKey<String> LABEL = new Timeline.TrackKey<String>("label", String.class);

    @Test
    void markersAreAscendingWithStableTies() {
        Timeline timeline = Timeline.builder().marker("late", 3.0D).marker("first", 1.0D).marker("second", 1.0D).marker("start", 0.0D).build();
        assertEquals(List.of("start", "first", "second", "late"), names(timeline.markers()));
        assertThrows(UnsupportedOperationException.class, () -> timeline.markers().add(new Marker("x", 1.0D)));
    }

    @Test
    void markersBetweenIsExclusiveAtTheStartAndInclusiveAtTheEnd() {
        Timeline timeline = Timeline.builder().marker("a", 0.0D).marker("b", 1.0D).marker("c", 2.0D).marker("d", 2.0D).marker("e", 3.5D).build();
        List<Marker> out = new ArrayList<Marker>();
        assertEquals(1, timeline.markersBetween(0.0D, 1.0D, collect(out, "seed")));
        assertEquals(List.of("seed", "b"), names(out));
        out.clear();
        assertEquals(3, timeline.markersBetween(1.0D, 3.5D, out));
        assertEquals(List.of("c", "d", "e"), names(out));
        out.clear();
        assertEquals(1, timeline.markersBetween(-1.0D, 0.0D, out));
        assertEquals(List.of("a"), names(out));
        out.clear();
        assertEquals(0, timeline.markersBetween(2.0D, 3.0D, out));
        assertEquals(0, timeline.markersBetween(3.5D, 10.0D, out));
        assertEquals(0, timeline.markersBetween(2.0D, 2.0D, out));
        assertEquals(0, timeline.markersBetween(3.0D, 1.0D, out));
        assertTrue(out.isEmpty());
        assertEquals(5, timeline.markersBetween(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, out));
    }

    @Test
    void markersBetweenAllocatesNothingWhenNothingFires() {
        Timeline timeline = Timeline.builder().marker("a", 1.0D).marker("b", 2.0D).build();
        List<Marker> out = new ArrayList<Marker>(4);
        int fired = 0;
        for (int warm = 0; warm < 20_000; warm++) {
            fired += timeline.markersBetween(1.25D, 1.75D, out);
        }
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int call = 0; call < 100_000; call++) {
            fired += timeline.markersBetween(1.25D, 1.75D, out);
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        assertEquals(0, fired);
        assertTrue(allocated < 1_024L, allocated + " bytes allocated by markersBetween");
    }

    @Test
    void durationCoversTracksAndMarkers() {
        Timeline tracksOnly = Timeline.builder().track(OPEN, Tween.of(0.0D, 1.0D, 2.0D, Easing.LINEAR)).track(LABEL, Constant.of("x", 0.5D)).build();
        assertEquals(2.0D, tracksOnly.duration(), 0.0D);
        assertFalse(tracksOnly.endless());
        Timeline withLateMarker = Timeline.builder().track(OPEN, Tween.of(0.0D, 1.0D, 2.0D, Easing.LINEAR)).marker("late", 5.0D).build();
        assertEquals(5.0D, withLateMarker.duration(), 0.0D);
        Timeline endless = Timeline.builder().track(LABEL, Constant.of("x", Double.POSITIVE_INFINITY)).build();
        assertTrue(endless.endless());
        assertEquals(0.0D, Timeline.builder().build().duration(), 0.0D);
    }

    @Test
    void tracksAreLookedUpByKey() {
        Tween<Double> open = Tween.of(0.0D, 1.0D, 2.0D, Easing.LINEAR);
        Timeline timeline = Timeline.builder().track(LABEL, Constant.of("x", 1.0D)).track(OPEN, open).build();
        assertEquals(List.of(LABEL, OPEN), new ArrayList<Timeline.TrackKey<?>>(timeline.keys()));
        assertSame(open, timeline.track(OPEN));
        assertEquals(0.5D, timeline.sample(OPEN, 1.0D), 1.0E-12D);
        assertEquals("x", timeline.sample(LABEL, 0.0D));
        Timeline.TrackKey<Double> missing = new Timeline.TrackKey<Double>("missing", Double.class);
        assertNull(timeline.track(missing));
        assertNull(timeline.sample(missing, 1.0D));
        assertEquals(OPEN, new Timeline.TrackKey<Double>("open", Double.class));
        assertThrows(UnsupportedOperationException.class, () -> timeline.keys().clear());
    }

    @Test
    void builderRejectsDuplicateKeysAndInvalidMarkers() {
        Timeline.Builder builder = Timeline.builder().track(OPEN, Tween.of(0.0D, 1.0D, 1.0D, Easing.LINEAR));
        assertThrows(IllegalArgumentException.class, () -> builder.track(OPEN, Constant.of(1.0D, 1.0D)));
        assertThrows(IllegalArgumentException.class, () -> builder.marker("negative", -1.0D));
        assertThrows(IllegalArgumentException.class, () -> builder.marker("nan", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Marker("infinite", Double.POSITIVE_INFINITY));
        assertThrows(NullPointerException.class, () -> new Marker(null, 1.0D));
        assertThrows(NullPointerException.class, () -> builder.track(null, Constant.of(1.0D, 1.0D)));
        assertThrows(NullPointerException.class, () -> new Timeline.TrackKey<Double>(null, Double.class));
    }

    private static List<Marker> collect(List<Marker> out, String seed) {
        out.add(new Marker(seed, 0.0D));
        return out;
    }

    private static List<String> names(List<Marker> markers) {
        List<String> names = new ArrayList<String>(markers.size());
        for (Marker marker : markers) {
            names.add(marker.name());
        }
        return names;
    }
}
