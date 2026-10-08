package art.arcane.optics.shape;

import java.util.ArrayList;
import java.util.List;

import art.arcane.optics.internal.shape.MarchingSquares;
import art.arcane.optics.internal.shape.PolylineDistance;

final class ShapeMath {
    static final double MAX_EXTENT = 4.0D;
    static final double MAX_RADIUS = 2.0D;
    static final double FIT_TOLERANCE = 1.0E-6D;
    private static final int MAX_ARC_SEGMENTS = 4096;
    private static final int MIN_TRACE_CELLS = 16;
    private static final int MAX_TRACE_CELLS = 1024;

    private ShapeMath() {
    }

    static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value + 0.0D;
    }

    static double positive(double value, double max, String name) {
        if (!Double.isFinite(value) || !(value > 0.0D) || value > max) {
            throw new IllegalArgumentException(name + " must be in (0, " + format(max) + "], got " + value);
        }
        return value;
    }

    static double closed(double value, double min, double max, String name) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " must be in [" + format(min) + ", " + format(max) + "], got " + value);
        }
        return value + 0.0D;
    }

    static int count(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be in " + min + ".." + max + ", got " + value);
        }
        return value;
    }

    static double tolerance(double tolerance) {
        if (!Double.isFinite(tolerance) || !(tolerance > 0.0D)) {
            throw new IllegalArgumentException("Outline tolerance must be positive and finite");
        }
        return tolerance;
    }

    static double cos(double degrees) {
        double turns = degrees / 90.0D;
        if (turns == Math.rint(turns) && Math.abs(turns) < 1.0E15D) {
            return switch ((int) Math.floorMod((long) turns, 4L)) {
                case 0 -> 1.0D;
                case 2 -> -1.0D;
                default -> 0.0D;
            };
        }
        return StrictMath.cos(Math.toRadians(degrees));
    }

    static double sin(double degrees) {
        double turns = degrees / 90.0D;
        if (turns == Math.rint(turns) && Math.abs(turns) < 1.0E15D) {
            return switch ((int) Math.floorMod((long) turns, 4L)) {
                case 1 -> 1.0D;
                case 3 -> -1.0D;
                default -> 0.0D;
            };
        }
        return StrictMath.sin(Math.toRadians(degrees));
    }

    static int arcSegments(double radius, double angle, double tolerance) {
        if (tolerance >= radius) {
            return Math.max(1, (int) Math.ceil(angle / (Math.PI * 0.5D)));
        }
        double step = 2.0D * StrictMath.acos(1.0D - tolerance / radius);
        int segments = (int) Math.ceil(angle / step);
        return Math.max(1, Math.min(MAX_ARC_SEGMENTS, segments));
    }

    static double box(double qx, double qy) {
        double outsideX = Math.max(qx, 0.0D);
        double outsideY = Math.max(qy, 0.0D);
        return Math.sqrt(outsideX * outsideX + outsideY * outsideY) + Math.min(Math.max(qx, qy), 0.0D);
    }

    static Outline counterClockwise(double[] loop) {
        Outline outline = Outline.of(loop);
        return outline.clockwise() ? outline.reversed() : outline;
    }

    static Outline clockwise(double[] loop) {
        Outline outline = Outline.of(loop);
        return outline.clockwise() ? outline : outline.reversed();
    }

    static List<Outline> nested(List<double[]> loops) {
        List<Outline> outlines = new ArrayList<Outline>(loops.size());
        for (int index = 0; index < loops.size(); index++) {
            double[] loop = loops.get(index);
            int depth = 0;
            for (int other = 0; other < loops.size(); other++) {
                if (other != index && PolylineDistance.contains(loops.get(other), loop[0], loop[1])) {
                    depth++;
                }
            }
            outlines.add((depth & 1) == 0 ? counterClockwise(loop) : clockwise(loop));
        }
        return List.copyOf(outlines);
    }

    static double[] pointPairs(double[] points, int minPoints, int maxPoints, String name) {
        if ((points.length & 1) != 0) {
            throw new IllegalArgumentException(name + " points need u,v pairs");
        }
        int count = points.length >> 1;
        if (count < minPoints || count > maxPoints) {
            throw new IllegalArgumentException(name + " needs " + minPoints + ".." + maxPoints + " points, got " + count);
        }
        double[] copy = new double[points.length];
        for (int index = 0; index < points.length; index++) {
            copy[index] = finite(points[index], name + " point");
        }
        return copy;
    }

    static List<Outline> traced(Shape shape, double tolerance) {
        tolerance(tolerance);
        Bounds2 bounds = shape.bounds();
        if (bounds.isEmpty()) {
            return List.of();
        }
        Bounds2 region = bounds.grown(Math.max(tolerance * 2.0D, Math.max(bounds.width(), bounds.height()) * 0.01D));
        int cellsU = traceCells(region.width(), tolerance);
        int cellsV = traceCells(region.height(), tolerance);
        List<double[]> loops = MarchingSquares.trace(shape::signedDistance, region.minU(), region.minV(),
            region.width() / cellsU, region.height() / cellsV, cellsU, cellsV, true);
        List<Outline> outlines = new ArrayList<Outline>(loops.size());
        for (double[] loop : loops) {
            outlines.add(Outline.of(loop));
        }
        return List.copyOf(outlines);
    }

    static double maxStretch(PlaneTransform transform) {
        double a = transform.a();
        double b = transform.b();
        double c = transform.c();
        double d = transform.d();
        double sum = a * a + b * b + c * c + d * d;
        double determinant = a * d - b * c;
        double root = Math.sqrt(Math.max(0.0D, sum * sum - 4.0D * determinant * determinant));
        return Math.sqrt((sum + root) * 0.5D);
    }

    static double minStretch(PlaneTransform transform) {
        double a = transform.a();
        double b = transform.b();
        double c = transform.c();
        double d = transform.d();
        double sum = a * a + b * b + c * c + d * d;
        double determinant = a * d - b * c;
        double root = Math.sqrt(Math.max(0.0D, sum * sum - 4.0D * determinant * determinant));
        return Math.sqrt(Math.max(0.0D, (sum - root) * 0.5D));
    }

    static Bounds2 rotatedBounds(Bounds2 bounds, double degrees) {
        return degrees == 0.0D ? bounds : bounds.transformed(PlaneTransform.rotation(degrees));
    }

    private static int traceCells(double extent, double tolerance) {
        double cells = Math.ceil(extent / tolerance);
        return (int) Math.max(MIN_TRACE_CELLS, Math.min(MAX_TRACE_CELLS, cells));
    }

    static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
