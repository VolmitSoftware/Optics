package art.arcane.optics.animation;

import java.util.List;

public interface Clip<T> {
    double duration();

    T sample(double time);

    default boolean endless() {
        return duration() == Double.POSITIVE_INFINITY;
    }

    default Clip<T> delayed(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.0D) {
            throw new IllegalArgumentException("Clip delay must be finite and non-negative: " + seconds);
        }
        if (seconds == 0.0D) {
            return this;
        }
        return Sequence.of(List.of(Constant.of(sample(0.0D), seconds), this));
    }

    default Clip<T> speed(double factor) {
        if (!Double.isFinite(factor) || factor <= 0.0D) {
            throw new IllegalArgumentException("Clip speed must be finite and positive: " + factor);
        }
        if (factor == 1.0D) {
            return this;
        }
        return new ScaledClip<T>(this, factor);
    }

    default Clip<T> reversed() {
        if (endless()) {
            throw new IllegalArgumentException("An endless clip cannot be reversed");
        }
        return new ReversedClip<T>(this);
    }

    default Clip<T> looped(int cycles, boolean pingPong) {
        return Loop.of(this, cycles, pingPong);
    }

    default Clip<T> then(Clip<T> next) {
        return Sequence.of(List.of(this, next));
    }
}
