package art.arcane.optics.shape;

import java.util.List;

public interface Shape {
    Bounds2 bounds();

    boolean contains(double u, double v);

    double signedDistance(double u, double v);

    List<Outline> outlines(double tolerance);

    default ShapeDescriptor descriptor() {
        return ShapeDescriptor.of(this, FitMode.CONTAIN);
    }

    default Shape union(Shape other) {
        return new Union(this, other);
    }

    default Shape intersect(Shape other) {
        return new Intersection(this, other);
    }

    default Shape subtract(Shape other) {
        return new Difference(this, other);
    }

    default Shape transformed(PlaneTransform transform) {
        return new Transformed(this, transform);
    }

    default Shape rotated(double degrees) {
        return transformed(PlaneTransform.rotation(degrees));
    }

    default Shape scaled(double factor) {
        return transformed(PlaneTransform.scale(factor));
    }

    default Shape scaled(double factorU, double factorV) {
        return transformed(PlaneTransform.scale(factorU, factorV));
    }

    default Shape translated(double du, double dv) {
        return transformed(PlaneTransform.translation(du, dv));
    }

    default Shape flippedU() {
        return transformed(PlaneTransform.flipU());
    }

    default Shape flippedV() {
        return transformed(PlaneTransform.flipV());
    }
}
