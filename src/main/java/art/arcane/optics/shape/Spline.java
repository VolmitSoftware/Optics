package art.arcane.optics.shape;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import art.arcane.optics.internal.shape.Flatten;
import art.arcane.optics.internal.shape.PolylineDistance;

public record Spline(double[] controlPoints, int segmentsPerSpan) implements Shape {
    static final int MAX_CONTROL_POINTS = 256;
    static final int DEFAULT_SEGMENTS = 8;

    public Spline {
        controlPoints = ShapeMath.pointPairs(Objects.requireNonNull(controlPoints, "controlPoints"), 3, MAX_CONTROL_POINTS, "Spline");
        segmentsPerSpan = ShapeMath.count(segmentsPerSpan, 2, 32, "Spline segments per span");
    }

    @Override
    public double[] controlPoints() {
        return controlPoints.clone();
    }

    @Override
    public Bounds2 bounds() {
        return Bounds2.of(flattened());
    }

    @Override
    public boolean contains(double u, double v) {
        int count = vertexCount();
        boolean inside = false;
        double previousU = vertex(count - 1, 0);
        double previousV = vertex(count - 1, 1);
        for (int index = 0; index < count; index++) {
            double currentU = vertex(index, 0);
            double currentV = vertex(index, 1);
            if (PolylineDistance.crosses(u, v, previousU, previousV, currentU, currentV)) {
                inside = !inside;
            }
            previousU = currentU;
            previousV = currentV;
        }
        return inside;
    }

    @Override
    public double signedDistance(double u, double v) {
        int count = vertexCount();
        double best = Double.POSITIVE_INFINITY;
        double previousU = vertex(count - 1, 0);
        double previousV = vertex(count - 1, 1);
        for (int index = 0; index < count; index++) {
            double currentU = vertex(index, 0);
            double currentV = vertex(index, 1);
            best = Math.min(best, PolylineDistance.segmentDistanceSquared(u, v, previousU, previousV, currentU, currentV));
            previousU = currentU;
            previousV = currentV;
        }
        double distance = Math.sqrt(best);
        return contains(u, v) ? -distance : distance;
    }

    @Override
    public List<Outline> outlines(double tolerance) {
        ShapeMath.tolerance(tolerance);
        return List.of(ShapeMath.counterClockwise(flattened()));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Spline spline && segmentsPerSpan == spline.segmentsPerSpan
            && Arrays.equals(controlPoints, spline.controlPoints);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(controlPoints) * 31 + segmentsPerSpan;
    }

    @Override
    public String toString() {
        return "Spline[controlPoints=" + Arrays.toString(controlPoints) + ", segmentsPerSpan=" + segmentsPerSpan + "]";
    }

    private int vertexCount() {
        return (controlPoints.length >> 1) * segmentsPerSpan;
    }

    private double vertex(int index, int axis) {
        return Flatten.splineCoordinate(controlPoints, segmentsPerSpan, index, axis);
    }

    private double[] flattened() {
        int count = vertexCount();
        double[] points = new double[count << 1];
        for (int index = 0; index < count; index++) {
            points[index << 1] = vertex(index, 0);
            points[(index << 1) + 1] = vertex(index, 1);
        }
        return points;
    }
}
