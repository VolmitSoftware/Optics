package art.arcane.optics.internal.shape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import art.arcane.optics.shape.Difference;
import art.arcane.optics.shape.Ellipse;
import art.arcane.optics.shape.Feather;
import art.arcane.optics.shape.FitMode;
import art.arcane.optics.shape.Flower;
import art.arcane.optics.shape.Heart;
import art.arcane.optics.shape.Intersection;
import art.arcane.optics.shape.Path;
import art.arcane.optics.shape.PlaneTransform;
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

public final class ShapeCodec {
    private static final int RECTANGLE = 0;
    private static final int ROUNDED_RECTANGLE = 1;
    private static final int ELLIPSE = 2;
    private static final int REGULAR_POLYGON = 3;
    private static final int STAR = 4;
    private static final int FLOWER = 5;
    private static final int HEART = 6;
    private static final int FEATHER = 7;
    private static final int RING = 8;
    private static final int POLYGON = 9;
    private static final int SPLINE = 10;
    private static final int PATH = 11;
    private static final int UNION = 12;
    private static final int INTERSECTION = 13;
    private static final int DIFFERENCE = 14;
    private static final int TRANSFORMED = 15;
    private static final int SEGMENT_MOVE = 0;
    private static final int SEGMENT_LINE = 1;
    private static final int SEGMENT_QUAD = 2;
    private static final int SEGMENT_CUBIC = 3;
    private static final int SEGMENT_CLOSE = 4;
    private static final int MAX_PATH_SEGMENTS = 512;
    private static final FitMode[] FITS = FitMode.values();

    private ShapeCodec() {
    }

    public static Encoded encode(Shape shape, FitMode fit) {
        if (shape == null || fit == null) {
            throw new IllegalArgumentException("A shape descriptor needs a shape and a fit");
        }
        Sink sink = new Sink();
        sink.u8(fit.ordinal());
        Counter counter = new Counter();
        node(sink, shape, 1, counter);
        if (sink.size > ShapeDescriptor.MAX_BYTES) {
            throw new IllegalArgumentException("Shape encodes to " + sink.size + " bytes, above " + ShapeDescriptor.MAX_BYTES);
        }
        return new Encoded(Arrays.copyOf(sink.bytes, sink.size), counter.nodes, counter.depth, counter.points);
    }

    public static Decoded decode(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Shape bytes are missing");
        }
        if (bytes.length > ShapeDescriptor.MAX_BYTES) {
            throw new IllegalArgumentException("Shape of " + bytes.length + " bytes exceeds " + ShapeDescriptor.MAX_BYTES);
        }
        try {
            Source source = new Source(bytes);
            int fit = source.u8();
            if (fit >= FITS.length) {
                throw new IllegalArgumentException("Unknown shape fit " + fit);
            }
            Shape shape = read(source, 1, new Counter());
            if (source.position != bytes.length) {
                throw new IllegalArgumentException("Shape has " + (bytes.length - source.position) + " trailing bytes");
            }
            return new Decoded(shape, FITS[fit]);
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException("Malformed shape bytes", malformed);
        }
    }

    private static void node(Sink sink, Shape shape, int depth, Counter counter) {
        counter.enter(depth);
        switch (shape) {
            case Rectangle rectangle -> {
                sink.u8(RECTANGLE);
                sink.f32(rectangle.width());
                sink.f32(rectangle.height());
            }
            case RoundedRectangle rounded -> {
                sink.u8(ROUNDED_RECTANGLE);
                sink.f32(rounded.width());
                sink.f32(rounded.height());
                sink.f32(rounded.radius());
            }
            case Ellipse ellipse -> {
                sink.u8(ELLIPSE);
                sink.f32(ellipse.radiusU());
                sink.f32(ellipse.radiusV());
            }
            case RegularPolygon polygon -> {
                sink.u8(REGULAR_POLYGON);
                sink.u8(polygon.sides());
                sink.f32(polygon.radius());
                sink.f32(polygon.rotationDegrees());
            }
            case Star star -> {
                sink.u8(STAR);
                sink.u8(star.points());
                sink.f32(star.outerRadius());
                sink.f32(star.innerRadius());
                sink.f32(star.rotationDegrees());
            }
            case Flower flower -> {
                sink.u8(FLOWER);
                sink.u8(flower.petals());
                sink.f32(flower.radius());
                sink.f32(flower.petalDepth());
                sink.f32(flower.rotationDegrees());
            }
            case Heart heart -> {
                sink.u8(HEART);
                sink.f32(heart.size());
                sink.f32(heart.rotationDegrees());
            }
            case Feather feather -> {
                sink.u8(FEATHER);
                sink.f32(feather.length());
                sink.f32(feather.width());
                sink.f32(feather.curve());
                sink.f32(feather.rotationDegrees());
            }
            case Ring ring -> {
                sink.u8(RING);
                sink.f32(ring.outerRadius());
                sink.f32(ring.innerRadius());
            }
            case Polygon polygon -> {
                double[] points = polygon.points();
                counter.points(points.length >> 1);
                sink.u8(POLYGON);
                sink.u16(points.length >> 1);
                pairs(sink, points);
            }
            case Spline spline -> {
                double[] points = spline.controlPoints();
                counter.points(points.length >> 1);
                sink.u8(SPLINE);
                sink.u16(points.length >> 1);
                sink.u8(spline.segmentsPerSpan());
                pairs(sink, points);
            }
            case Path path -> {
                sink.u8(PATH);
                sink.u16(path.segments().size());
                for (Path.Segment segment : path.segments()) {
                    segment(sink, segment, counter);
                }
            }
            case Union union -> {
                sink.u8(UNION);
                node(sink, union.left(), depth + 1, counter);
                node(sink, union.right(), depth + 1, counter);
            }
            case Intersection intersection -> {
                sink.u8(INTERSECTION);
                node(sink, intersection.left(), depth + 1, counter);
                node(sink, intersection.right(), depth + 1, counter);
            }
            case Difference difference -> {
                sink.u8(DIFFERENCE);
                node(sink, difference.left(), depth + 1, counter);
                node(sink, difference.right(), depth + 1, counter);
            }
            case Transformed transformed -> {
                PlaneTransform transform = transformed.transform();
                sink.u8(TRANSFORMED);
                sink.f32(transform.a());
                sink.f32(transform.b());
                sink.f32(transform.c());
                sink.f32(transform.d());
                sink.f32(transform.tu());
                sink.f32(transform.tv());
                node(sink, transformed.shape(), depth + 1, counter);
            }
            default -> throw new IllegalArgumentException("Unsupported shape type " + shape.getClass().getSimpleName());
        }
    }

    private static void segment(Sink sink, Path.Segment segment, Counter counter) {
        switch (segment) {
            case Path.Move move -> {
                counter.points(1);
                sink.u8(SEGMENT_MOVE);
                sink.f32(move.u());
                sink.f32(move.v());
            }
            case Path.Line line -> {
                counter.points(1);
                sink.u8(SEGMENT_LINE);
                sink.f32(line.u());
                sink.f32(line.v());
            }
            case Path.Quad quad -> {
                counter.points(2);
                sink.u8(SEGMENT_QUAD);
                sink.f32(quad.cu());
                sink.f32(quad.cv());
                sink.f32(quad.u());
                sink.f32(quad.v());
            }
            case Path.Cubic cubic -> {
                counter.points(3);
                sink.u8(SEGMENT_CUBIC);
                sink.f32(cubic.c1u());
                sink.f32(cubic.c1v());
                sink.f32(cubic.c2u());
                sink.f32(cubic.c2v());
                sink.f32(cubic.u());
                sink.f32(cubic.v());
            }
            case Path.Close _ -> sink.u8(SEGMENT_CLOSE);
        }
    }

    private static Shape read(Source source, int depth, Counter counter) {
        counter.enter(depth);
        int kind = source.u8();
        return switch (kind) {
            case RECTANGLE -> new Rectangle(source.f32(), source.f32());
            case ROUNDED_RECTANGLE -> new RoundedRectangle(source.f32(), source.f32(), source.f32());
            case ELLIPSE -> new Ellipse(source.f32(), source.f32());
            case REGULAR_POLYGON -> new RegularPolygon(source.u8(), source.f32(), source.f32());
            case STAR -> new Star(source.u8(), source.f32(), source.f32(), source.f32());
            case FLOWER -> new Flower(source.u8(), source.f32(), source.f32(), source.f32());
            case HEART -> new Heart(source.f32(), source.f32());
            case FEATHER -> new Feather(source.f32(), source.f32(), source.f32(), source.f32());
            case RING -> new Ring(source.f32(), source.f32());
            case POLYGON -> {
                int count = source.u16();
                counter.points(count);
                yield new Polygon(source.pairs(count));
            }
            case SPLINE -> {
                int count = source.u16();
                counter.points(count);
                int segments = source.u8();
                yield new Spline(source.pairs(count), segments);
            }
            case PATH -> readPath(source, counter);
            case UNION -> new Union(read(source, depth + 1, counter), read(source, depth + 1, counter));
            case INTERSECTION -> new Intersection(read(source, depth + 1, counter), read(source, depth + 1, counter));
            case DIFFERENCE -> new Difference(read(source, depth + 1, counter), read(source, depth + 1, counter));
            case TRANSFORMED -> {
                PlaneTransform transform = new PlaneTransform(source.f32(), source.f32(), source.f32(), source.f32(), source.f32(),
                    source.f32());
                yield new Transformed(read(source, depth + 1, counter), transform);
            }
            default -> throw new IllegalArgumentException("Unknown shape kind " + kind);
        };
    }

    private static Path readPath(Source source, Counter counter) {
        int count = source.u16();
        if (count > MAX_PATH_SEGMENTS) {
            throw new IllegalArgumentException("Path of " + count + " segments exceeds " + MAX_PATH_SEGMENTS);
        }
        List<Path.Segment> segments = new ArrayList<Path.Segment>(count);
        for (int index = 0; index < count; index++) {
            int type = source.u8();
            Path.Segment segment = switch (type) {
                case SEGMENT_MOVE -> {
                    counter.points(1);
                    yield new Path.Move(source.f32(), source.f32());
                }
                case SEGMENT_LINE -> {
                    counter.points(1);
                    yield new Path.Line(source.f32(), source.f32());
                }
                case SEGMENT_QUAD -> {
                    counter.points(2);
                    yield new Path.Quad(source.f32(), source.f32(), source.f32(), source.f32());
                }
                case SEGMENT_CUBIC -> {
                    counter.points(3);
                    yield new Path.Cubic(source.f32(), source.f32(), source.f32(), source.f32(), source.f32(), source.f32());
                }
                case SEGMENT_CLOSE -> new Path.Close();
                default -> throw new IllegalArgumentException("Unknown path segment " + type);
            };
            segments.add(segment);
        }
        return new Path(segments);
    }

    private static void pairs(Sink sink, double[] points) {
        for (double value : points) {
            sink.f32(value);
        }
    }

    public record Encoded(byte[] bytes, int nodes, int depth, int points) {
    }

    public record Decoded(Shape shape, FitMode fit) {
    }

    private static final class Counter {
        private int nodes;
        private int depth;
        private int points;

        private void enter(int level) {
            if (level > ShapeDescriptor.MAX_DEPTH) {
                throw new IllegalArgumentException("Shape nests deeper than " + ShapeDescriptor.MAX_DEPTH);
            }
            if (++nodes > ShapeDescriptor.MAX_NODES) {
                throw new IllegalArgumentException("Shape has more than " + ShapeDescriptor.MAX_NODES + " nodes");
            }
            depth = Math.max(depth, level);
        }

        private void points(int count) {
            points += count;
            if (points > ShapeDescriptor.MAX_POINTS) {
                throw new IllegalArgumentException("Shape has more than " + ShapeDescriptor.MAX_POINTS + " points");
            }
        }
    }

    private static final class Sink {
        private byte[] bytes = new byte[64];
        private int size;

        private void u8(int value) {
            ensure(1);
            bytes[size++] = (byte) value;
        }

        private void u16(int value) {
            ensure(2);
            bytes[size++] = (byte) value;
            bytes[size++] = (byte) (value >>> 8);
        }

        private void f32(double value) {
            int bits = Float.floatToRawIntBits((float) value);
            ensure(4);
            bytes[size++] = (byte) bits;
            bytes[size++] = (byte) (bits >>> 8);
            bytes[size++] = (byte) (bits >>> 16);
            bytes[size++] = (byte) (bits >>> 24);
        }

        private void ensure(int extra) {
            if (size + extra > bytes.length) {
                bytes = Arrays.copyOf(bytes, Math.max(size + extra, bytes.length << 1));
            }
        }
    }

    private static final class Source {
        private final byte[] bytes;
        private int position;

        private Source(byte[] bytes) {
            this.bytes = bytes;
        }

        private int u8() {
            require(1);
            return bytes[position++] & 0xFF;
        }

        private int u16() {
            require(2);
            int value = (bytes[position] & 0xFF) | ((bytes[position + 1] & 0xFF) << 8);
            position += 2;
            return value;
        }

        private double f32() {
            require(4);
            int bits = (bytes[position] & 0xFF) | ((bytes[position + 1] & 0xFF) << 8) | ((bytes[position + 2] & 0xFF) << 16)
                | ((bytes[position + 3] & 0xFF) << 24);
            position += 4;
            return Float.intBitsToFloat(bits);
        }

        private double[] pairs(int count) {
            require(count * 8);
            double[] values = new double[count << 1];
            for (int index = 0; index < values.length; index++) {
                values[index] = f32();
            }
            return values;
        }

        private void require(int count) {
            if (count < 0 || position + count > bytes.length) {
                throw new IllegalArgumentException("Shape bytes end early at " + position);
            }
        }
    }
}
