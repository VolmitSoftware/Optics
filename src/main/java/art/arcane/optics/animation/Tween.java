package art.arcane.optics.animation;

import java.util.Objects;

public final class Tween<T> implements Clip<T> {
    private final T from;
    private final T to;
    private final double duration;
    private final EasingFunction easing;
    private final Interpolator<T> interpolator;

    private Tween(T from, T to, double duration, EasingFunction easing, Interpolator<T> interpolator) {
        this.from = from;
        this.to = to;
        this.duration = duration;
        this.easing = easing;
        this.interpolator = interpolator;
    }

    public static <T> Tween<T> of(T from, T to, double duration, EasingFunction easing, Interpolator<T> interpolator) {
        return new Tween<T>(Objects.requireNonNull(from, "from"), Objects.requireNonNull(to, "to"), ClipTime.requireDuration(duration, false, "Tween"),
            Objects.requireNonNull(easing, "easing"), Objects.requireNonNull(interpolator, "interpolator"));
    }

    public static Tween<Double> of(double from, double to, double duration, EasingFunction easing) {
        return of(Double.valueOf(from), Double.valueOf(to), duration, easing, Interpolators.doubles());
    }

    public T from() {
        return from;
    }

    public T to() {
        return to;
    }

    public EasingFunction easing() {
        return easing;
    }

    @Override
    public double duration() {
        return duration;
    }

    @Override
    public T sample(double time) {
        double clamped = ClipTime.clamp(time, duration);
        if (clamped >= duration) {
            return to;
        }
        if (clamped <= 0.0D) {
            return from;
        }
        return interpolator.interpolate(from, to, easing.apply(clamped / duration));
    }
}
