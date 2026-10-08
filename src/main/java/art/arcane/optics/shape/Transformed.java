package art.arcane.optics.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record Transformed(Shape shape, PlaneTransform transform) implements Shape {
    private static final double SINGULAR = 1.0E-12D;

    public Transformed {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(transform, "transform");
        if (Math.abs(transform.determinant()) < SINGULAR) {
            throw new IllegalArgumentException("Shape transform must not be singular");
        }
    }

    @Override
    public Bounds2 bounds() {
        return shape.bounds().transformed(transform);
    }

    @Override
    public boolean contains(double u, double v) {
        double determinant = transform.determinant();
        double du = u - transform.tu();
        double dv = v - transform.tv();
        return shape.contains((transform.d() * du - transform.b() * dv) / determinant,
            (transform.a() * dv - transform.c() * du) / determinant);
    }

    @Override
    public double signedDistance(double u, double v) {
        double determinant = transform.determinant();
        double du = u - transform.tu();
        double dv = v - transform.tv();
        return shape.signedDistance((transform.d() * du - transform.b() * dv) / determinant,
            (transform.a() * dv - transform.c() * du) / determinant) * Math.sqrt(Math.abs(determinant));
    }

    @Override
    public List<Outline> outlines(double tolerance) {
        ShapeMath.tolerance(tolerance);
        List<Outline> source = shape.outlines(tolerance / ShapeMath.maxStretch(transform));
        List<Outline> mapped = new ArrayList<Outline>(source.size());
        boolean reflects = transform.reflects();
        for (Outline outline : source) {
            Outline moved = outline.transformed(transform);
            mapped.add(reflects ? moved.reversed() : moved);
        }
        return List.copyOf(mapped);
    }

    @Override
    public Shape transformed(PlaneTransform outer) {
        return new Transformed(shape, outer.compose(transform));
    }
}
