package art.arcane.optics.shape;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

final class ShapeDescriptorCodecTest {
    @Test
    void everyKindRoundTripsByBytes() {
        List<Shape> shapes = new ArrayList<Shape>(ShapeFixtures.presets().values());
        shapes.add(Path.builder().moveTo(-1.0D, -1.0D).lineTo(1.0D, -1.0D).quadTo(1.0D, 1.0D, 0.0D, 1.0D)
            .cubicTo(-0.5D, 1.0D, -1.0D, 0.5D, -1.0D, 0.0D).build());
        shapes.add(Ellipse.circle(0.5D).union(new Rectangle(0.4D, 1.2D)));
        shapes.add(Ellipse.circle(0.5D).intersect(new Rectangle(0.4D, 1.2D)));
        shapes.add(Ellipse.circle(0.9D).subtract(new Star(5, 0.5D, 0.2D, 0.0D)));
        shapes.add(new Heart(0.9D, 10.0D).rotated(33.3D).scaled(0.7D, 0.9D).translated(0.1D, -0.2D));
        for (Shape shape : shapes) {
            for (FitMode fit : FitMode.values()) {
                ShapeDescriptor descriptor = ShapeDescriptor.of(shape, fit);
                byte[] bytes = descriptor.encode();
                ShapeDescriptor decoded = ShapeDescriptor.decode(bytes);
                assertArrayEquals(bytes, decoded.encode(), Shapes.format(shape));
                assertEquals(descriptor, decoded);
                assertEquals(descriptor.hashCode(), decoded.hashCode());
                assertSame(fit, decoded.fit());
                assertEquals(bytes.length, descriptor.encodedSize());
                assertEquals(ShapeDescriptor.decode(decoded.encode()).shape(), decoded.shape());
            }
        }
    }

    @Test
    void canonicalLayoutIsLittleEndianPreorder() {
        byte[] circle = ShapeDescriptor.of(Ellipse.circle(1.0D), FitMode.CONTAIN).encode();
        assertArrayEquals(new byte[] {0, 2, 0, 0, (byte) 0x80, 0x3F, 0, 0, (byte) 0x80, 0x3F}, circle);
        byte[] full = ShapeDescriptor.FULL.encode();
        assertArrayEquals(new byte[] {2, 0, 0, 0, 0, 0x40, 0, 0, 0, 0x40}, full);
        byte[] union = ShapeDescriptor.of(new Rectangle(1.0D, 1.0D).union(new Ring(1.0D, 0.5D)), FitMode.COVER).encode();
        assertEquals(1, union[0]);
        assertEquals(12, union[1]);
        assertEquals(0, union[2]);
        assertEquals(8, union[11]);
        ShapeDescriptor counted = ShapeDescriptor.of(new Polygon(new double[] {0.0D, 0.0D, 1.0D, 0.0D, 0.0D, 1.0D})
            .union(Ellipse.circle(0.5D)).rotated(10.0D), FitMode.CONTAIN);
        assertEquals(4, counted.nodeCount());
        assertEquals(3, counted.depth());
        assertEquals(3, counted.pointCount());
    }

    @Test
    void fullIsTheStretchedUnitRectangle() {
        assertTrue(ShapeDescriptor.FULL.isFull());
        assertTrue(ShapeDescriptor.of(new Rectangle(2.0D, 2.0D), FitMode.STRETCH).isFull());
        assertFalse(ShapeDescriptor.of(new Rectangle(2.0D, 2.0D), FitMode.CONTAIN).isFull());
        assertFalse(ShapeDescriptor.of(Ellipse.circle(1.0D), FitMode.STRETCH).isFull());
        assertSame(ShapeDescriptor.FULL, ShapeDescriptor.decode(new byte[0]));
        assertNotEquals(ShapeDescriptor.FULL, ShapeDescriptor.of(Ellipse.circle(1.0D), FitMode.STRETCH));
    }

    @Test
    void limitsAreEnforcedOnEncodeAndDecode() {
        IllegalArgumentException bytes = assertThrows(IllegalArgumentException.class,
            () -> ShapeDescriptor.of(new Polygon(new double[600]), FitMode.CONTAIN));
        assertTrue(bytes.getMessage().contains("bytes"), bytes.getMessage());
        IllegalArgumentException points = assertThrows(IllegalArgumentException.class,
            () -> ShapeDescriptor.of(new Polygon(new double[600]).union(new Polygon(new double[428])), FitMode.CONTAIN));
        assertTrue(points.getMessage().contains("points"), points.getMessage());
        IllegalArgumentException nodes = assertThrows(IllegalArgumentException.class,
            () -> ShapeDescriptor.of(balanced(33), FitMode.CONTAIN));
        assertTrue(nodes.getMessage().contains("nodes"), nodes.getMessage());
        ShapeDescriptor.of(balanced(32), FitMode.CONTAIN);
        Shape deep = Ellipse.circle(1.0D);
        for (int level = 0; level < 7; level++) {
            deep = new Transformed(deep, PlaneTransform.translation(0.01D, 0.0D));
        }
        assertEquals(8, ShapeDescriptor.of(deep, FitMode.CONTAIN).depth());
        Shape deeper = new Transformed(deep, PlaneTransform.translation(0.01D, 0.0D));
        IllegalArgumentException depth = assertThrows(IllegalArgumentException.class, () -> ShapeDescriptor.of(deeper, FitMode.CONTAIN));
        assertTrue(depth.getMessage().contains("deeper"), depth.getMessage());
    }

    @Test
    void malformedBytesAreRejected() {
        byte[] circle = ShapeDescriptor.of(Ellipse.circle(1.0D), FitMode.CONTAIN).encode();
        assertThrows(IllegalArgumentException.class, () -> ShapeDescriptor.decode(new byte[] {0}));
        assertThrows(IllegalArgumentException.class, () -> ShapeDescriptor.decode(new byte[] {3, 2, 0, 0, (byte) 0x80, 0x3F, 0, 0, (byte) 0x80, 0x3F}));
        assertThrows(IllegalArgumentException.class, () -> ShapeDescriptor.decode(new byte[] {0, 16}));
        byte[] trailing = new byte[circle.length + 1];
        System.arraycopy(circle, 0, trailing, 0, circle.length);
        assertThrows(IllegalArgumentException.class, () -> ShapeDescriptor.decode(trailing));
        byte[] negative = circle.clone();
        negative[5] = (byte) 0xBF;
        assertThrows(IllegalArgumentException.class, () -> ShapeDescriptor.decode(negative));
        assertThrows(IllegalArgumentException.class, () -> ShapeDescriptor.decode(new byte[ShapeDescriptor.MAX_BYTES + 1]));
        Random random = new Random(99L);
        byte[] base = ShapeDescriptor.of(new Heart(0.9D, 10.0D).union(new Polygon(new double[] {0.0D, 0.0D, 1.0D, 0.0D, 0.0D, 1.0D}))
            .rotated(20.0D), FitMode.COVER).encode();
        for (int trial = 0; trial < 4000; trial++) {
            byte[] mutated = base.clone();
            mutated[random.nextInt(mutated.length)] ^= (byte) (1 << random.nextInt(8));
            byte[] cut = Arrays.copyOf(mutated, random.nextInt(mutated.length + 1));
            try {
                ShapeDescriptor.decode(cut);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage() != null);
            }
        }
    }

    private static Shape balanced(int leaves) {
        if (leaves == 1) {
            return Ellipse.circle(0.5D);
        }
        int left = leaves / 2;
        return balanced(left).union(balanced(leaves - left));
    }
}
