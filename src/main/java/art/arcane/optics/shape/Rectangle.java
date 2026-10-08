package art.arcane.optics.shape;

import java.util.List;

public record Rectangle(double width, double height) implements Shape {
    public Rectangle {
        width = ShapeMath.positive(width, ShapeMath.MAX_EXTENT, "Rectangle width");
        height = ShapeMath.positive(height, ShapeMath.MAX_EXTENT, "Rectangle height");
    }

    @Override
    public Bounds2 bounds() {
        return new Bounds2(-width * 0.5D, -height * 0.5D, width * 0.5D, height * 0.5D);
    }

    @Override
    public boolean contains(double u, double v) {
        return Math.abs(u) <= width * 0.5D && Math.abs(v) <= height * 0.5D;
    }

    @Override
    public double signedDistance(double u, double v) {
        return ShapeMath.box(Math.abs(u) - width * 0.5D, Math.abs(v) - height * 0.5D);
    }

    @Override
    public List<Outline> outlines(double tolerance) {
        ShapeMath.tolerance(tolerance);
        double halfWidth = width * 0.5D;
        double halfHeight = height * 0.5D;
        return List.of(Outline.of(-halfWidth, -halfHeight, halfWidth, -halfHeight, halfWidth, halfHeight, -halfWidth, halfHeight));
    }
}
