package art.arcane.optics.animation;

final class ScaledClip<T> implements Clip<T> {
    private final Clip<T> clip;
    private final double factor;
    private final double duration;

    ScaledClip(Clip<T> clip, double factor) {
        this.clip = clip;
        this.factor = factor;
        this.duration = clip.duration() / factor;
    }

    @Override
    public double duration() {
        return duration;
    }

    @Override
    public T sample(double time) {
        return clip.sample(ClipTime.clamp(time, duration) * factor);
    }
}
