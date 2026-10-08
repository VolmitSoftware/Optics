package art.arcane.optics.shape;

import java.util.List;
import java.util.Objects;

public record Union(Shape left, Shape right) implements Shape {
    public Union {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
    }

    @Override
    public Bounds2 bounds() {
        return left.bounds().union(right.bounds());
    }

    @Override
    public boolean contains(double u, double v) {
        return left.contains(u, v) || right.contains(u, v);
    }

    @Override
    public double signedDistance(double u, double v) {
        return Math.min(left.signedDistance(u, v), right.signedDistance(u, v));
    }

    @Override
    public List<Outline> outlines(double tolerance) {
        return ShapeMath.traced(this, tolerance);
    }
}
