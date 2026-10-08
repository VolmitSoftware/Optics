package art.arcane.optics.animation;

import java.util.Locale;

public enum Easing implements EasingFunction {
    LINEAR,
    SMOOTH_STEP,
    SMOOTHER_STEP,
    SINE_IN,
    SINE_OUT,
    SINE_IN_OUT,
    QUAD_IN,
    QUAD_OUT,
    QUAD_IN_OUT,
    CUBIC_IN,
    CUBIC_OUT,
    CUBIC_IN_OUT,
    QUART_IN,
    QUART_OUT,
    QUART_IN_OUT,
    QUINT_IN,
    QUINT_OUT,
    QUINT_IN_OUT,
    EXPO_IN,
    EXPO_OUT,
    EXPO_IN_OUT,
    CIRC_IN,
    CIRC_OUT,
    CIRC_IN_OUT,
    BACK_IN,
    BACK_OUT,
    BACK_IN_OUT,
    ELASTIC_IN,
    ELASTIC_OUT,
    ELASTIC_IN_OUT,
    BOUNCE_IN,
    BOUNCE_OUT,
    BOUNCE_IN_OUT;

    private static final double BACK = 1.70158D;
    private static final double BACK_IN_OUT_OVERSHOOT = BACK * 1.525D;
    private static final double ELASTIC_PERIOD = 2.0D * Math.PI / 3.0D;
    private static final double ELASTIC_IN_OUT_PERIOD = 2.0D * Math.PI / 4.5D;
    private static final double BOUNCE_SCALE = 7.5625D;
    private static final double BOUNCE_DIVISOR = 2.75D;
    private static final int BEZIER_NEWTON_STEPS = 8;
    private static final int BEZIER_BISECTION_STEPS = 64;
    private static final double BEZIER_EPSILON = 1.0E-14D;

    public static EasingFunction steps(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Step easing needs at least one step: " + count);
        }
        double steps = count;
        return t -> t >= 1.0D ? 1.0D : Math.floor(t * steps) / steps;
    }

    public static EasingFunction cubicBezier(double x1, double y1, double x2, double y2) {
        if (!(x1 >= 0.0D && x1 <= 1.0D && x2 >= 0.0D && x2 <= 1.0D) || !Double.isFinite(y1) || !Double.isFinite(y2)) {
            throw new IllegalArgumentException("Cubic bezier easing needs x1 and x2 in [0, 1] and finite y values: " + x1 + ", " + y1 + ", " + x2 + ", " + y2);
        }
        return t -> t <= 0.0D ? 0.0D : t >= 1.0D ? 1.0D : bezier(y1, y2, solveBezier(x1, x2, t));
    }

    public static Easing parse(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Easing name must not be null");
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (Easing easing : values()) {
            if (easing.name().equals(normalized)) {
                return easing;
            }
        }
        throw new IllegalArgumentException("Unknown easing: " + name);
    }

    @Override
    public double apply(double t) {
        return switch (this) {
            case LINEAR -> t;
            case SMOOTH_STEP -> t * t * (3.0D - 2.0D * t);
            case SMOOTHER_STEP -> t * t * t * (t * (6.0D * t - 15.0D) + 10.0D);
            case SINE_IN -> 1.0D - Math.cos(t * Math.PI / 2.0D);
            case SINE_OUT -> Math.sin(t * Math.PI / 2.0D);
            case SINE_IN_OUT -> -(Math.cos(Math.PI * t) - 1.0D) / 2.0D;
            case QUAD_IN -> power(t, 2);
            case QUAD_OUT -> 1.0D - power(1.0D - t, 2);
            case QUAD_IN_OUT -> inOutPower(t, 2);
            case CUBIC_IN -> power(t, 3);
            case CUBIC_OUT -> 1.0D - power(1.0D - t, 3);
            case CUBIC_IN_OUT -> inOutPower(t, 3);
            case QUART_IN -> power(t, 4);
            case QUART_OUT -> 1.0D - power(1.0D - t, 4);
            case QUART_IN_OUT -> inOutPower(t, 4);
            case QUINT_IN -> power(t, 5);
            case QUINT_OUT -> 1.0D - power(1.0D - t, 5);
            case QUINT_IN_OUT -> inOutPower(t, 5);
            case EXPO_IN -> t <= 0.0D ? 0.0D : Math.pow(2.0D, 10.0D * t - 10.0D);
            case EXPO_OUT -> t >= 1.0D ? 1.0D : 1.0D - Math.pow(2.0D, -10.0D * t);
            case EXPO_IN_OUT -> expoInOut(t);
            case CIRC_IN -> 1.0D - Math.sqrt(Math.max(0.0D, 1.0D - t * t));
            case CIRC_OUT -> Math.sqrt(Math.max(0.0D, 1.0D - (t - 1.0D) * (t - 1.0D)));
            case CIRC_IN_OUT -> t < 0.5D
                ? (1.0D - Math.sqrt(Math.max(0.0D, 1.0D - 4.0D * t * t))) / 2.0D
                : (Math.sqrt(Math.max(0.0D, 1.0D - power(-2.0D * t + 2.0D, 2))) + 1.0D) / 2.0D;
            case BACK_IN -> (BACK + 1.0D) * t * t * t - BACK * t * t;
            case BACK_OUT -> 1.0D + (BACK + 1.0D) * power(t - 1.0D, 3) + BACK * power(t - 1.0D, 2);
            case BACK_IN_OUT -> backInOut(t);
            case ELASTIC_IN -> t <= 0.0D ? 0.0D : t >= 1.0D ? 1.0D : -Math.pow(2.0D, 10.0D * t - 10.0D) * Math.sin((10.0D * t - 10.75D) * ELASTIC_PERIOD);
            case ELASTIC_OUT -> t <= 0.0D ? 0.0D : t >= 1.0D ? 1.0D : Math.pow(2.0D, -10.0D * t) * Math.sin((10.0D * t - 0.75D) * ELASTIC_PERIOD) + 1.0D;
            case ELASTIC_IN_OUT -> elasticInOut(t);
            case BOUNCE_IN -> 1.0D - bounceOut(1.0D - t);
            case BOUNCE_OUT -> bounceOut(t);
            case BOUNCE_IN_OUT -> t < 0.5D ? (1.0D - bounceOut(1.0D - 2.0D * t)) / 2.0D : (1.0D + bounceOut(2.0D * t - 1.0D)) / 2.0D;
        };
    }

    private static double power(double base, int exponent) {
        double result = base;
        for (int factor = 1; factor < exponent; factor++) {
            result *= base;
        }
        return result;
    }

    private static double inOutPower(double t, int exponent) {
        return t < 0.5D ? power(2.0D, exponent - 1) * power(t, exponent) : 1.0D - power(-2.0D * t + 2.0D, exponent) / 2.0D;
    }

    private static double expoInOut(double t) {
        if (t <= 0.0D) {
            return 0.0D;
        }
        if (t >= 1.0D) {
            return 1.0D;
        }
        return t < 0.5D ? Math.pow(2.0D, 20.0D * t - 10.0D) / 2.0D : (2.0D - Math.pow(2.0D, -20.0D * t + 10.0D)) / 2.0D;
    }

    private static double backInOut(double t) {
        if (t < 0.5D) {
            return power(2.0D * t, 2) * ((BACK_IN_OUT_OVERSHOOT + 1.0D) * 2.0D * t - BACK_IN_OUT_OVERSHOOT) / 2.0D;
        }
        return (power(2.0D * t - 2.0D, 2) * ((BACK_IN_OUT_OVERSHOOT + 1.0D) * (t * 2.0D - 2.0D) + BACK_IN_OUT_OVERSHOOT) + 2.0D) / 2.0D;
    }

    private static double elasticInOut(double t) {
        if (t <= 0.0D) {
            return 0.0D;
        }
        if (t >= 1.0D) {
            return 1.0D;
        }
        if (t < 0.5D) {
            return -(Math.pow(2.0D, 20.0D * t - 10.0D) * Math.sin((20.0D * t - 11.125D) * ELASTIC_IN_OUT_PERIOD)) / 2.0D;
        }
        return Math.pow(2.0D, -20.0D * t + 10.0D) * Math.sin((20.0D * t - 11.125D) * ELASTIC_IN_OUT_PERIOD) / 2.0D + 1.0D;
    }

    private static double bounceOut(double t) {
        if (t < 1.0D / BOUNCE_DIVISOR) {
            return BOUNCE_SCALE * t * t;
        }
        if (t < 2.0D / BOUNCE_DIVISOR) {
            double shifted = t - 1.5D / BOUNCE_DIVISOR;
            return BOUNCE_SCALE * shifted * shifted + 0.75D;
        }
        if (t < 2.5D / BOUNCE_DIVISOR) {
            double shifted = t - 2.25D / BOUNCE_DIVISOR;
            return BOUNCE_SCALE * shifted * shifted + 0.9375D;
        }
        double shifted = t - 2.625D / BOUNCE_DIVISOR;
        return BOUNCE_SCALE * shifted * shifted + 0.984375D;
    }

    private static double bezier(double first, double second, double s) {
        double inverse = 1.0D - s;
        return 3.0D * inverse * inverse * s * first + 3.0D * inverse * s * s * second + s * s * s;
    }

    private static double bezierSlope(double first, double second, double s) {
        double inverse = 1.0D - s;
        return 3.0D * inverse * inverse * first + 6.0D * inverse * s * (second - first) + 3.0D * s * s * (1.0D - second);
    }

    private static double solveBezier(double x1, double x2, double x) {
        double s = x;
        for (int step = 0; step < BEZIER_NEWTON_STEPS; step++) {
            double error = bezier(x1, x2, s) - x;
            if (Math.abs(error) < BEZIER_EPSILON) {
                return s;
            }
            double slope = bezierSlope(x1, x2, s);
            if (Math.abs(slope) < 1.0E-6D) {
                break;
            }
            s -= error / slope;
            if (s < 0.0D || s > 1.0D) {
                break;
            }
        }
        double low = 0.0D;
        double high = 1.0D;
        s = x;
        for (int step = 0; step < BEZIER_BISECTION_STEPS; step++) {
            double value = bezier(x1, x2, s);
            if (Math.abs(value - x) < BEZIER_EPSILON) {
                return s;
            }
            if (value < x) {
                low = s;
            } else {
                high = s;
            }
            s = (low + high) * 0.5D;
        }
        return s;
    }
}
