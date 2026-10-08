package art.arcane.optics.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

final class EasingTest {
    private static final int GRID = 1_000;
    private static final Set<Easing> OVERSHOOTING = EnumSet.of(Easing.BACK_IN, Easing.BACK_OUT, Easing.BACK_IN_OUT, Easing.ELASTIC_IN,
        Easing.ELASTIC_OUT, Easing.ELASTIC_IN_OUT);
    private static final Set<Easing> NON_MONOTONE = EnumSet.of(Easing.BOUNCE_IN, Easing.BOUNCE_OUT, Easing.BOUNCE_IN_OUT);

    @Test
    void everyEasingMapsZeroToZeroAndOneToOne() {
        for (Easing easing : Easing.values()) {
            assertEquals(0.0D, easing.apply(0.0D), 1.0E-12D, easing.name());
            assertEquals(1.0D, easing.apply(1.0D), 1.0E-12D, easing.name());
        }
    }

    @Test
    void nonOvershootingEasingsAreMonotoneAndStayInRange() {
        for (Easing easing : Easing.values()) {
            if (OVERSHOOTING.contains(easing)) {
                continue;
            }
            double previous = easing.apply(0.0D);
            for (int step = 1; step <= GRID; step++) {
                double value = easing.apply(step / (double) GRID);
                assertTrue(value >= -1.0E-12D && value <= 1.0D + 1.0E-12D, easing + " left [0, 1] at " + step + ": " + value);
                if (!NON_MONOTONE.contains(easing)) {
                    assertTrue(value >= previous - 1.0E-12D, easing + " decreased at " + step + ": " + previous + " -> " + value);
                }
                previous = value;
            }
        }
    }

    @Test
    void overshootingEasingsLeaveTheUnitRange() {
        assertTrue(minimum(Easing.BACK_IN) < -0.05D);
        assertTrue(maximum(Easing.BACK_OUT) > 1.05D);
        assertTrue(minimum(Easing.ELASTIC_IN) < -0.05D);
        assertTrue(maximum(Easing.ELASTIC_OUT) > 1.05D);
        assertTrue(maximum(Easing.BACK_IN_OUT) > 1.05D && minimum(Easing.BACK_IN_OUT) < -0.05D);
    }

    @Test
    void outEasingsAreTheReversedInEasings() {
        Easing[][] pairs = {{Easing.SINE_IN, Easing.SINE_OUT}, {Easing.QUAD_IN, Easing.QUAD_OUT}, {Easing.CUBIC_IN, Easing.CUBIC_OUT},
            {Easing.QUART_IN, Easing.QUART_OUT}, {Easing.QUINT_IN, Easing.QUINT_OUT}, {Easing.EXPO_IN, Easing.EXPO_OUT},
            {Easing.CIRC_IN, Easing.CIRC_OUT}, {Easing.BACK_IN, Easing.BACK_OUT}, {Easing.ELASTIC_IN, Easing.ELASTIC_OUT},
            {Easing.BOUNCE_IN, Easing.BOUNCE_OUT}};
        for (Easing[] pair : pairs) {
            EasingFunction reversed = pair[0].reversed();
            for (int step = 0; step <= GRID; step++) {
                double t = step / (double) GRID;
                assertEquals(pair[1].apply(t), reversed.apply(t), 1.0E-9D, pair[1] + " at " + t);
            }
        }
    }

    @Test
    void inOutEasingsAreTheMirroredInEasings() {
        Easing[][] pairs = {{Easing.SINE_IN, Easing.SINE_IN_OUT}, {Easing.QUAD_IN, Easing.QUAD_IN_OUT}, {Easing.CUBIC_IN, Easing.CUBIC_IN_OUT},
            {Easing.QUART_IN, Easing.QUART_IN_OUT}, {Easing.QUINT_IN, Easing.QUINT_IN_OUT}, {Easing.EXPO_IN, Easing.EXPO_IN_OUT},
            {Easing.CIRC_IN, Easing.CIRC_IN_OUT}, {Easing.BOUNCE_IN, Easing.BOUNCE_IN_OUT}};
        for (Easing[] pair : pairs) {
            EasingFunction mirrored = pair[0].mirrored();
            for (int step = 0; step <= GRID; step++) {
                double t = step / (double) GRID;
                assertEquals(pair[1].apply(t), mirrored.apply(t), 1.0E-9D, pair[1] + " at " + t);
            }
        }
    }

    @Test
    void knownValuesMatchTheClosedForms() {
        assertEquals(0.5D, Easing.LINEAR.apply(0.5D), 0.0D);
        assertEquals(0.15625D, Easing.SMOOTH_STEP.apply(0.25D), 1.0E-15D);
        assertEquals(0.5D, Easing.SMOOTHER_STEP.apply(0.5D), 1.0E-15D);
        assertEquals(0.103515625D, Easing.SMOOTHER_STEP.apply(0.25D), 1.0E-15D);
        assertEquals(0.0625D, Easing.QUAD_IN.apply(0.25D), 1.0E-15D);
        assertEquals(0.4375D, Easing.QUAD_OUT.apply(0.25D), 1.0E-15D);
        assertEquals(0.125D, Easing.QUAD_IN_OUT.apply(0.25D), 1.0E-15D);
        assertEquals(Math.pow(2.0D, -5.0D), Easing.EXPO_IN.apply(0.5D), 1.0E-15D);
        assertEquals(0.5D, Easing.SINE_IN_OUT.apply(0.5D), 1.0E-15D);
        assertEquals(0.75D, Easing.BOUNCE_OUT.apply(1.5D / 2.75D), 1.0E-12D);
        assertEquals(1.0D, Easing.BOUNCE_OUT.apply(1.0D / 2.75D), 1.0E-12D);
    }

    @Test
    void parseAcceptsAnyCaseAndEitherSeparator() {
        assertSame(Easing.QUAD_IN, Easing.parse("quad-in"));
        assertSame(Easing.QUAD_IN, Easing.parse("QUAD_IN"));
        assertSame(Easing.SINE_IN_OUT, Easing.parse("Sine-In_Out"));
        assertSame(Easing.SMOOTHER_STEP, Easing.parse(" smoother-step "));
        assertSame(Easing.LINEAR, Easing.parse("linear"));
        assertThrows(IllegalArgumentException.class, () -> Easing.parse("wobbly"));
        assertThrows(IllegalArgumentException.class, () -> Easing.parse(""));
        assertThrows(IllegalArgumentException.class, () -> Easing.parse(null));
    }

    @Test
    void stepsJumpAtTheEndOfEachInterval() {
        EasingFunction steps = Easing.steps(4);
        assertEquals(0.0D, steps.apply(0.0D), 0.0D);
        assertEquals(0.0D, steps.apply(0.24D), 0.0D);
        assertEquals(0.25D, steps.apply(0.25D), 0.0D);
        assertEquals(0.75D, steps.apply(0.99D), 0.0D);
        assertEquals(1.0D, steps.apply(1.0D), 0.0D);
        assertEquals(1.0D, Easing.steps(1).apply(1.0D), 0.0D);
        assertEquals(0.0D, Easing.steps(1).apply(0.999D), 0.0D);
        assertThrows(IllegalArgumentException.class, () -> Easing.steps(0));
    }

    @Test
    void cubicBezierSolvesTheCurveParameter() {
        EasingFunction linear = Easing.cubicBezier(0.0D, 0.0D, 1.0D, 1.0D);
        for (int step = 0; step <= GRID; step++) {
            double t = step / (double) GRID;
            assertEquals(t, linear.apply(t), 1.0E-9D);
        }
        double[][] curves = {{0.25D, 0.1D, 0.25D, 1.0D}, {0.42D, 0.0D, 1.0D, 1.0D}, {0.0D, 0.0D, 0.58D, 1.0D}, {0.68D, -0.55D, 0.265D, 1.55D},
            {0.9D, 0.1D, 0.1D, 0.9D}};
        SplittableRandom random = new SplittableRandom(101L);
        for (double[] curve : curves) {
            EasingFunction easing = Easing.cubicBezier(curve[0], curve[1], curve[2], curve[3]);
            assertEquals(0.0D, easing.apply(0.0D), 0.0D);
            assertEquals(1.0D, easing.apply(1.0D), 0.0D);
            for (int sample = 0; sample < 1_000; sample++) {
                double s = random.nextDouble();
                double x = bezier(curve[0], curve[2], s);
                double y = bezier(curve[1], curve[3], s);
                assertEquals(y, easing.apply(x), 1.0E-7D, "curve " + curve[0] + "," + curve[1] + "," + curve[2] + "," + curve[3] + " at s=" + s);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> Easing.cubicBezier(-0.1D, 0.0D, 0.5D, 1.0D));
        assertThrows(IllegalArgumentException.class, () -> Easing.cubicBezier(0.1D, 0.0D, 1.5D, 1.0D));
        assertThrows(IllegalArgumentException.class, () -> Easing.cubicBezier(0.1D, Double.NaN, 0.5D, 1.0D));
    }

    private static double bezier(double first, double second, double s) {
        double inverse = 1.0D - s;
        return 3.0D * inverse * inverse * s * first + 3.0D * inverse * s * s * second + s * s * s;
    }

    private static double minimum(Easing easing) {
        double minimum = Double.POSITIVE_INFINITY;
        for (int step = 0; step <= GRID; step++) {
            minimum = Math.min(minimum, easing.apply(step / (double) GRID));
        }
        return minimum;
    }

    private static double maximum(Easing easing) {
        double maximum = Double.NEGATIVE_INFINITY;
        for (int step = 0; step <= GRID; step++) {
            maximum = Math.max(maximum, easing.apply(step / (double) GRID));
        }
        return maximum;
    }
}
