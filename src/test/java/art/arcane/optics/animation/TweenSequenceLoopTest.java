package art.arcane.optics.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

final class TweenSequenceLoopTest {
    @Test
    void tweenEasesBetweenItsEndpoints() {
        Tween<Double> tween = Tween.of(10.0D, 20.0D, 2.0D, Easing.QUAD_IN);
        assertEquals(2.0D, tween.duration(), 0.0D);
        assertEquals(10.0D, tween.sample(0.0D), 0.0D);
        assertEquals(12.5D, tween.sample(1.0D), 1.0E-12D);
        assertEquals(20.0D, tween.sample(2.0D), 0.0D);
        assertEquals(20.0D, tween.sample(9.0D), 0.0D);
        assertEquals(10.0D, tween.sample(-1.0D), 0.0D);
        assertEquals(10.0D, tween.from(), 0.0D);
        assertEquals(20.0D, tween.to(), 0.0D);
        assertSame(Easing.QUAD_IN, tween.easing());
        assertFalse(tween.endless());
        Tween<String> instant = Tween.of("a", "b", 0.0D, Easing.LINEAR, Interpolators.step());
        assertEquals("b", instant.sample(0.0D));
        assertThrows(IllegalArgumentException.class, () -> Tween.of(0.0D, 1.0D, -1.0D, Easing.LINEAR));
        assertThrows(IllegalArgumentException.class, () -> Tween.of(0.0D, 1.0D, Double.POSITIVE_INFINITY, Easing.LINEAR));
        assertThrows(IllegalArgumentException.class, () -> Tween.of(0.0D, 1.0D, Double.NaN, Easing.LINEAR));
    }

    @Test
    void constantHoldsItsValue() {
        Constant<String> constant = Constant.of("x", 3.0D);
        assertEquals(3.0D, constant.duration(), 0.0D);
        assertEquals("x", constant.sample(-1.0D));
        assertEquals("x", constant.sample(100.0D));
        assertTrue(Constant.of("x", Double.POSITIVE_INFINITY).endless());
        assertThrows(IllegalArgumentException.class, () -> Constant.of("x", -1.0D));
        assertThrows(IllegalArgumentException.class, () -> Constant.of("x", Double.NaN));
    }

    @Test
    void sequenceDurationsComposeAndBoundariesBelongToTheNextClip() {
        Sequence<Double> sequence = Sequence.of(List.<Clip<Double>>of(Tween.of(0.0D, 1.0D, 1.0D, Easing.LINEAR), Constant.of(5.0D, 0.5D),
            Tween.of(10.0D, 20.0D, 2.0D, Easing.LINEAR)));
        assertEquals(3.5D, sequence.duration(), 0.0D);
        assertEquals(3, sequence.clips().size());
        assertEquals(0.5D, sequence.sample(0.5D), 1.0E-12D);
        assertEquals(5.0D, sequence.sample(1.0D), 0.0D);
        assertEquals(5.0D, sequence.sample(1.25D), 0.0D);
        assertEquals(10.0D, sequence.sample(1.5D), 0.0D);
        assertEquals(15.0D, sequence.sample(2.5D), 1.0E-12D);
        assertEquals(20.0D, sequence.sample(3.5D), 0.0D);
        assertEquals(20.0D, sequence.sample(50.0D), 0.0D);
        assertEquals(0.0D, sequence.sample(-5.0D), 0.0D);
    }

    @Test
    void onlyTheLastClipOfASequenceMayBeEndless() {
        Clip<Double> endless = Constant.of(1.0D, Double.POSITIVE_INFINITY);
        Sequence<Double> tail = Sequence.of(List.<Clip<Double>>of(Tween.of(0.0D, 1.0D, 1.0D, Easing.LINEAR), endless));
        assertTrue(tail.endless());
        assertEquals(1.0D, tail.sample(1.0E9D), 0.0D);
        assertEquals(0.5D, tail.sample(0.5D), 1.0E-12D);
        assertThrows(IllegalArgumentException.class, () -> Sequence.of(List.<Clip<Double>>of(endless, Tween.of(0.0D, 1.0D, 1.0D, Easing.LINEAR))));
        assertThrows(IllegalArgumentException.class, () -> Sequence.of(List.<Clip<Double>>of()));
    }

    @Test
    void loopRepeatsAndPingPongReflectsOddCycles() {
        Tween<Double> ramp = Tween.of(0.0D, 10.0D, 2.0D, Easing.LINEAR);
        Loop<Double> loop = Loop.of(ramp, 3, false);
        assertEquals(6.0D, loop.duration(), 0.0D);
        assertEquals(3, loop.cycles());
        assertFalse(loop.pingPong());
        assertSame(ramp, loop.clip());
        assertEquals(5.0D, loop.sample(1.0D), 1.0E-12D);
        assertEquals(0.0D, loop.sample(2.0D), 0.0D);
        assertEquals(5.0D, loop.sample(3.0D), 1.0E-12D);
        assertEquals(10.0D, loop.sample(6.0D), 0.0D);
        Loop<Double> pingPong = Loop.of(ramp, 2, true);
        assertEquals(4.0D, pingPong.duration(), 0.0D);
        assertEquals(2.5D, pingPong.sample(0.5D), 1.0E-12D);
        assertEquals(10.0D, pingPong.sample(2.0D), 0.0D);
        assertEquals(7.5D, pingPong.sample(2.5D), 1.0E-12D);
        assertEquals(0.0D, pingPong.sample(4.0D), 0.0D);
        assertEquals(10.0D, Loop.of(ramp, 3, true).sample(6.0D), 0.0D);
    }

    @Test
    void loopsWithoutACycleCountAreEndless() {
        Loop<Double> endless = Loop.of(Tween.of(0.0D, 10.0D, 2.0D, Easing.LINEAR), 0, true);
        assertTrue(endless.endless());
        assertEquals(Double.POSITIVE_INFINITY, endless.duration(), 0.0D);
        assertEquals(5.0D, endless.sample(1001.0D), 1.0E-9D);
        assertEquals(7.5D, endless.sample(1002.5D), 1.0E-9D);
        assertTrue(Loop.of(Tween.of(0.0D, 10.0D, 2.0D, Easing.LINEAR), -4, false).endless());
        assertThrows(IllegalArgumentException.class, () -> Loop.of(Constant.of(1.0D, Double.POSITIVE_INFINITY), 2, false));
        assertThrows(IllegalArgumentException.class, () -> Loop.of(Constant.of(1.0D, 0.0D), 2, false));
    }

    @Test
    void clipDefaultsComposeTheConcreteClips() {
        Tween<Double> ramp = Tween.of(0.0D, 10.0D, 2.0D, Easing.LINEAR);
        Clip<Double> delayed = ramp.delayed(1.0D);
        assertEquals(3.0D, delayed.duration(), 0.0D);
        assertEquals(0.0D, delayed.sample(0.5D), 0.0D);
        assertEquals(5.0D, delayed.sample(2.0D), 1.0E-12D);
        assertSame(ramp, ramp.delayed(0.0D));
        Clip<Double> fast = ramp.speed(2.0D);
        assertEquals(1.0D, fast.duration(), 0.0D);
        assertEquals(5.0D, fast.sample(0.5D), 1.0E-12D);
        assertEquals(10.0D, fast.sample(1.0D), 0.0D);
        Clip<Double> slow = ramp.speed(0.5D);
        assertEquals(4.0D, slow.duration(), 0.0D);
        assertEquals(2.5D, slow.sample(1.0D), 1.0E-12D);
        assertThrows(IllegalArgumentException.class, () -> ramp.speed(0.0D));
        assertThrows(IllegalArgumentException.class, () -> ramp.speed(-1.0D));
        Clip<Double> reversed = ramp.reversed();
        assertEquals(2.0D, reversed.duration(), 0.0D);
        assertEquals(10.0D, reversed.sample(0.0D), 0.0D);
        assertEquals(7.5D, reversed.sample(0.5D), 1.0E-12D);
        assertEquals(0.0D, reversed.sample(2.0D), 0.0D);
        assertThrows(IllegalArgumentException.class, () -> Constant.of(1.0D, Double.POSITIVE_INFINITY).reversed());
        Clip<Double> looped = ramp.looped(2, true);
        assertEquals(4.0D, looped.duration(), 0.0D);
        assertEquals(7.5D, looped.sample(2.5D), 1.0E-12D);
        Clip<Double> chained = ramp.then(Tween.of(10.0D, 0.0D, 1.0D, Easing.LINEAR));
        assertEquals(3.0D, chained.duration(), 0.0D);
        assertEquals(5.0D, chained.sample(2.5D), 1.0E-12D);
        assertTrue(ramp.then(Constant.of(10.0D, Double.POSITIVE_INFINITY)).endless());
    }
}
