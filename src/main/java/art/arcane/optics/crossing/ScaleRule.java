package art.arcane.optics.crossing;

import java.util.Objects;

import art.arcane.optics.aperture.SizeRatio;

public record ScaleRule(Mode mode, double min, double max) {
    public static final double ATTRIBUTE_MIN = 0.0625D;
    public static final double ATTRIBUTE_MAX = 16.0D;
    public static final ScaleRule OFF = new ScaleRule(Mode.OFF, ATTRIBUTE_MIN, ATTRIBUTE_MAX);

    public ScaleRule {
        Objects.requireNonNull(mode, "mode");
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            throw new IllegalArgumentException("Scale bounds must be finite: " + min + ".." + max);
        }
        min = clampToAttribute(min);
        max = clampToAttribute(max);
        if (min > max) {
            throw new IllegalArgumentException("Scale minimum " + min + " exceeds maximum " + max);
        }
    }

    public static ScaleRule motion() {
        return new ScaleRule(Mode.MOTION, ATTRIBUTE_MIN, ATTRIBUTE_MAX);
    }

    public static ScaleRule ratio(double min, double max) {
        return new ScaleRule(Mode.RATIO, min, max);
    }

    public double travelScale(SizeRatio ratio) {
        return mode == Mode.OFF ? 1.0D : ratio.ratio();
    }

    public double entityFactor(double currentFactor, SizeRatio ratio) {
        if (mode != Mode.RATIO) {
            return currentFactor;
        }
        return Math.max(min, Math.min(max, currentFactor * ratio.ratio()));
    }

    public boolean changesEntity() {
        return mode == Mode.RATIO;
    }

    private static double clampToAttribute(double value) {
        return Math.max(ATTRIBUTE_MIN, Math.min(ATTRIBUTE_MAX, value));
    }

    public enum Mode {
        OFF,
        MOTION,
        RATIO
    }
}
