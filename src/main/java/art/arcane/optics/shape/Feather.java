package art.arcane.optics.shape;

import java.util.List;

import art.arcane.optics.internal.shape.PolylineDistance;

public record Feather(double length, double width, double curve, double rotationDegrees) implements Shape {
    private static final int SIDE_SAMPLES = 96;
    private static final int VERTICES = SIDE_SAMPLES * 2 - 2;
    private static final double VANE_EXPONENT = 0.7D;
    private static final double RIGHT_VANE = 0.55D;

    public Feather {
        length = ShapeMath.positive(length, ShapeMath.MAX_RADIUS, "Feather length");
        width = ShapeMath.positive(width, ShapeMath.MAX_RADIUS, "Feather width");
        curve = ShapeMath.closed(curve, -1.0D, 1.0D, "Feather curve");
        rotationDegrees = ShapeMath.finite(rotationDegrees, "Feather rotation");
    }

    @Override
    public Bounds2 bounds() {
        double cos = ShapeMath.cos(rotationDegrees);
        double sin = ShapeMath.sin(rotationDegrees);
        double minU = Double.POSITIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY;
        double maxU = Double.NEGATIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < VERTICES; index++) {
            double localU = vertexU(index);
            double localV = vertexV(index);
            double u = cos * localU - sin * localV;
            double v = sin * localU + cos * localV;
            minU = Math.min(minU, u);
            minV = Math.min(minV, v);
            maxU = Math.max(maxU, u);
            maxV = Math.max(maxV, v);
        }
        return new Bounds2(minU, minV, maxU, maxV);
    }

    @Override
    public boolean contains(double u, double v) {
        double cos = ShapeMath.cos(rotationDegrees);
        double sin = ShapeMath.sin(rotationDegrees);
        return containsLocal(cos * u + sin * v, -sin * u + cos * v);
    }

    @Override
    public double signedDistance(double u, double v) {
        double cos = ShapeMath.cos(rotationDegrees);
        double sin = ShapeMath.sin(rotationDegrees);
        double localU = cos * u + sin * v;
        double localV = -sin * u + cos * v;
        double best = Double.POSITIVE_INFINITY;
        double previousU = vertexU(VERTICES - 1);
        double previousV = vertexV(VERTICES - 1);
        for (int index = 0; index < VERTICES; index++) {
            double currentU = vertexU(index);
            double currentV = vertexV(index);
            best = Math.min(best, PolylineDistance.segmentDistanceSquared(localU, localV, previousU, previousV, currentU, currentV));
            previousU = currentU;
            previousV = currentV;
        }
        double distance = Math.sqrt(best);
        return containsLocal(localU, localV) ? -distance : distance;
    }

    @Override
    public List<Outline> outlines(double tolerance) {
        ShapeMath.tolerance(tolerance);
        double cos = ShapeMath.cos(rotationDegrees);
        double sin = ShapeMath.sin(rotationDegrees);
        double[] points = new double[VERTICES << 1];
        for (int index = 0; index < VERTICES; index++) {
            double localU = vertexU(index);
            double localV = vertexV(index);
            points[index << 1] = cos * localU - sin * localV;
            points[(index << 1) + 1] = sin * localU + cos * localV;
        }
        return List.of(ShapeMath.counterClockwise(points));
    }

    private boolean containsLocal(double localU, double localV) {
        boolean inside = false;
        double previousU = vertexU(VERTICES - 1);
        double previousV = vertexV(VERTICES - 1);
        for (int index = 0; index < VERTICES; index++) {
            double currentU = vertexU(index);
            double currentV = vertexV(index);
            if (PolylineDistance.crosses(localU, localV, previousU, previousV, currentU, currentV)) {
                inside = !inside;
            }
            previousU = currentU;
            previousV = currentV;
        }
        return inside;
    }

    private double vertexU(int index) {
        if (index < SIDE_SAMPLES) {
            double t = spine(index);
            return spineOffset(t) - halfWidth(t);
        }
        double t = spine(VERTICES - index);
        return spineOffset(t) + halfWidth(t) * RIGHT_VANE;
    }

    private double vertexV(int index) {
        double t = index < SIDE_SAMPLES ? spine(index) : spine(VERTICES - index);
        return -length * 0.5D + length * t;
    }

    private static double spine(int sample) {
        return sample / (double) (SIDE_SAMPLES - 1);
    }

    private double spineOffset(double t) {
        return curve * length * 0.5D * t * t;
    }

    private double halfWidth(double t) {
        return width * 0.5D * StrictMath.pow(Math.max(0.0D, StrictMath.sin(Math.PI * t)), VANE_EXPONENT);
    }
}
