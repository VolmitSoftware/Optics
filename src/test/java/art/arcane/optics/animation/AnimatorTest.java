package art.arcane.optics.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.sun.management.ThreadMXBean;

final class AnimatorTest {
    private static final Timeline.TrackKey<Double> OPEN = new Timeline.TrackKey<Double>("open", Double.class);

    @Test
    void playPauseAndSeekMoveTimeOnlyWhilePlaying() {
        double[] clock = {100.0D};
        Animator animator = new Animator(ramp(), TimeSource.of(() -> clock[0]));
        List<Marker> fired = new ArrayList<Marker>();
        assertSame(Animator.State.STOPPED, animator.state());
        clock[0] = 101.0D;
        assertEquals(0, animator.advance(fired));
        assertEquals(0.0D, animator.time(), 0.0D);
        animator.play();
        assertSame(Animator.State.PLAYING, animator.state());
        clock[0] = 101.5D;
        animator.advance(fired);
        assertEquals(0.5D, animator.time(), 1.0E-12D);
        assertEquals(2.5D, animator.value(OPEN), 1.0E-12D);
        animator.pause();
        assertSame(Animator.State.PAUSED, animator.state());
        clock[0] = 102.5D;
        assertEquals(0, animator.advance(fired));
        assertEquals(0.5D, animator.time(), 1.0E-12D);
        animator.play();
        clock[0] = 103.0D;
        animator.advance(fired);
        assertEquals(1.0D, animator.time(), 1.0E-12D);
        animator.seek(1.8D);
        assertEquals(1.8D, animator.time(), 0.0D);
        clock[0] = 104.0D;
        animator.advance(fired);
        assertEquals(2.0D, animator.time(), 0.0D);
        assertTrue(animator.finished());
        assertSame(Animator.State.FINISHED, animator.state());
        assertEquals(10.0D, animator.value(OPEN), 0.0D);
        animator.seek(-5.0D);
        assertEquals(0.0D, animator.time(), 0.0D);
        assertSame(Animator.State.PAUSED, animator.state());
        animator.seek(50.0D);
        assertEquals(2.0D, animator.time(), 0.0D);
        assertThrows(IllegalArgumentException.class, () -> animator.seek(Double.NaN));
    }

    @Test
    void stopRewindsAndPlayAfterFinishRestarts() {
        double[] clock = {0.0D};
        Animator animator = new Animator(ramp(), TimeSource.of(() -> clock[0]));
        List<Marker> fired = new ArrayList<Marker>();
        animator.play();
        clock[0] = 5.0D;
        animator.advance(fired);
        assertTrue(animator.finished());
        animator.play();
        assertSame(Animator.State.PLAYING, animator.state());
        assertEquals(0.0D, animator.time(), 0.0D);
        clock[0] = 6.0D;
        animator.advance(fired);
        assertEquals(1.0D, animator.time(), 1.0E-12D);
        animator.stop();
        assertSame(Animator.State.STOPPED, animator.state());
        assertEquals(0.0D, animator.time(), 0.0D);
        assertSame(ramp().getClass(), animator.timeline().getClass());
    }

    @Test
    void speedScalesTheSourceDeltaAndNegativeSpeedPlaysBackwards() {
        double[] clock = {0.0D};
        Animator animator = new Animator(ramp(), TimeSource.of(() -> clock[0]));
        List<Marker> fired = new ArrayList<Marker>();
        animator.speed(2.0D);
        assertEquals(2.0D, animator.speed(), 0.0D);
        animator.play();
        clock[0] = 0.25D;
        animator.advance(fired);
        assertEquals(0.5D, animator.time(), 1.0E-12D);
        animator.stop();
        animator.speed(-1.0D);
        animator.play();
        assertEquals(2.0D, animator.time(), 0.0D);
        clock[0] = 0.75D;
        animator.advance(fired);
        assertEquals(1.5D, animator.time(), 1.0E-12D);
        assertEquals(7.5D, animator.value(OPEN), 1.0E-12D);
        clock[0] = 3.0D;
        animator.advance(fired);
        assertEquals(0.0D, animator.time(), 0.0D);
        assertTrue(animator.finished());
        assertThrows(IllegalArgumentException.class, () -> animator.speed(Double.POSITIVE_INFINITY));
    }

    @Test
    void markersFireInOrderOncePerPassAndLoopsReFireThem() {
        Timeline timeline = Timeline.builder().track(OPEN, Tween.of(0.0D, 10.0D, 1.0D, Easing.LINEAR)).marker("start", 0.0D).marker("a", 0.25D)
            .marker("end", 1.0D).build();
        double[] clock = {0.0D};
        Animator animator = new Animator(timeline, TimeSource.of(() -> clock[0]));
        animator.loop(3, false);
        List<Marker> fired = new ArrayList<Marker>();
        animator.play();
        clock[0] = 2.5D;
        assertEquals(8, animator.advance(fired));
        assertEquals(List.of("start", "a", "end", "start", "a", "end", "start", "a"), names(fired));
        assertEquals(0.5D, animator.time(), 1.0E-12D);
        assertSame(Animator.State.PLAYING, animator.state());
        fired.clear();
        clock[0] = 3.5D;
        assertEquals(1, animator.advance(fired));
        assertEquals(List.of("end"), names(fired));
        assertTrue(animator.finished());
        assertEquals(1.0D, animator.time(), 0.0D);
    }

    @Test
    void pingPongTurnsAtTheEndsWithoutReFiringTheTurningMarker() {
        Timeline timeline = Timeline.builder().track(OPEN, Tween.of(0.0D, 10.0D, 1.0D, Easing.LINEAR)).marker("a", 0.25D).marker("end", 1.0D).build();
        double[] clock = {0.0D};
        Animator animator = new Animator(timeline, TimeSource.of(() -> clock[0]));
        animator.loop(2, true);
        List<Marker> fired = new ArrayList<Marker>();
        animator.play();
        clock[0] = 1.5D;
        assertEquals(2, animator.advance(fired));
        assertEquals(List.of("a", "end"), names(fired));
        assertEquals(0.5D, animator.time(), 1.0E-12D);
        fired.clear();
        clock[0] = 2.5D;
        assertEquals(1, animator.advance(fired));
        assertEquals(List.of("a"), names(fired));
        assertEquals(0.0D, animator.time(), 0.0D);
        assertTrue(animator.finished());
    }

    @Test
    void backwardsPlaybackFiresMarkersInDescendingOrder() {
        Timeline timeline = Timeline.builder().track(OPEN, Tween.of(0.0D, 10.0D, 1.0D, Easing.LINEAR)).marker("a", 0.25D).marker("b", 0.5D)
            .marker("end", 1.0D).build();
        double[] clock = {0.0D};
        Animator animator = new Animator(timeline, TimeSource.of(() -> clock[0]));
        List<Marker> fired = new ArrayList<Marker>();
        animator.speed(-1.0D);
        animator.play();
        clock[0] = 0.6D;
        assertEquals(2, animator.advance(fired));
        assertEquals(List.of("end", "b"), names(fired));
        fired.clear();
        clock[0] = 2.0D;
        assertEquals(1, animator.advance(fired));
        assertEquals(List.of("a"), names(fired));
        assertTrue(animator.finished());
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void endlessLoopsNeverFinishAndHugeDeltasStayCheap() {
        Timeline timeline = Timeline.builder().track(OPEN, Tween.of(0.0D, 1.0D, 0.001D, Easing.LINEAR)).build();
        double[] clock = {0.0D};
        Animator animator = new Animator(timeline, TimeSource.of(() -> clock[0]));
        List<Marker> fired = new ArrayList<Marker>();
        animator.loop(-1, false);
        animator.play();
        clock[0] = 1.0E9D + 0.0005D;
        assertEquals(0, animator.advance(fired));
        assertSame(Animator.State.PLAYING, animator.state());
        assertTrue(animator.time() >= 0.0D && animator.time() <= 0.001D, "time " + animator.time());
        animator.loop(5, true);
        clock[0] = 2.0E9D;
        animator.advance(fired);
        assertTrue(animator.finished());
        Animator bounded = new Animator(timeline, TimeSource.of(() -> clock[0]));
        bounded.loop(4, true);
        bounded.play();
        clock[0] = 3.0E9D;
        bounded.advance(fired);
        assertTrue(bounded.finished());
        assertEquals(0.0D, bounded.time(), 0.0D);
    }

    @Test
    void endlessTimelinesKeepPlaying() {
        Timeline timeline = Timeline.builder().track(OPEN, Constant.of(1.0D, Double.POSITIVE_INFINITY)).marker("later", 50.0D).build();
        double[] clock = {0.0D};
        Animator animator = new Animator(timeline, TimeSource.of(() -> clock[0]));
        List<Marker> fired = new ArrayList<Marker>();
        animator.play();
        clock[0] = 100.0D;
        assertEquals(1, animator.advance(fired));
        assertEquals(100.0D, animator.time(), 0.0D);
        assertFalse(animator.finished());
        assertEquals(1.0D, animator.value(OPEN), 0.0D);
    }

    @Test
    void tickAndNanoSourcesConvertToSeconds() {
        long[] ticks = {0L};
        Animator animator = new Animator(ramp(), TimeSource.ticks(() -> ticks[0], 0.05D));
        List<Marker> fired = new ArrayList<Marker>();
        animator.play();
        ticks[0] = 20L;
        animator.advance(fired);
        assertEquals(1.0D, animator.time(), 1.0E-12D);
        assertEquals(5.0D, animator.value(OPEN), 1.0E-12D);
        long[] nanos = {7_000_000_000L};
        TimeSource nanoSource = TimeSource.nanos(() -> nanos[0]);
        assertEquals(7.0D, nanoSource.seconds(), 0.0D);
        nanos[0] = 8_500_000_000L;
        assertEquals(8.5D, nanoSource.seconds(), 0.0D);
        assertThrows(IllegalArgumentException.class, () -> TimeSource.ticks(() -> 0L, 0.0D));
        assertThrows(IllegalArgumentException.class, () -> TimeSource.ticks(() -> 0L, Double.NaN));
    }

    @Test
    void playAnchorsTheSourceSoTimeBeforePlayIsIgnored() {
        double[] clock = {500.0D};
        Animator animator = new Animator(ramp(), TimeSource.of(() -> clock[0]));
        clock[0] = 900.0D;
        animator.play();
        clock[0] = 900.25D;
        animator.advance(new ArrayList<Marker>());
        assertEquals(0.25D, animator.time(), 1.0E-9D);
    }

    @Test
    void advanceAllocatesNothingWhenNoMarkerFires() {
        Timeline timeline = Timeline.builder().track(OPEN, Tween.of(0.0D, 1.0D, 1.0E12D, Easing.LINEAR)).marker("far", 9.0E11D).build();
        long[] ticks = {0L};
        Animator animator = new Animator(timeline, TimeSource.ticks(() -> ticks[0], 0.05D));
        List<Marker> fired = new ArrayList<Marker>(4);
        animator.play();
        int count = 0;
        for (int warm = 0; warm < 20_000; warm++) {
            ticks[0]++;
            count += animator.advance(fired);
        }
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int call = 0; call < 100_000; call++) {
            ticks[0]++;
            count += animator.advance(fired);
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        assertEquals(0, count);
        assertTrue(allocated < 1_024L, allocated + " bytes allocated by advance");
    }

    private static Timeline ramp() {
        return Timeline.builder().track(OPEN, Tween.of(0.0D, 10.0D, 2.0D, Easing.LINEAR)).build();
    }

    private static List<String> names(List<Marker> markers) {
        List<String> names = new ArrayList<String>(markers.size());
        for (Marker marker : markers) {
            names.add(marker.name());
        }
        return names;
    }
}
