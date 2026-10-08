package art.arcane.optics.shape;

import java.util.List;

public record Star(int points, double outerRadius, double innerRadius, double rotationDegrees) implements Shape {
    public Star {
        points = ShapeMath.count(points, 3, 32, "Star points");
        outerRadius = ShapeMath.positive(outerRadius, ShapeMath.MAX_RADIUS, "Star outer radius");
        innerRadius = ShapeMath.positive(innerRadius, outerRadius, "Star inner radius");
        if (innerRadius >= outerRadius) {
            throw new IllegalArgumentException("Star inner radius must be below the outer radius");
        }
        rotationDegrees = ShapeMath.finite(rotationDegrees, "Star rotation");
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
        double sector = 2.0D * Math.PI / points;
        double angle = StrictMath.atan2(localV, localU) - Math.PI * 0.5D;
        angle = Math.abs(angle - sector * Math.rint(angle / sector));
        double rho = Math.sqrt(localU * localU + localV * localV);
        double qx = rho * StrictMath.cos(angle);
        double qy = rho * StrictMath.sin(angle);
        double edgeX = innerRadius * StrictMath.cos(sector * 0.5D) - outerRadius;
        double edgeY = innerRadius * StrictMath.sin(sector * 0.5D);
        double offsetX = qx - outerRadius;
        double t = (offsetX * edgeX + qy * edgeY) / (edgeX * edgeX + edgeY * edgeY);
        t = Math.max(0.0D, Math.min(1.0D, t));
        double dx = offsetX - edgeX * t;
        double dy = qy - edgeY * t;
        double distance = Math.sqrt(dx * dx + dy * dy);
        return edgeX * qy - edgeY * offsetX >= 0.0D ? -distance : distance;
    }

    @Override
    public List<Outline> outlines(double tolerance) {
        ShapeMath.tolerance(tolerance);
        return List.of(Outline.of(vertices()));
    }

    private double[] vertices() {
        int count = points << 1;
        double[] out = new double[count << 1];
        for (int index = 0; index < count; index++) {
            double degrees = 90.0D + rotationDegrees + 180.0D * index / points;
            double reach = (index & 1) == 0 ? outerRadius : innerRadius;
            out[index << 1] = reach * ShapeMath.cos(degrees);
            out[(index << 1) + 1] = reach * ShapeMath.sin(degrees);
        }
        return out;
    }
}
