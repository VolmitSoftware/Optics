package art.arcane.optics.animation;

import java.util.Objects;

public record Keyframe<T>(double time, T value, EasingFunction easing) {
    public Keyframe {
        if (!Double.isFinite(time) || time < 0.0D) {
            throw new IllegalArgumentException("Keyframe time must be finite and non-negative: " + time);
        }
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(easing, "easing");
    }

    public Keyframe(double time, T value) {
        this(time, value, Easing.LINEAR);
    }
}
