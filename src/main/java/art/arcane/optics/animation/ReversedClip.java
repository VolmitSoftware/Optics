package art.arcane.optics.animation;

final class ReversedClip<T> implements Clip<T> {
    private final Clip<T> clip;
    private final double duration;

    ReversedClip(Clip<T> clip) {
        this.clip = clip;
        this.duration = clip.duration();
    }

    @Override
    public double duration() {
        return duration;
    }

    @Override
    public T sample(double time) {
        return clip.sample(duration - ClipTime.clamp(time, duration));
    }

    @Override
    public Clip<T> reversed() {
        return clip;
    }
}
