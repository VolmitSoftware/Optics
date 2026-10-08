package art.arcane.optics.shape;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

import art.arcane.optics.internal.shape.ShapeCodec;
import art.arcane.optics.internal.shape.ShapeGrammar;

public final class ShapeDescriptor {
    public static final int MAX_BYTES = 2048;
    public static final int MAX_NODES = 64;
    public static final int MAX_DEPTH = 8;
    public static final int MAX_POINTS = 512;
    public static final ShapeDescriptor FULL = of(Shapes.FULL, FitMode.STRETCH);

    private final Shape shape;
    private final FitMode fit;
    private final byte[] bytes;
    private final int nodes;
    private final int depth;
    private final int points;

    private ShapeDescriptor(Shape shape, FitMode fit, ShapeCodec.Encoded encoded) {
        this.shape = shape;
        this.fit = fit;
        this.bytes = encoded.bytes();
        this.nodes = encoded.nodes();
        this.depth = encoded.depth();
        this.points = encoded.points();
    }

    public static ShapeDescriptor of(Shape shape, FitMode fit) {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(fit, "fit");
        ShapeCodec.Encoded encoded = ShapeCodec.encode(shape, fit);
        ShapeCodec.decode(encoded.bytes());
        return new ShapeDescriptor(shape, fit, encoded);
    }

    public static ShapeDescriptor decode(byte[] bytes) {
        if (bytes != null && bytes.length == 0) {
            return FULL;
        }
        ShapeCodec.Decoded decoded = ShapeCodec.decode(bytes);
        return new ShapeDescriptor(decoded.shape(), decoded.fit(), ShapeCodec.encode(decoded.shape(), decoded.fit()));
    }

    public static ShapeDescriptor parse(String text) {
        ShapeGrammar.Parsed parsed = ShapeGrammar.parse(text, true);
        FitMode fit = parsed.fit() != null ? parsed.fit() : defaultFit(parsed.shape());
        return of(parsed.shape(), fit);
    }

    public Shape shape() {
        return shape;
    }

    public FitMode fit() {
        return fit;
    }

    public boolean isFull() {
        return this == FULL || Arrays.equals(bytes, FULL.bytes);
    }

    public byte[] encode() {
        return bytes.clone();
    }

    public int encodedSize() {
        return bytes.length;
    }

    public String format() {
        if (isFull()) {
            return "full";
        }
        String text = ShapeGrammar.format(shape);
        return fit == defaultFit(shape) ? text : text + "@fit(" + fit.name().toLowerCase(Locale.ROOT) + ")";
    }

    public PlaneShape fitTo(int columns, int rows) {
        return PlaneShape.fit(shape, fit, columns, rows);
    }

    public ShapeDescriptor withFit(FitMode fit) {
        return of(shape, fit);
    }

    public ShapeDescriptor transformed(PlaneTransform transform) {
        return of(shape.transformed(transform), fit);
    }

    public int nodeCount() {
        return nodes;
    }

    public int depth() {
        return depth;
    }

    public int pointCount() {
        return points;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ShapeDescriptor descriptor && Arrays.equals(bytes, descriptor.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return format();
    }

    private static FitMode defaultFit(Shape shape) {
        return Shapes.FULL.equals(shape) ? FitMode.STRETCH : FitMode.CONTAIN;
    }
}
