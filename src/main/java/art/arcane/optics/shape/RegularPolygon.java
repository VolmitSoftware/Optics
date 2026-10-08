package art.arcane.optics.shape;

import java.util.List;

public record RegularPolygon(int sides, double radius, double rotationDegrees) implements Shape {
    public RegularPolygon {
        sides = ShapeMath.count(sides, 3, 64, "Regular polygon sides");
        radius = ShapeMath.positive(radius, ShapeMath.MAX_RADIUS, "Regular polygon radius");
        rotationDegrees = ShapeMath.finite(rotationDegrees, "Regular polygon rotation");
    }

    @Override
    public Bounds2 bounds() {
        return Bounds2.of(vertices());
    }

    @Override
    public boolean contains(double u, double v) {
        return signedDistance(u, v) <= 0.0D;
    }

    @Override
    public double signedDistance(double u, double v) {
        double cos = ShapeMath.cos(rotationDegrees);
        double sin = ShapeMath.sin(rotationDegrees);
        double localU = cos * u + sin * v;
        double localV = -sin * u + cos * v;
        double sector = 2.0D * Math.PI / sides;
        double angle = StrictMath.atan2(localV, localU) - Math.PI * 0.5D - sector * 0.5D;
        angle -= sector * Math.rint(angle / sector);
        double rho = Math.sqrt(localU * localU + localV * localV);
        double x = rho * StrictMath.cos(angle);
        double y = rho * StrictMath.sin(angle);
        double apothem = radius * StrictMath.cos(Math.PI / sides);
        double half = radius * StrictMath.sin(Math.PI / sides);
        double dx = x - apothem;
        double dy = y - Math.max(-half, Math.min(half, y));
        double distance = Math.sqrt(dx * dx + dy * dy);
        return dx > 0.0D ? distance : -distance;
    }

    @Override
    public List<Outline> outlines(double tolerance) {
        ShapeMath.tolerance(tolerance);
        return List.of(Outline.of(vertices()));
    }

    private double[] vertices() {
        double[] points = new double[sides << 1];
        for (int index = 0; index < sides; index++) {
            double degrees = 90.0D + rotationDegrees + 360.0D * index / sides;
            points[index << 1] = radius * ShapeMath.cos(degrees);
            points[(index << 1) + 1] = radius * ShapeMath.sin(degrees);
        }
        return points;
    }
}
