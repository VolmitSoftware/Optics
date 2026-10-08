package art.arcane.optics.animation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class Track<T> implements Clip<T> {
    private final Interpolator<T> interpolator;
    private final List<Keyframe<T>> keyframes;
    private final double[] times;

    private Track(Interpolator<T> interpolator, List<Keyframe<T>> keyframes) {
        this.interpolator = interpolator;
        this.keyframes = keyframes;
        this.times = new double[keyframes.size()];
        for (int index = 0; index < times.length; index++) {
            times[index] = keyframes.get(index).time();
        }
    }

    public static <T> Track<T> of(Interpolator<T> interpolator, List<Keyframe<T>> keyframes) {
        Objects.requireNonNull(interpolator, "interpolator");
        if (keyframes.isEmpty()) {
            throw new IllegalArgumentException("A track needs at least one keyframe");
        }
        List<Keyframe<T>> sorted = new ArrayList<Keyframe<T>>(keyframes);
        sorted.sort(Comparator.comparingDouble(Keyframe::time));
        for (int index = 1; index < sorted.size(); index++) {
            if (sorted.get(index).time() == sorted.get(index - 1).time()) {
                throw new IllegalArgumentException("Track keyframes must have distinct times: " + sorted.get(index).time());
            }
        }
        return new Track<T>(interpolator, List.copyOf(sorted));
    }

    public List<Keyframe<T>> keyframes() {
        return keyframes;
    }

    public Track<T> with(Keyframe<T> keyframe) {
        Objects.requireNonNull(keyframe, "keyframe");
        List<Keyframe<T>> next = new ArrayList<Keyframe<T>>(keyframes.size() + 1);
        for (Keyframe<T> existing : keyframes) {
            if (existing.time() != keyframe.time()) {
                next.add(existing);
            }
        }
        next.add(keyframe);
        return of(interpolator, next);
    }

    @Override
    public double duration() {
        return times[times.length - 1];
    }

    @Override
    public T sample(double time) {
        double clamped = ClipTime.clamp(time, duration());
        int last = times.length - 1;
        if (clamped <= times[0]) {
            return keyframes.get(0).value();
        }
        int low = 0;
        int high = last;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (times[middle] <= clamped) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        Keyframe<T> start = keyframes.get(low);
        if (low == last || clamped == times[low]) {
            return start.value();
        }
        Keyframe<T> end = keyframes.get(low + 1);
        double progress = (clamped - times[low]) / (times[low + 1] - times[low]);
        return interpolator.interpolate(start.value(), end.value(), end.easing().apply(progress));
    }
}
