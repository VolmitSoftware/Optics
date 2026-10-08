package art.arcane.optics.shape;

import java.util.List;

import art.arcane.optics.internal.shape.MarchingSquares;
import art.arcane.optics.internal.shape.PolylineDistance;

public record Heart(double size, double rotationDegrees) implements Shape {
    private static final double HALF_WIDTH = 1.139028164686316D;
    private static final double TOP = 1.236659170012161D;
    private static final double BOTTOM = -1.0D;
    private static final double CENTER = (TOP + BOTTOM) * 0.5D;
    private static final double HALF_HEIGHT = (TOP - BOTTOM) * 0.5D / HALF_WIDTH;
    private static final int OUTLINE_SAMPLES = 128;
    private static final double OUTLINE_MARGIN = 0.05D;
    private static final double[] OUTLINE = unitOutline();

    public Heart {
        size = ShapeMath.positive(size, ShapeMath.MAX_RADIUS, "Heart size");
        rotationDegrees = ShapeMath.finite(rotationDegrees, "Heart rotation");
    }

    @Override
    public Bounds2 bounds() {
        return ShapeMath.rotatedBounds(new Bounds2(-size, -HALF_HEIGHT * size, size, HALF_HEIGHT * size), rotationDegrees);
    }

    @Override
    public boolean contains(double u, double v) {
        double cos = ShapeMath.cos(rotationDegrees);
        double sin = ShapeMath.sin(rotationDegrees);
        return implicit((cos * u + sin * v) / size, (-sin * u + cos * v) / size) <= 0.0D;
    }

    @Override
    public double signedDistance(double u, double v) {
        double cos = ShapeMath.cos(rotationDegrees);
        double sin = ShapeMath.sin(rotationDegrees);
        double unitU = (cos * u + sin * v) / size;
        double unitV = (-sin * u + cos * v) / size;
        double distance = size * Math.sqrt(PolylineDistance.distanceSquared(OUTLINE, unitU, unitV));
        return implicit(unitU, unitV) <= 0.0D ? -distance : distance;
    }

    @Override
    public List<Outline> outlines(double tolerance) {
        ShapeMath.tolerance(tolerance);
        double cos = ShapeMath.cos(rotationDegrees);
        double sin = ShapeMath.sin(rotationDegrees);
        double[] points = new double[OUTLINE.length];
        for (int index = 0; index < OUTLINE.length; index += 2) {
            double unitU = OUTLINE[index] * size;
            double unitV = OUTLINE[index + 1] * size;
            points[index] = cos * unitU - sin * unitV;
            points[index + 1] = sin * unitU + cos * unitV;
        }
        return List.of(Outline.of(points));
    }

    private static double implicit(double unitU, double unitV) {
        double x = unitU * HALF_WIDTH;
        double y = unitV * HALF_WIDTH + CENTER;
        double radial = x * x + y * y - 1.0D;
        return radial * radial * radial - x * x * y * y * y;
    }

    private static double[] unitOutline() {
        double minU = -1.0D - OUTLINE_MARGIN;
        double minV = -HALF_HEIGHT - OUTLINE_MARGIN;
        double stepU = (2.0D + 2.0D * OUTLINE_MARGIN) / (OUTLINE_SAMPLES - 1);
        double stepV = (2.0D * HALF_HEIGHT + 2.0D * OUTLINE_MARGIN) / (OUTLINE_SAMPLES - 1);
        List<double[]> loops = MarchingSquares.trace(Heart::implicit, minU, minV, stepU, stepV, OUTLINE_SAMPLES - 1,
            OUTLINE_SAMPLES - 1, true);
        double[] largest = loops.getFirst();
        for (double[] loop : loops) {
            if (Math.abs(PolylineDistance.signedArea(loop)) > Math.abs(PolylineDistance.signedArea(largest))) {
                largest = loop;
            }
        }
        return PolylineDistance.signedArea(largest) < 0.0D ? PolylineDistance.reversed(largest) : largest;
    }
}
