package art.arcane.optics.animation;

public interface Interpolator<T> {
    T interpolate(T from, T to, double t);
}
