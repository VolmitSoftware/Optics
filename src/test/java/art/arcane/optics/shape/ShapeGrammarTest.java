package art.arcane.optics.shape;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

final class ShapeGrammarTest {
    private static final List<String> EXAMPLES = List.of(
        "full",
        "rectangle(width=2,height=1)",
        "rectangle(1.5, 0.75)",
        "rounded(radius=0.35)",
        "rounded(width=2,height=1.5,radius=0.5)",
        "circle",
        "circle(0.5)",
        "circle(radius=1)",
        "ellipse(radiusU=1,radiusV=0.5)",
        "polygon(sides=6)",
        "polygon(8, 1, 22.5)",
        "polygon(points=0:1;-1:-1;1:-1)",
        "star",
        "star(points=6,outer=1,inner=0.5,rotate=15)",
        "flower(petals=7,depth=0.7)",
        "heart",
        "heart(size=0.8, rotate=-10)",
        "feather",
        "feather(length=1.8,width=0.9,curve=-0.3,rotate=45)",
        "ring",
        "ring(outer=1,inner=0.4)",
        "spline(points=0:1;-1:0;0:-1;1:0,segments=12)",
        "path(d=M -1:-1 L 1:-1 Q 1:1;0:1 C -0.5:1;-1:0.5;-1:0 Z)",
        "path(M-1:-1L1:-1L0:1Z)",
        "circle-ring(outer=0.5,inner=0.2)",
        "circle + star & rectangle",
        "(circle+star)&rectangle(1.2,1.2)",
        "circle@rotate(45)@scale(0.5,1)@offset(0.1,-0.2)@flipU",
        "heart@flipV@scale(0.75)",
        "(circle-circle(0.3))@offset(-0.25, 0)",
        "  star ( points = 5 )  @ rotate ( 10 ) ");

    @Test
    void everyExampleParses() {
        for (String example : EXAMPLES) {
            Shape shape = Shapes.parse(example);
            assertTrue(shape.bounds().width() > 0.0D, example);
            Shape again = Shapes.parse(Shapes.format(shape));
            assertArrayEquals(ShapeDescriptor.of(shape, FitMode.CONTAIN).encode(), ShapeDescriptor.of(again, FitMode.CONTAIN).encode(),
                example);
        }
    }

    @Test
    void defaultsMatchTheGrammar() {
        assertEquals(Shapes.FULL, Shapes.parse("full"));
        assertEquals(new RoundedRectangle(2.0D, 2.0D, 0.25D), Shapes.parse("rounded"));
        assertEquals(Ellipse.circle(1.0D), Shapes.parse("circle"));
        assertEquals(new RegularPolygon(6, 1.0D, 0.0D), Shapes.parse("polygon"));
        assertEquals(new Star(5, 1.0D, 0.45D, 0.0D), Shapes.parse("star"));
        assertEquals(new Flower(5, 1.0D, 0.6D, 0.0D), Shapes.parse("flower"));
        assertEquals(new Heart(1.0D, 0.0D), Shapes.parse("heart"));
        assertEquals(new Feather(2.0D, 1.0D, 0.25D, 0.0D), Shapes.parse("feather"));
        assertEquals(new Ring(1.0D, 0.6D), Shapes.parse("ring"));
        assertEquals(new Rectangle(2.0D, 2.0D), Shapes.parse("rectangle"));
        assertInstanceOf(Difference.class, Shapes.parse("circle-ring"));
        Transformed offset = assertInstanceOf(Transformed.class, Shapes.parse("circle@offset(-0.5,-0.25)"));
        assertEquals(-0.5D, offset.transform().tu());
        assertEquals(-0.25D, offset.transform().tv());
    }

    @Test
    void formatIsCanonical() {
        assertEquals("circle(radius=1)", Shapes.format(Ellipse.circle(1.0D)));
        assertEquals("full", Shapes.format(Shapes.FULL));
        assertEquals("star(points=5,outer=1,inner=0.45,rotate=0)", Shapes.format(Shapes.parse("star")));
        assertEquals("circle(radius=1)@rotate(45)", Shapes.format(Ellipse.circle(1.0D).rotated(45.0D)));
        assertEquals("circle(radius=1)+(ring(outer=1,inner=0.6)&heart(size=1,rotate=0))",
            Shapes.format(Ellipse.circle(1.0D).union(new Ring(1.0D, 0.6D).intersect(new Heart(1.0D, 0.0D)))));
        assertEquals("full", ShapeDescriptor.FULL.format());
        assertEquals("circle(radius=1)@fit(cover)", ShapeDescriptor.of(Ellipse.circle(1.0D), FitMode.COVER).format());
        assertEquals("full@fit(contain)", ShapeDescriptor.of(Shapes.FULL, FitMode.CONTAIN).format());
    }

    @Test
    void descriptorTextCarriesTheFit() {
        assertSame(FitMode.CONTAIN, ShapeDescriptor.parse("circle").fit());
        assertSame(FitMode.COVER, ShapeDescriptor.parse("circle@fit(cover)").fit());
        assertSame(FitMode.STRETCH, ShapeDescriptor.parse("star+circle@fit(stretch)").fit());
        assertEquals(ShapeDescriptor.FULL, ShapeDescriptor.parse("full"));
        assertTrue(ShapeDescriptor.parse("full").isFull());
        assertEquals(ShapeDescriptor.of(Shapes.FULL, FitMode.CONTAIN), ShapeDescriptor.parse("full@fit(contain)"));
        for (String text : List.of("circle@fit(cover)", "full", "full@fit(contain)", "heart@rotate(30)@fit(stretch)")) {
            assertEquals(ShapeDescriptor.parse(text), ShapeDescriptor.parse(ShapeDescriptor.parse(text).format()), text);
        }
    }

    @Test
    void generatedShapesRoundTripThroughText() {
        Random random = new Random(0x5EEDL);
        for (int trial = 0; trial < 50; trial++) {
            Shape shape = generated(random, 0);
            ShapeDescriptor descriptor = ShapeDescriptor.of(shape, FitMode.values()[random.nextInt(3)]);
            ShapeDescriptor parsed = ShapeDescriptor.parse(descriptor.format());
            assertArrayEquals(descriptor.encode(), parsed.encode(), descriptor.format());
            assertEquals(descriptor, ShapeDescriptor.decode(descriptor.encode()));
            assertEquals(Shapes.format(ShapeDescriptor.decode(descriptor.encode()).shape()), Shapes.format(parsed.shape()));
        }
    }

    @Test
    void errorsNameTheOffendingPosition() {
        assertError("circle(radius=)", 14);
        assertError("star(points=5", 13);
        assertError("blob", 0);
        assertError("circle(radius=1,radius=2)", 16);
        assertError("circle(depth=1)", 7);
        assertError("circle+", 7);
        assertError("circle@spin(4)", 7);
        assertError("circle(radius=-1)", 0);
        assertError("polygon(sides=5.5)", 14);
        assertError("circle # ring", 7);
        assertThrows(IllegalArgumentException.class, () -> Shapes.parse("circle@fit(cover)"));
        IllegalArgumentException late = assertThrows(IllegalArgumentException.class, () -> ShapeDescriptor.parse("circle@fit(cover)+ring"));
        assertTrue(late.getMessage().contains("position 6"), late.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ShapeDescriptor.parse("(circle@fit(cover))"));
        assertThrows(IllegalArgumentException.class, () -> Shapes.parse(""));
    }

    private static void assertError(String text, int position) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> Shapes.parse(text), text);
        assertTrue(error.getMessage().contains("at position " + position), text + " -> " + error.getMessage());
    }

    private static Shape generated(Random random, int depth) {
        int pick = depth >= 2 ? random.nextInt(11) : random.nextInt(15);
        Shape shape = switch (pick) {
            case 0 -> new Rectangle(nice(random, 0.1D, 2.0D), nice(random, 0.1D, 2.0D));
            case 1 -> new RoundedRectangle(1.5D, 1.0D, nice(random, 0.0D, 0.5D));
            case 2 -> new Ellipse(nice(random, 0.1D, 1.0D), nice(random, 0.1D, 1.0D));
            case 3 -> new RegularPolygon(3 + random.nextInt(10), nice(random, 0.2D, 1.0D), nice(random, -180.0D, 180.0D));
            case 4 -> new Star(3 + random.nextInt(10), 1.0D, nice(random, 0.1D, 0.9D), nice(random, -90.0D, 90.0D));
            case 5 -> new Flower(2 + random.nextInt(10), nice(random, 0.3D, 1.0D), nice(random, 0.0D, 1.0D), nice(random, 0.0D, 72.0D));
            case 6 -> new Heart(nice(random, 0.3D, 1.0D), nice(random, -30.0D, 30.0D));
            case 7 -> new Feather(nice(random, 0.5D, 2.0D), nice(random, 0.2D, 1.0D), nice(random, -1.0D, 1.0D), nice(random, 0.0D, 360.0D));
            case 8 -> new Ring(1.0D, nice(random, 0.0D, 0.9D));
            case 9 -> new Polygon(new double[] {nice(random, -1.0D, 0.0D), -1.0D, 1.0D, nice(random, -1.0D, 0.0D), 0.0D, 1.0D});
            case 10 -> new Spline(new double[] {0.0D, 1.0D, -1.0D, nice(random, -0.5D, 0.5D), 0.0D, -1.0D, 1.0D, 0.0D}, 2 + random.nextInt(10));
            case 11 -> generated(random, depth + 1).union(generated(random, depth + 1));
            case 12 -> generated(random, depth + 1).intersect(generated(random, depth + 1));
            case 13 -> generated(random, depth + 1).subtract(generated(random, depth + 1));
            default -> Path.builder().moveTo(-1.0D, -1.0D).lineTo(1.0D, -1.0D)
                .quadTo(nice(random, 0.0D, 1.0D), 1.0D, 0.0D, 1.0D).cubicTo(-0.5D, 1.0D, -1.0D, 0.5D, -1.0D, 0.0D).build();
        };
        return switch (random.nextInt(5)) {
            case 0 -> shape.rotated(nice(random, -90.0D, 90.0D));
            case 1 -> shape.scaled(nice(random, 0.5D, 1.0D)).translated(nice(random, -0.5D, 0.5D), nice(random, -0.5D, 0.5D));
            case 2 -> shape.flippedU();
            case 3 -> shape.scaled(nice(random, 0.5D, 1.0D), nice(random, 0.5D, 1.0D)).rotated(nice(random, 0.0D, 45.0D));
            default -> shape;
        };
    }

    private static double nice(Random random, double low, double high) {
        double value = low + random.nextDouble() * (high - low);
        return Math.round(value * 1000.0D) / 1000.0D;
    }
}
