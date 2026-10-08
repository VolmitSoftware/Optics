package art.arcane.optics.shape;

import java.util.List;

public record Ellipse(double radiusU, double radiusV) implements Shape {
    private static final int MIN_SEGMENTS = 16;

    public Ellipse {
        radiusU = ShapeMath.positive(radiusU, ShapeMath.MAX_RADIUS, "Ellipse radiusU");
        radiusV = ShapeMath.positive(radiusV, ShapeMath.MAX_RADIUS, "Ellipse radiusV");
    }

    public static Ellipse circle(double radius) {
        return new Ellipse(radius, radius);
    }

    @Override
    public Bounds2 bounds() {
        return new Bounds2(-radiusU, -radiusV, radiusU, radiusV);
    }

    @Override
    public boolean contains(double u, double v) {
        double nu = u / radiusU;
        double nv = v / radiusV;
        return nu * nu + nv * nv <= 1.0D;
    }

    @Override
    public double signedDistance(double u, double v) {
        if (radiusU == radiusV) {
            return Math.sqrt(u * u + v * v) - radiusU;
        }
        double nu = u / radiusU;
        double nv = v / radiusV;
        double field = nu * nu + nv * nv;
        double gradientU = 2.0D * u / (radiusU * radiusU);
        double gradientV = 2.0D * v / (radiusV * radiusV);
        double gradient = Math.sqrt(gradientU * gradientU + gradientV * gradientV);
        if (gradient <= 0.0D) {
            return -Math.min(radiusU, radiusV);
        }
        return (field - 1.0D) / gradient;
    }

    @Override
    public List<Outline> outlines(double tolerance) {
        ShapeMath.tolerance(tolerance);
        int segments = Math.max(MIN_SEGMENTS, ShapeMath.arcSegments(Math.max(radiusU, radiusV), 2.0D * Math.PI, tolerance));
        double[] points = new double[segments << 1];
        for (int step = 0; step < segments; step++) {
            double degrees = 360.0D * step / segments;
            points[step << 1] = radiusU * ShapeMath.cos(degrees);
            points[(step << 1) + 1] = radiusV * ShapeMath.sin(degrees);
        }
        return List.of(Outline.of(points));
    }
}
