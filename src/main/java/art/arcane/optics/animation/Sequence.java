package art.arcane.optics.animation;

import java.util.List;

public final class Sequence<T> implements Clip<T> {
    private final List<Clip<T>> clips;
    private final double[] starts;
    private final double duration;

    private Sequence(List<Clip<T>> clips) {
        this.clips = clips;
        this.starts = new double[clips.size()];
        double total = 0.0D;
        for (int index = 0; index < starts.length; index++) {
            starts[index] = total;
            total += clips.get(index).duration();
        }
        this.duration = total;
    }

    public static <T> Sequence<T> of(List<Clip<T>> clips) {
        List<Clip<T>> copy = List.copyOf(clips);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("A sequence needs at least one clip");
        }
        for (int index = 0; index < copy.size() - 1; index++) {
            if (copy.get(index).endless()) {
                throw new IllegalArgumentException("Only the last clip of a sequence may be endless (clip " + index + ")");
            }
        }
        return new Sequence<T>(copy);
    }

    public List<Clip<T>> clips() {
        return clips;
    }

    @Override
    public double duration() {
        return duration;
    }

    @Override
    public T sample(double time) {
        double clamped = ClipTime.clamp(time, duration);
        int low = 0;
        int high = starts.length - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (starts[middle] <= clamped) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return clips.get(low).sample(clamped - starts[low]);
    }
}
