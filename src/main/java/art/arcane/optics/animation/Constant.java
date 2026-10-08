package art.arcane.optics.animation;

import java.util.Objects;

public final class Constant<T> implements Clip<T> {
    private final T value;
    private final double duration;

    private Constant(T value, double duration) {
        this.value = value;
        this.duration = duration;
    }

    public static <T> Constant<T> of(T value, double duration) {
        return new Constant<T>(Objects.requireNonNull(value, "value"), ClipTime.requireDuration(duration, true, "Constant"));
    }

    @Override
    public double duration() {
        return duration;
    }

    @Override
    public T sample(double time) {
        return value;
    }
}
