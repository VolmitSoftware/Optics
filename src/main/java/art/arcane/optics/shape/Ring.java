package art.arcane.optics.shape;

import java.util.List;

public record Ring(double outerRadius, double innerRadius) implements Shape {
    private static final int MIN_SEGMENTS = 16;

    public Ring {
        outerRadius = ShapeMath.positive(outerRadius, ShapeMath.MAX_RADIUS, "Ring outer radius");
        innerRadius = ShapeMath.closed(innerRadius, 0.0D, outerRadius, "Ring inner radius");
        if (innerRadius >= outerRadius) {
            throw new IllegalArgumentException("Ring inner radius must be below the outer radius");
        }
    }

    @Override
    public Bounds2 bounds() {
        return new Bounds2(-outerRadius, -outerRadius, outerRadius, outerRadius);
    }

    @Override
    public boolean contains(double u, double v) {
        double rho = Math.sqrt(u * u + v * v);
        return rho >= innerRadius && rho <= outerRadius;
    }

    @Override
    public double signedDistance(double u, double v) {
        double rho = Math.sqrt(u * u + v * v);
        return innerRadius > 0.0D ? Math.max(rho - outerRadius, innerRadius - rho) : rho - outerRadius;
    }

    @Override
    public List<Outline> outlines(double tolerance) {
        ShapeMath.tolerance(tolerance);
        Outline outer = Outline.of(circle(outerRadius, tolerance));
        if (innerRadius <= 0.0D) {
            return List.of(outer);
        }
        return List.of(outer, Outline.of(circle(innerRadius, tolerance)).reversed());
    }

    private static double[] circle(double radius, double tolerance) {
        int segments = Math.max(MIN_SEGMENTS, ShapeMath.arcSegments(radius, 2.0D * Math.PI, tolerance));
        double[] points = new double[segments << 1];
        for (int step = 0; step < segments; step++) {
            double degrees = 360.0D * step / segments;
            points[step << 1] = radius * ShapeMath.cos(degrees);
            points[(step << 1) + 1] = radius * ShapeMath.sin(degrees);
        }
        return points;
    }
}
