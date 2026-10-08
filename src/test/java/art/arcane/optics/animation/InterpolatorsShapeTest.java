package art.arcane.optics.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import art.arcane.optics.shape.FitMode;
import art.arcane.optics.shape.Path;
import art.arcane.optics.shape.PlaneTransform;
import art.arcane.optics.shape.Polygon;
import art.arcane.optics.shape.Shape;
import art.arcane.optics.shape.ShapeDescriptor;
import art.arcane.optics.shape.Shapes;
import art.arcane.optics.shape.Star;
import art.arcane.optics.shape.Transformed;
import art.arcane.optics.shape.Union;

final class InterpolatorsShapeTest {
    private static final Interpolator<ShapeDescriptor> SHAPES = Interpolators.shapes();

    @Test
    void planeTransformsInterpolateTheDecomposition() {
        Interpolator<PlaneTransform> transforms = Interpolators.planeTransforms();
        PlaneTransform from = PlaneTransform.translation(2.0D, -2.0D);
        PlaneTransform to = PlaneTransform.translation(4.0D, 2.0D).compose(PlaneTransform.rotation(90.0D)).compose(PlaneTransform.scale(3.0D));
        PlaneTransform middle = transforms.interpolate(from, to, 0.5D);
        assertEquals(from.lerp(to, 0.5D), middle);
        assertEquals(45.0D, middle.rotationDegrees(), 1.0E-9D);
        assertEquals(2.0D, middle.scaleU(), 1.0E-9D);
        assertEquals(3.0D, middle.tu(), 1.0E-12D);
        assertEquals(0.0D, middle.tv(), 1.0E-12D);
    }

    @Test
    void endpointsReturnTheEndpointDescriptors() {
        ShapeDescriptor from = descriptor(Shapes.circle(0.5D));
        ShapeDescriptor to = descriptor(Shapes.heart(1.0D));
        assertSame(from, SHAPES.interpolate(from, to, 0.0D));
        assertSame(from, SHAPES.interpolate(from, to, -1.0D));
        assertSame(to, SHAPES.interpolate(from, to, 1.0D));
        assertSame(to, SHAPES.interpolate(from, to, 3.0D));
    }

    @Test
    void sameKindsInterpolateTheirParameters() {
        assertEquals(descriptor(Shapes.circle(0.75D)), SHAPES.interpolate(descriptor(Shapes.circle(0.5D)), descriptor(Shapes.circle(1.0D)), 0.5D));
        ShapeDescriptor fromStar = descriptor(Shapes.star(5, 1.0D, 0.5D, 0.0D));
        ShapeDescriptor toStar = descriptor(Shapes.star(7, 0.8D, 0.2D, 90.0D));
        assertEquals(descriptor(new Star(5, 0.95D, 0.425D, 22.5D)), SHAPES.interpolate(fromStar, toStar, 0.25D));
        assertEquals(descriptor(new Star(7, 0.85D, 0.275D, 67.5D)), SHAPES.interpolate(fromStar, toStar, 0.75D));
        assertEquals(descriptor(Shapes.roundedRectangle(1.5D, 1.0D, 0.25D)),
            SHAPES.interpolate(descriptor(Shapes.roundedRectangle(2.0D, 1.0D, 0.5D)), descriptor(Shapes.roundedRectangle(1.0D, 1.0D, 0.0D)), 0.5D));
    }

    @Test
    void differentKindsStepAtTheMidpoint() {
        ShapeDescriptor from = descriptor(Shapes.circle(0.5D));
        ShapeDescriptor to = descriptor(Shapes.heart(1.0D));
        assertSame(from, SHAPES.interpolate(from, to, 0.49D));
        assertSame(to, SHAPES.interpolate(from, to, 0.5D));
        ShapeDescriptor triangle = descriptor(Shapes.polygon(-0.5D, -0.5D, 0.5D, -0.5D, 0.0D, 0.5D));
        ShapeDescriptor square = descriptor(Shapes.polygon(-0.5D, -0.5D, 0.5D, -0.5D, 0.5D, 0.5D, -0.5D, 0.5D));
        assertSame(triangle, SHAPES.interpolate(triangle, square, 0.3D));
        assertSame(square, SHAPES.interpolate(triangle, square, 0.7D));
    }

    @Test
    void fitStepsAtTheMidpointWhileParametersInterpolate() {
        ShapeDescriptor from = ShapeDescriptor.of(Shapes.circle(0.5D), FitMode.CONTAIN);
        ShapeDescriptor to = ShapeDescriptor.of(Shapes.circle(1.0D), FitMode.COVER);
        assertEquals(ShapeDescriptor.of(Shapes.circle(0.625D), FitMode.CONTAIN), SHAPES.interpolate(from, to, 0.25D));
        assertEquals(ShapeDescriptor.of(Shapes.circle(0.875D), FitMode.COVER), SHAPES.interpolate(from, to, 0.75D));
    }

    @Test
    void polygonsSplinesAndPathsWithMatchingStructureInterpolatePointWise() {
        ShapeDescriptor narrow = descriptor(Shapes.polygon(-0.25D, -0.5D, 0.25D, -0.5D, 0.0D, 0.5D));
        ShapeDescriptor wide = descriptor(Shapes.polygon(-0.75D, -0.5D, 0.75D, -0.5D, 0.0D, 1.0D));
        Shape polygon = SHAPES.interpolate(narrow, wide, 0.5D).shape();
        assertEquals(new Polygon(new double[] {-0.5D, -0.5D, 0.5D, -0.5D, 0.0D, 0.75D}), polygon);
        ShapeDescriptor smallSpline = descriptor(Shapes.spline(-0.5D, -0.5D, 0.5D, -0.5D, 0.0D, 0.5D));
        ShapeDescriptor largeSpline = descriptor(Shapes.spline(-1.0D, -1.0D, 1.0D, -1.0D, 0.0D, 1.0D));
        assertEquals(descriptor(Shapes.spline(-0.75D, -0.75D, 0.75D, -0.75D, 0.0D, 0.75D)), SHAPES.interpolate(smallSpline, largeSpline, 0.5D));
        ShapeDescriptor smallPath = descriptor(Shapes.path().moveTo(-0.5D, -0.5D).lineTo(0.5D, -0.5D).quadTo(0.5D, 0.5D, 0.0D, 0.5D).close().build());
        ShapeDescriptor largePath = descriptor(Shapes.path().moveTo(-1.0D, -1.0D).lineTo(1.0D, -1.0D).quadTo(1.0D, 1.0D, 0.0D, 1.0D).close().build());
        ShapeDescriptor curvedPath = descriptor(Shapes.path().moveTo(-1.0D, -1.0D).lineTo(1.0D, -1.0D).cubicTo(1.0D, 0.0D, 1.0D, 1.0D, 0.0D, 1.0D)
            .close().build());
        Path middle = (Path) SHAPES.interpolate(smallPath, largePath, 0.5D).shape();
        assertEquals(new Path.Quad(0.75D, 0.75D, 0.0D, 0.75D), middle.segments().get(2));
        assertEquals(new Path.Move(-0.75D, -0.75D), middle.segments().get(0));
        assertSame(smallPath, SHAPES.interpolate(smallPath, curvedPath, 0.25D));
        assertSame(curvedPath, SHAPES.interpolate(smallPath, curvedPath, 0.75D));
    }

    @Test
    void compositesInterpolateChildrenIndependently() {
        ShapeDescriptor from = descriptor(Shapes.circle(0.5D).union(Shapes.rectangle(1.0D, 1.0D)));
        ShapeDescriptor to = descriptor(Shapes.circle(1.0D).union(Shapes.star(5, 1.0D, 0.5D, 0.0D)));
        assertEquals(descriptor(new Union(Shapes.circle(0.625D), Shapes.rectangle(1.0D, 1.0D))), SHAPES.interpolate(from, to, 0.25D));
        assertEquals(descriptor(new Union(Shapes.circle(0.875D), Shapes.star(5, 1.0D, 0.5D, 0.0D))), SHAPES.interpolate(from, to, 0.75D));
        ShapeDescriptor turnedA = descriptor(Shapes.ellipse(0.5D, 1.0D).rotated(10.0D));
        ShapeDescriptor turnedB = descriptor(Shapes.ellipse(1.0D, 1.0D).rotated(50.0D));
        Transformed turned = assertInstanceOf(Transformed.class, SHAPES.interpolate(turnedA, turnedB, 0.5D).shape());
        assertEquals(30.0D, turned.transform().rotationDegrees(), 1.0E-9D);
        assertEquals(Shapes.ellipse(0.75D, 1.0D), turned.shape());
        ShapeDescriptor mirrored = descriptor(Shapes.ellipse(0.5D, 1.0D).flippedU());
        assertSame(turnedA, SHAPES.interpolate(turnedA, mirrored, 0.4D));
        assertSame(mirrored, SHAPES.interpolate(turnedA, mirrored, 0.6D));
    }

    @Test
    void everyIntermediateOfValidEndpointsIsValid() {
        Shape[][] pairs = {
            {Shapes.roundedRectangle(2.0D, 0.5D, 0.25D), Shapes.roundedRectangle(0.5D, 2.0D, 0.25D)},
            {Shapes.ring(1.0D, 0.0D), Shapes.ring(0.5D, 0.49D)},
            {Shapes.star(3, 2.0D, 0.01D, -720.0D), Shapes.star(32, 0.1D, 0.09D, 720.0D)},
            {Shapes.flower(2, 1.0D, 0.0D, 0.0D), Shapes.flower(32, 0.2D, 1.0D, 45.0D)},
            {Shapes.feather(2.0D, 0.1D, -1.0D), Shapes.feather(0.1D, 2.0D, 1.0D)},
            {Shapes.regularPolygon(3, 2.0D, 0.0D), Shapes.regularPolygon(64, 0.1D, 33.0D)},
            {Shapes.rectangle(4.0D, 0.1D), Shapes.rectangle(0.1D, 4.0D)},
            {Shapes.heart(0.1D).subtract(Shapes.circle(0.05D)), Shapes.heart(2.0D).subtract(Shapes.circle(1.0D))},
            {Shapes.circle(1.0D).intersect(Shapes.rectangle(1.0D, 2.0D)).scaled(0.5D, 2.0D), Shapes.circle(0.2D).intersect(Shapes.rectangle(3.0D, 0.5D)).rotated(170.0D)}
        };
        for (Shape[] pair : pairs) {
            ShapeDescriptor from = descriptor(pair[0]);
            ShapeDescriptor to = descriptor(pair[1]);
            for (int step = 0; step <= 100; step++) {
                ShapeDescriptor sample = SHAPES.interpolate(from, to, step / 100.0D);
                assertEquals(sample, ShapeDescriptor.decode(sample.encode()));
            }
        }
    }

    private static ShapeDescriptor descriptor(Shape shape) {
        return ShapeDescriptor.of(shape, FitMode.CONTAIN);
    }
}
