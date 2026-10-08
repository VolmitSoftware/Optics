package art.arcane.optics.shape;

import java.util.List;
import java.util.Objects;

public record Intersection(Shape left, Shape right) implements Shape {
    public Intersection {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
    }

    @Override
    public Bounds2 bounds() {
        return left.bounds().intersect(right.bounds());
    }

    @Override
    public boolean contains(double u, double v) {
        return left.contains(u, v) && right.contains(u, v);
    }

    @Override
    public double signedDistance(double u, double v) {
        return Math.max(left.signedDistance(u, v), right.signedDistance(u, v));
    }

    @Override
    public List<Outline> outlines(double tolerance) {
        return ShapeMath.traced(this, tolerance);
    }
}
