package art.arcane.optics.shape;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import art.arcane.optics.internal.shape.PolylineDistance;
import art.arcane.optics.math.Vec2d;

public record Polygon(double[] points) implements Shape {
    static final int MAX_POINTS = 512;

    public Polygon {
        points = ShapeMath.pointPairs(Objects.requireNonNull(points, "points"), 3, MAX_POINTS, "Polygon");
    }

    public static Polygon of(List<Vec2d> points) {
        Objects.requireNonNull(points, "points");
        double[] pairs = new double[points.size() * 2];
        for (int index = 0; index < points.size(); index++) {
            Vec2d point = points.get(index);
            pairs[index * 2] = point.u();
            pairs[index * 2 + 1] = point.v();
        }
        return new Polygon(pairs);
    }

    @Override
    public double[] points() {
        return points.clone();
    }

    @Override
    public Bounds2 bounds() {
        return Bounds2.of(points);
    }

    @Override
    public boolean contains(double u, double v) {
        return PolylineDistance.contains(points, u, v);
    }

    @Override
    public double signedDistance(double u, double v) {
        double distance = Math.sqrt(PolylineDistance.distanceSquared(points, u, v));
        return PolylineDistance.contains(points, u, v) ? -distance : distance;
    }

    @Override
    public List<Outline> outlines(double tolerance) {
        ShapeMath.tolerance(tolerance);
        return List.of(ShapeMath.counterClockwise(points));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Polygon polygon && Arrays.equals(points, polygon.points);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(points);
    }

    @Override
    public String toString() {
        return "Polygon[points=" + Arrays.toString(points) + "]";
    }
}
