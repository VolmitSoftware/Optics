package art.arcane.optics.shape;

import java.util.LinkedHashMap;
import java.util.Map;

final class ShapeFixtures {
    private ShapeFixtures() {
    }

    static Map<String, Shape> presets() {
        Map<String, Shape> presets = new LinkedHashMap<String, Shape>();
        presets.put("rectangle", new Rectangle(1.6D, 1.1D));
        presets.put("rounded", new RoundedRectangle(1.8D, 1.4D, 0.35D));
        presets.put("circle", Ellipse.circle(0.9D));
        presets.put("ellipse", new Ellipse(1.0D, 0.55D));
        presets.put("hexagon", new RegularPolygon(6, 0.95D, 0.0D));
        presets.put("triangle", new RegularPolygon(3, 1.0D, 20.0D));
        presets.put("star", new Star(5, 1.0D, 0.45D, 0.0D));
        presets.put("star-turned", new Star(7, 0.9D, 0.5D, 13.0D));
        presets.put("flower", new Flower(5, 1.0D, 0.6D, 0.0D));
        presets.put("flower-three", new Flower(3, 0.95D, 0.7D, 25.0D));
        presets.put("heart", new Heart(1.0D, 0.0D));
        presets.put("heart-turned", new Heart(0.8D, 30.0D));
        presets.put("feather", new Feather(2.0D, 1.0D, 0.25D, 0.0D));
        presets.put("feather-turned", new Feather(1.6D, 0.8D, -0.5D, 45.0D));
        presets.put("ring", new Ring(1.0D, 0.6D));
        presets.put("polygon", new Polygon(new double[] {-0.8D, -0.7D, 0.9D, -0.6D, 0.3D, 0.2D, 0.7D, 0.9D, -0.6D, 0.5D}));
        presets.put("spline", new Spline(new double[] {0.0D, 0.9D, -0.8D, 0.1D, -0.4D, -0.8D, 0.5D, -0.7D, 0.8D, 0.2D}, 8));
        return presets;
    }

    static Map<String, Shape> mirrorSymmetric() {
        Map<String, Shape> shapes = new LinkedHashMap<String, Shape>();
        shapes.put("rectangle", new Rectangle(1.6D, 1.1D));
        shapes.put("rounded", new RoundedRectangle(1.8D, 1.4D, 0.35D));
        shapes.put("circle", Ellipse.circle(0.9D));
        shapes.put("ellipse", new Ellipse(1.0D, 0.55D));
        shapes.put("hexagon", new RegularPolygon(6, 0.95D, 0.0D));
        shapes.put("star", new Star(5, 1.0D, 0.45D, 0.0D));
        shapes.put("flower", new Flower(5, 1.0D, 0.6D, 0.0D));
        shapes.put("heart", new Heart(1.0D, 0.0D));
        shapes.put("ring", new Ring(1.0D, 0.6D));
        return shapes;
    }
}
