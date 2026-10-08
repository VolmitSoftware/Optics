package art.arcane.optics.animation;

public interface EasingFunction {
    double apply(double t);

    default EasingFunction reversed() {
        return t -> 1.0D - apply(1.0D - t);
    }

    default EasingFunction mirrored() {
        return t -> t < 0.5D ? 0.5D * apply(2.0D * t) : 1.0D - 0.5D * apply(2.0D - 2.0D * t);
    }
}
