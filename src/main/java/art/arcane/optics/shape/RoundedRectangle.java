package art.arcane.optics.shape;

import java.util.List;

import art.arcane.optics.internal.shape.PointBuffer;

public record RoundedRectangle(double width, double height, double radius) implements Shape {
    public RoundedRectangle {
        width = ShapeMath.positive(width, ShapeMath.MAX_EXTENT, "Rounded rectangle width");
        height = ShapeMath.positive(height, ShapeMath.MAX_EXTENT, "Rounded rectangle height");
        double limit = Math.min(width, height) * 0.5D;
        radius = ShapeMath.closed(radius, 0.0D, limit + limit * ShapeMath.FIT_TOLERANCE, "Rounded rectangle radius");
    }

    @Override
    public Bounds2 bounds() {
        return new Bounds2(-width * 0.5D, -height * 0.5D, width * 0.5D, height * 0.5D);
    }

    @Override
    public boolean contains(double u, double v) {
        return signedDistance(u, v) <= 0.0D;
    }

    @Override
    public double signedDistance(double u, double v) {
        double corner = Math.min(radius, Math.min(width, height) * 0.5D);
        return ShapeMath.box(Math.abs(u) - width * 0.5D + corner, Math.abs(v) - height * 0.5D + corner) - corner;
    }

    @Override
    public List<Outline> outlines(double tolerance) {
        ShapeMath.tolerance(tolerance);
        double halfWidth = width * 0.5D;
        double halfHeight = height * 0.5D;
        double corner = Math.min(radius, Math.min(width, height) * 0.5D);
        if (corner <= 0.0D) {
            return List.of(Outline.of(-halfWidth, -halfHeight, halfWidth, -halfHeight, halfWidth, halfHeight, -halfWidth, halfHeight));
        }
        int segments = ShapeMath.arcSegments(corner, Math.PI * 0.5D, tolerance);
        PointBuffer buffer = new PointBuffer(4 * (segments + 1));
        arc(buffer, halfWidth - corner, halfHeight - corner, corner, 0, segments);
        arc(buffer, -halfWidth + corner, halfHeight - corner, corner, 1, segments);
        arc(buffer, -halfWidth + corner, -halfHeight + corner, corner, 2, segments);
        arc(buffer, halfWidth - corner, -halfHeight + corner, corner, 3, segments);
        return List.of(Outline.of(buffer.closedLoop()));
    }

    private static void arc(PointBuffer buffer, double centerU, double centerV, double radius, int quadrant, int segments) {
        for (int step = 0; step <= segments; step++) {
            double degrees = quadrant * 90.0D + 90.0D * step / segments;
            buffer.addDistinct(centerU + radius * ShapeMath.cos(degrees), centerV + radius * ShapeMath.sin(degrees));
        }
    }
}
