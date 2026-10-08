package art.arcane.optics.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import art.arcane.optics.internal.shape.Flatten;
import art.arcane.optics.internal.shape.PointBuffer;
import art.arcane.optics.internal.shape.PolylineDistance;

public record Path(List<Segment> segments) implements Shape {
    static final int MAX_SEGMENTS = 512;

    public Path {
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        validate(segments);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Bounds2 bounds() {
        double minU = Double.POSITIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY;
        double maxU = Double.NEGATIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;
        for (Segment segment : segments) {
            double[] coordinates = coordinates(segment);
            for (int index = 0; index < coordinates.length; index += 2) {
                minU = Math.min(minU, coordinates[index]);
                maxU = Math.max(maxU, coordinates[index]);
                minV = Math.min(minV, coordinates[index + 1]);
                maxV = Math.max(maxV, coordinates[index + 1]);
            }
        }
        return new Bounds2(minU, minV, maxU, maxV);
    }

    @Override
    public boolean contains(double u, double v) {
        int crossings = 0;
        double startU = 0.0D;
        double startV = 0.0D;
        double currentU = 0.0D;
        double currentV = 0.0D;
        for (int index = 0; index < segments.size(); index++) {
            switch (segments.get(index)) {
                case Move move -> {
                    startU = move.u();
                    startV = move.v();
                    currentU = startU;
                    currentV = startV;
                }
                case Line line -> {
                    crossings += PolylineDistance.crosses(u, v, currentU, currentV, line.u(), line.v()) ? 1 : 0;
                    currentU = line.u();
                    currentV = line.v();
                }
                case Quad quad -> {
                    crossings += Flatten.quadCrossings(u, v, currentU, currentV, quad.cu(), quad.cv(), quad.u(), quad.v(), 0);
                    currentU = quad.u();
                    currentV = quad.v();
                }
                case Cubic cubic -> {
                    crossings += Flatten.cubicCrossings(u, v, currentU, currentV, cubic.c1u(), cubic.c1v(), cubic.c2u(), cubic.c2v(),
                        cubic.u(), cubic.v(), 0);
                    currentU = cubic.u();
                    currentV = cubic.v();
                }
                case Close _ -> {
                    crossings += PolylineDistance.crosses(u, v, currentU, currentV, startU, startV) ? 1 : 0;
                    currentU = startU;
                    currentV = startV;
                }
            }
        }
        return (crossings & 1) != 0;
    }

    @Override
    public double signedDistance(double u, double v) {
        double best = Double.POSITIVE_INFINITY;
        double startU = 0.0D;
        double startV = 0.0D;
        double currentU = 0.0D;
        double currentV = 0.0D;
        for (int index = 0; index < segments.size(); index++) {
            switch (segments.get(index)) {
                case Move move -> {
                    startU = move.u();
                    startV = move.v();
                    currentU = startU;
                    currentV = startV;
                }
                case Line line -> {
                    best = Math.min(best, PolylineDistance.segmentDistanceSquared(u, v, currentU, currentV, line.u(), line.v()));
                    currentU = line.u();
                    currentV = line.v();
                }
                case Quad quad -> {
                    best = Flatten.quadDistanceSquared(u, v, currentU, currentV, quad.cu(), quad.cv(), quad.u(), quad.v(), best, 0);
                    currentU = quad.u();
                    currentV = quad.v();
                }
                case Cubic cubic -> {
                    best = Flatten.cubicDistanceSquared(u, v, currentU, currentV, cubic.c1u(), cubic.c1v(), cubic.c2u(), cubic.c2v(),
                        cubic.u(), cubic.v(), best, 0);
                    currentU = cubic.u();
                    currentV = cubic.v();
                }
                case Close _ -> {
                    best = Math.min(best, PolylineDistance.segmentDistanceSquared(u, v, currentU, currentV, startU, startV));
                    currentU = startU;
                    currentV = startV;
                }
            }
        }
        double distance = Math.sqrt(best);
        return contains(u, v) ? -distance : distance;
    }

    @Override
    public List<Outline> outlines(double tolerance) {
        ShapeMath.tolerance(tolerance);
        List<double[]> loops = new ArrayList<double[]>();
        PointBuffer buffer = new PointBuffer(64);
        double currentU = 0.0D;
        double currentV = 0.0D;
        for (Segment segment : segments) {
            switch (segment) {
                case Move move -> {
                    buffer.clear();
                    buffer.add(move.u(), move.v());
                    currentU = move.u();
                    currentV = move.v();
                }
                case Line line -> {
                    buffer.addDistinct(line.u(), line.v());
                    currentU = line.u();
                    currentV = line.v();
                }
                case Quad quad -> {
                    Flatten.quad(buffer, currentU, currentV, quad.cu(), quad.cv(), quad.u(), quad.v(), tolerance, 0);
                    currentU = quad.u();
                    currentV = quad.v();
                }
                case Cubic cubic -> {
                    Flatten.cubic(buffer, currentU, currentV, cubic.c1u(), cubic.c1v(), cubic.c2u(), cubic.c2v(), cubic.u(), cubic.v(),
                        tolerance, 0);
                    currentU = cubic.u();
                    currentV = cubic.v();
                }
                case Close _ -> {
                    double[] loop = buffer.closedLoop();
                    if (loop.length >= 6) {
                        loops.add(loop);
                    }
                }
            }
        }
        return ShapeMath.nested(loops);
    }

    private static void validate(List<Segment> segments) {
        if (segments.isEmpty() || segments.size() > MAX_SEGMENTS) {
            throw new IllegalArgumentException("A path needs 1.." + MAX_SEGMENTS + " segments, got " + segments.size());
        }
        boolean open = false;
        int drawn = 0;
        for (Segment segment : segments) {
            Objects.requireNonNull(segment, "segment");
            if (segment instanceof Move) {
                if (open) {
                    throw new IllegalArgumentException("Every path subpath must be closed before the next moveTo");
                }
                open = true;
                drawn = 0;
                continue;
            }
            if (!open) {
                throw new IllegalArgumentException("Path segments must follow a moveTo");
            }
            if (segment instanceof Close) {
                if (drawn == 0) {
                    throw new IllegalArgumentException("A path subpath needs at least one drawing segment");
                }
                open = false;
                continue;
            }
            drawn++;
        }
        if (open) {
            throw new IllegalArgumentException("Every path subpath must be closed");
        }
    }

    private static double[] coordinates(Segment segment) {
        return switch (segment) {
            case Move move -> new double[] {move.u(), move.v()};
            case Line line -> new double[] {line.u(), line.v()};
            case Quad quad -> new double[] {quad.cu(), quad.cv(), quad.u(), quad.v()};
            case Cubic cubic -> new double[] {cubic.c1u(), cubic.c1v(), cubic.c2u(), cubic.c2v(), cubic.u(), cubic.v()};
            case Close _ -> new double[0];
        };
    }

    public sealed interface Segment permits Move, Line, Quad, Cubic, Close {
    }

    public record Move(double u, double v) implements Segment {
        public Move {
            u = ShapeMath.finite(u, "Path u");
            v = ShapeMath.finite(v, "Path v");
        }
    }

    public record Line(double u, double v) implements Segment {
        public Line {
            u = ShapeMath.finite(u, "Path u");
            v = ShapeMath.finite(v, "Path v");
        }
    }

    public record Quad(double cu, double cv, double u, double v) implements Segment {
        public Quad {
            cu = ShapeMath.finite(cu, "Path control u");
            cv = ShapeMath.finite(cv, "Path control v");
            u = ShapeMath.finite(u, "Path u");
            v = ShapeMath.finite(v, "Path v");
        }
    }

    public record Cubic(double c1u, double c1v, double c2u, double c2v, double u, double v) implements Segment {
        public Cubic {
            c1u = ShapeMath.finite(c1u, "Path control u");
            c1v = ShapeMath.finite(c1v, "Path control v");
            c2u = ShapeMath.finite(c2u, "Path control u");
            c2v = ShapeMath.finite(c2v, "Path control v");
            u = ShapeMath.finite(u, "Path u");
            v = ShapeMath.finite(v, "Path v");
        }
    }

    public record Close() implements Segment {
    }

    public static final class Builder {
        private final List<Segment> segments = new ArrayList<Segment>();
        private boolean open;
        private int drawn;

        public Builder moveTo(double u, double v) {
            closeOpen();
            segments.add(new Move(u, v));
            open = true;
            drawn = 0;
            return this;
        }

        public Builder lineTo(double u, double v) {
            return draw(new Line(u, v));
        }

        public Builder quadTo(double cu, double cv, double u, double v) {
            return draw(new Quad(cu, cv, u, v));
        }

        public Builder cubicTo(double c1u, double c1v, double c2u, double c2v, double u, double v) {
            return draw(new Cubic(c1u, c1v, c2u, c2v, u, v));
        }

        public Builder close() {
            if (!open) {
                throw new IllegalStateException("close needs an open subpath");
            }
            closeOpen();
            return this;
        }

        public Path build() {
            closeOpen();
            return new Path(segments);
        }

        private Builder draw(Segment segment) {
            if (!open) {
                throw new IllegalStateException("A path subpath must start with moveTo");
            }
            segments.add(segment);
            drawn++;
            return this;
        }

        private void closeOpen() {
            if (!open) {
                return;
            }
            if (drawn == 0) {
                segments.removeLast();
            } else {
                segments.add(new Close());
            }
            open = false;
        }
    }
}
