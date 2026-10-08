package art.arcane.optics.internal.shape;

public final class Flatten {
    public static final double CURVE_TOLERANCE = 1.0E-4D;
    private static final int MAX_DEPTH = 18;

    private Flatten() {
    }

    public static double splineCoordinate(double[] control, int segments, int index, int axis) {
        int count = control.length >> 1;
        int span = index / segments;
        int step = index - span * segments;
        int i1 = span % count;
        if (step == 0) {
            return control[(i1 << 1) + axis];
        }
        int i0 = (i1 + count - 1) % count;
        int i2 = (i1 + 1) % count;
        int i3 = (i1 + 2) % count;
        double t0 = 0.0D;
        double t1 = t0 + knot(control, i0, i1);
        double t2 = t1 + knot(control, i1, i2);
        double t3 = t2 + knot(control, i2, i3);
        double t = t1 + (t2 - t1) * step / segments;
        double p0 = control[(i0 << 1) + axis];
        double p1 = control[(i1 << 1) + axis];
        double p2 = control[(i2 << 1) + axis];
        double p3 = control[(i3 << 1) + axis];
        double a1 = (t1 - t) / (t1 - t0) * p0 + (t - t0) / (t1 - t0) * p1;
        double a2 = (t2 - t) / (t2 - t1) * p1 + (t - t1) / (t2 - t1) * p2;
        double a3 = (t3 - t) / (t3 - t2) * p2 + (t - t2) / (t3 - t2) * p3;
        double b1 = (t2 - t) / (t2 - t0) * a1 + (t - t0) / (t2 - t0) * a2;
        double b2 = (t3 - t) / (t3 - t1) * a2 + (t - t1) / (t3 - t1) * a3;
        return (t2 - t) / (t2 - t1) * b1 + (t - t1) / (t2 - t1) * b2;
    }

    public static int quadCrossings(double px, double py, double x0, double y0, double cx, double cy, double x1, double y1, int depth) {
        if (Math.min(y0, Math.min(cy, y1)) > py || Math.max(y0, Math.max(cy, y1)) <= py
            || Math.max(x0, Math.max(cx, x1)) < px) {
            return 0;
        }
        if (depth >= MAX_DEPTH || quadFlat(x0, y0, cx, cy, x1, y1, CURVE_TOLERANCE)) {
            return PolylineDistance.crosses(px, py, x0, y0, x1, y1) ? 1 : 0;
        }
        double ax = (x0 + cx) * 0.5D;
        double ay = (y0 + cy) * 0.5D;
        double bx = (cx + x1) * 0.5D;
        double by = (cy + y1) * 0.5D;
        double mx = (ax + bx) * 0.5D;
        double my = (ay + by) * 0.5D;
        return quadCrossings(px, py, x0, y0, ax, ay, mx, my, depth + 1) + quadCrossings(px, py, mx, my, bx, by, x1, y1, depth + 1);
    }

    public static int cubicCrossings(double px, double py, double x0, double y0, double c1x, double c1y, double c2x, double c2y,
                                     double x1, double y1, int depth) {
        if (Math.min(Math.min(y0, c1y), Math.min(c2y, y1)) > py || Math.max(Math.max(y0, c1y), Math.max(c2y, y1)) <= py
            || Math.max(Math.max(x0, c1x), Math.max(c2x, x1)) < px) {
            return 0;
        }
        if (depth >= MAX_DEPTH || cubicFlat(x0, y0, c1x, c1y, c2x, c2y, x1, y1, CURVE_TOLERANCE)) {
            return PolylineDistance.crosses(px, py, x0, y0, x1, y1) ? 1 : 0;
        }
        double abx = (x0 + c1x) * 0.5D;
        double aby = (y0 + c1y) * 0.5D;
        double bcx = (c1x + c2x) * 0.5D;
        double bcy = (c1y + c2y) * 0.5D;
        double cdx = (c2x + x1) * 0.5D;
        double cdy = (c2y + y1) * 0.5D;
        double abcx = (abx + bcx) * 0.5D;
        double abcy = (aby + bcy) * 0.5D;
        double bcdx = (bcx + cdx) * 0.5D;
        double bcdy = (bcy + cdy) * 0.5D;
        double mx = (abcx + bcdx) * 0.5D;
        double my = (abcy + bcdy) * 0.5D;
        return cubicCrossings(px, py, x0, y0, abx, aby, abcx, abcy, mx, my, depth + 1)
            + cubicCrossings(px, py, mx, my, bcdx, bcdy, cdx, cdy, x1, y1, depth + 1);
    }

    public static double quadDistanceSquared(double px, double py, double x0, double y0, double cx, double cy, double x1, double y1,
                                             double best, int depth) {
        if (boxDistanceSquared(px, py, Math.min(x0, Math.min(cx, x1)), Math.min(y0, Math.min(cy, y1)),
            Math.max(x0, Math.max(cx, x1)), Math.max(y0, Math.max(cy, y1))) >= best) {
            return best;
        }
        if (depth >= MAX_DEPTH || quadFlat(x0, y0, cx, cy, x1, y1, CURVE_TOLERANCE)) {
            return Math.min(best, PolylineDistance.segmentDistanceSquared(px, py, x0, y0, x1, y1));
        }
        double ax = (x0 + cx) * 0.5D;
        double ay = (y0 + cy) * 0.5D;
        double bx = (cx + x1) * 0.5D;
        double by = (cy + y1) * 0.5D;
        double mx = (ax + bx) * 0.5D;
        double my = (ay + by) * 0.5D;
        double first = quadDistanceSquared(px, py, x0, y0, ax, ay, mx, my, best, depth + 1);
        return quadDistanceSquared(px, py, mx, my, bx, by, x1, y1, first, depth + 1);
    }

    public static double cubicDistanceSquared(double px, double py, double x0, double y0, double c1x, double c1y, double c2x, double c2y,
                                              double x1, double y1, double best, int depth) {
        if (boxDistanceSquared(px, py, Math.min(Math.min(x0, c1x), Math.min(c2x, x1)), Math.min(Math.min(y0, c1y), Math.min(c2y, y1)),
            Math.max(Math.max(x0, c1x), Math.max(c2x, x1)), Math.max(Math.max(y0, c1y), Math.max(c2y, y1))) >= best) {
            return best;
        }
        if (depth >= MAX_DEPTH || cubicFlat(x0, y0, c1x, c1y, c2x, c2y, x1, y1, CURVE_TOLERANCE)) {
            return Math.min(best, PolylineDistance.segmentDistanceSquared(px, py, x0, y0, x1, y1));
        }
        double abx = (x0 + c1x) * 0.5D;
        double aby = (y0 + c1y) * 0.5D;
        double bcx = (c1x + c2x) * 0.5D;
        double bcy = (c1y + c2y) * 0.5D;
        double cdx = (c2x + x1) * 0.5D;
        double cdy = (c2y + y1) * 0.5D;
        double abcx = (abx + bcx) * 0.5D;
        double abcy = (aby + bcy) * 0.5D;
        double bcdx = (bcx + cdx) * 0.5D;
        double bcdy = (bcy + cdy) * 0.5D;
        double mx = (abcx + bcdx) * 0.5D;
        double my = (abcy + bcdy) * 0.5D;
        double first = cubicDistanceSquared(px, py, x0, y0, abx, aby, abcx, abcy, mx, my, best, depth + 1);
        return cubicDistanceSquared(px, py, mx, my, bcdx, bcdy, cdx, cdy, x1, y1, first, depth + 1);
    }

    public static void quad(PointBuffer out, double x0, double y0, double cx, double cy, double x1, double y1, double tolerance, int depth) {
        if (depth >= MAX_DEPTH || quadFlat(x0, y0, cx, cy, x1, y1, tolerance)) {
            out.addDistinct(x1, y1);
            return;
        }
        double ax = (x0 + cx) * 0.5D;
        double ay = (y0 + cy) * 0.5D;
        double bx = (cx + x1) * 0.5D;
        double by = (cy + y1) * 0.5D;
        double mx = (ax + bx) * 0.5D;
        double my = (ay + by) * 0.5D;
        quad(out, x0, y0, ax, ay, mx, my, tolerance, depth + 1);
        quad(out, mx, my, bx, by, x1, y1, tolerance, depth + 1);
    }

    public static void cubic(PointBuffer out, double x0, double y0, double c1x, double c1y, double c2x, double c2y, double x1, double y1,
                             double tolerance, int depth) {
        if (depth >= MAX_DEPTH || cubicFlat(x0, y0, c1x, c1y, c2x, c2y, x1, y1, tolerance)) {
            out.addDistinct(x1, y1);
            return;
        }
        double abx = (x0 + c1x) * 0.5D;
        double aby = (y0 + c1y) * 0.5D;
        double bcx = (c1x + c2x) * 0.5D;
        double bcy = (c1y + c2y) * 0.5D;
        double cdx = (c2x + x1) * 0.5D;
        double cdy = (c2y + y1) * 0.5D;
        double abcx = (abx + bcx) * 0.5D;
        double abcy = (aby + bcy) * 0.5D;
        double bcdx = (bcx + cdx) * 0.5D;
        double bcdy = (bcy + cdy) * 0.5D;
        double mx = (abcx + bcdx) * 0.5D;
        double my = (abcy + bcdy) * 0.5D;
        cubic(out, x0, y0, abx, aby, abcx, abcy, mx, my, tolerance, depth + 1);
        cubic(out, mx, my, bcdx, bcdy, cdx, cdy, x1, y1, tolerance, depth + 1);
    }

    private static double knot(double[] control, int from, int to) {
        double dx = control[to << 1] - control[from << 1];
        double dy = control[(to << 1) + 1] - control[(from << 1) + 1];
        double knot = Math.sqrt(Math.sqrt(dx * dx + dy * dy));
        return knot > 0.0D ? knot : 1.0D;
    }

    private static boolean quadFlat(double x0, double y0, double cx, double cy, double x1, double y1, double tolerance) {
        double dx = x0 - 2.0D * cx + x1;
        double dy = y0 - 2.0D * cy + y1;
        return (dx * dx + dy * dy) * 0.0625D <= tolerance * tolerance;
    }

    private static boolean cubicFlat(double x0, double y0, double c1x, double c1y, double c2x, double c2y, double x1, double y1,
                                     double tolerance) {
        double ax = x0 - 2.0D * c1x + c2x;
        double ay = y0 - 2.0D * c1y + c2y;
        double bx = c1x - 2.0D * c2x + x1;
        double by = c1y - 2.0D * c2y + y1;
        double deviation = Math.max(ax * ax + ay * ay, bx * bx + by * by) * 0.5625D;
        return deviation <= tolerance * tolerance;
    }

    private static double boxDistanceSquared(double px, double py, double minX, double minY, double maxX, double maxY) {
        double dx = px < minX ? minX - px : (px > maxX ? px - maxX : 0.0D);
        double dy = py < minY ? minY - py : (py > maxY ? py - maxY : 0.0D);
        return dx * dx + dy * dy;
    }
}
