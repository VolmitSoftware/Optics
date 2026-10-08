package art.arcane.optics.animation;

import java.util.ArrayList;
import java.util.List;

import art.arcane.optics.shape.Difference;
import art.arcane.optics.shape.Ellipse;
import art.arcane.optics.shape.Feather;
import art.arcane.optics.shape.FitMode;
import art.arcane.optics.shape.Flower;
import art.arcane.optics.shape.Heart;
import art.arcane.optics.shape.Intersection;
import art.arcane.optics.shape.Path;
import art.arcane.optics.shape.Polygon;
import art.arcane.optics.shape.Rectangle;
import art.arcane.optics.shape.RegularPolygon;
import art.arcane.optics.shape.Ring;
import art.arcane.optics.shape.RoundedRectangle;
import art.arcane.optics.shape.Shape;
import art.arcane.optics.shape.ShapeDescriptor;
import art.arcane.optics.shape.Spline;
import art.arcane.optics.shape.Star;
import art.arcane.optics.shape.Transformed;
import art.arcane.optics.shape.Union;

final class ShapeInterpolation {
    private static final double MIDPOINT = 0.5D;

    private ShapeInterpolation() {
    }

    static ShapeDescriptor interpolate(ShapeDescriptor from, ShapeDescriptor to, double t) {
        if (t <= 0.0D) {
            return from;
        }
        if (t >= 1.0D) {
            return to;
        }
        Shape shape = shape(from.shape(), to.shape(), t);
        FitMode fit = t >= MIDPOINT ? to.fit() : from.fit();
        if (shape == from.shape() && fit == from.fit()) {
            return from;
        }
        if (shape == to.shape() && fit == to.fit()) {
            return to;
        }
        return ShapeDescriptor.of(shape, fit);
    }

    private static Shape shape(Shape from, Shape to, double t) {
        return switch (from) {
            case Rectangle a when to instanceof Rectangle b -> new Rectangle(mix(a.width(), b.width(), t), mix(a.height(), b.height(), t));
            case RoundedRectangle a when to instanceof RoundedRectangle b ->
                new RoundedRectangle(mix(a.width(), b.width(), t), mix(a.height(), b.height(), t), mix(a.radius(), b.radius(), t));
            case Ellipse a when to instanceof Ellipse b -> new Ellipse(mix(a.radiusU(), b.radiusU(), t), mix(a.radiusV(), b.radiusV(), t));
            case RegularPolygon a when to instanceof RegularPolygon b ->
                new RegularPolygon(pick(a.sides(), b.sides(), t), mix(a.radius(), b.radius(), t), mix(a.rotationDegrees(), b.rotationDegrees(), t));
            case Star a when to instanceof Star b -> new Star(pick(a.points(), b.points(), t), mix(a.outerRadius(), b.outerRadius(), t),
                mix(a.innerRadius(), b.innerRadius(), t), mix(a.rotationDegrees(), b.rotationDegrees(), t));
            case Flower a when to instanceof Flower b -> new Flower(pick(a.petals(), b.petals(), t), mix(a.radius(), b.radius(), t),
                mix(a.petalDepth(), b.petalDepth(), t), mix(a.rotationDegrees(), b.rotationDegrees(), t));
            case Heart a when to instanceof Heart b -> new Heart(mix(a.size(), b.size(), t), mix(a.rotationDegrees(), b.rotationDegrees(), t));
            case Feather a when to instanceof Feather b -> new Feather(mix(a.length(), b.length(), t), mix(a.width(), b.width(), t),
                mix(a.curve(), b.curve(), t), mix(a.rotationDegrees(), b.rotationDegrees(), t));
            case Ring a when to instanceof Ring b -> new Ring(mix(a.outerRadius(), b.outerRadius(), t), mix(a.innerRadius(), b.innerRadius(), t));
            case Polygon a when to instanceof Polygon b && a.points().length == b.points().length -> new Polygon(mix(a.points(), b.points(), t));
            case Spline a when to instanceof Spline b && a.controlPoints().length == b.controlPoints().length ->
                new Spline(mix(a.controlPoints(), b.controlPoints(), t), pick(a.segmentsPerSpan(), b.segmentsPerSpan(), t));
            case Path a when to instanceof Path b && sameStructure(a, b) -> path(a, b, t);
            case Union a when to instanceof Union b -> new Union(shape(a.left(), b.left(), t), shape(a.right(), b.right(), t));
            case Intersection a when to instanceof Intersection b -> new Intersection(shape(a.left(), b.left(), t), shape(a.right(), b.right(), t));
            case Difference a when to instanceof Difference b -> new Difference(shape(a.left(), b.left(), t), shape(a.right(), b.right(), t));
            case Transformed a when to instanceof Transformed b && a.transform().reflects() == b.transform().reflects() ->
                new Transformed(shape(a.shape(), b.shape(), t), a.transform().lerp(b.transform(), t));
            default -> t >= MIDPOINT ? to : from;
        };
    }

    private static boolean sameStructure(Path from, Path to) {
        List<Path.Segment> fromSegments = from.segments();
        List<Path.Segment> toSegments = to.segments();
        if (fromSegments.size() != toSegments.size()) {
            return false;
        }
        for (int index = 0; index < fromSegments.size(); index++) {
            if (fromSegments.get(index).getClass() != toSegments.get(index).getClass()) {
                return false;
            }
        }
        return true;
    }

    private static Path path(Path from, Path to, double t) {
        List<Path.Segment> fromSegments = from.segments();
        List<Path.Segment> toSegments = to.segments();
        List<Path.Segment> segments = new ArrayList<Path.Segment>(fromSegments.size());
        for (int index = 0; index < fromSegments.size(); index++) {
            segments.add(segment(fromSegments.get(index), toSegments.get(index), t));
        }
        return new Path(segments);
    }

    private static Path.Segment segment(Path.Segment from, Path.Segment to, double t) {
        return switch (from) {
            case Path.Move a when to instanceof Path.Move b -> new Path.Move(mix(a.u(), b.u(), t), mix(a.v(), b.v(), t));
            case Path.Line a when to instanceof Path.Line b -> new Path.Line(mix(a.u(), b.u(), t), mix(a.v(), b.v(), t));
            case Path.Quad a when to instanceof Path.Quad b ->
                new Path.Quad(mix(a.cu(), b.cu(), t), mix(a.cv(), b.cv(), t), mix(a.u(), b.u(), t), mix(a.v(), b.v(), t));
            case Path.Cubic a when to instanceof Path.Cubic b -> new Path.Cubic(mix(a.c1u(), b.c1u(), t), mix(a.c1v(), b.c1v(), t),
                mix(a.c2u(), b.c2u(), t), mix(a.c2v(), b.c2v(), t), mix(a.u(), b.u(), t), mix(a.v(), b.v(), t));
            default -> from;
        };
    }

    private static double[] mix(double[] from, double[] to, double t) {
        double[] mixed = new double[from.length];
        for (int index = 0; index < mixed.length; index++) {
            mixed[index] = mix(from[index], to[index], t);
        }
        return mixed;
    }

    private static double mix(double from, double to, double t) {
        double value = from + (to - from) * t;
        return Math.max(Math.min(from, to), Math.min(Math.max(from, to), value));
    }

    private static int pick(int from, int to, double t) {
        return t >= MIDPOINT ? to : from;
    }
}
