package art.arcane.optics.shape;

import java.util.List;

import art.arcane.optics.internal.shape.ShapeGrammar;
import art.arcane.optics.math.Vec2d;

public final class Shapes {
    public static final Shape FULL = new Rectangle(2.0D, 2.0D);

    private Shapes() {
    }

    public static Shape rectangle(double width, double height) {
        return new Rectangle(width, height);
    }

    public static Shape roundedRectangle(double width, double height, double radius) {
        return new RoundedRectangle(width, height, radius);
    }

    public static Shape circle(double radius) {
        return Ellipse.circle(radius);
    }

    public static Shape ellipse(double radiusU, double radiusV) {
        return new Ellipse(radiusU, radiusV);
    }

    public static Shape regularPolygon(int sides, double radius, double rotationDegrees) {
        return new RegularPolygon(sides, radius, rotationDegrees);
    }

    public static Shape star(int points, double outerRadius, double innerRadius, double rotationDegrees) {
        return new Star(points, outerRadius, innerRadius, rotationDegrees);
    }

    public static Shape flower(int petals, double radius, double petalDepth, double rotationDegrees) {
        return new Flower(petals, radius, petalDepth, rotationDegrees);
    }

    public static Shape heart(double size) {
        return new Heart(size, 0.0D);
    }

    public static Shape feather(double length, double width, double curve) {
        return new Feather(length, width, curve, 0.0D);
    }

    public static Shape ring(double outerRadius, double innerRadius) {
        return new Ring(outerRadius, innerRadius);
    }

    public static Shape polygon(double... uv) {
        return new Polygon(uv);
    }

    public static Shape polygon(List<Vec2d> points) {
        return Polygon.of(points);
    }

    public static Shape spline(double... uv) {
        return new Spline(uv, Spline.DEFAULT_SEGMENTS);
    }

    public static Path.Builder path() {
        return Path.builder();
    }

    public static Shape of(ShapeDescriptor descriptor) {
        return descriptor.shape();
    }

    public static Shape parse(String text) {
        return ShapeGrammar.parse(text, false).shape();
    }

    public static String format(Shape shape) {
        return ShapeGrammar.format(shape);
    }

    public static List<String> presetNames() {
        return List.of("full", "rectangle", "rounded", "circle", "ellipse", "polygon", "star", "flower", "heart", "feather", "ring");
    }
}
