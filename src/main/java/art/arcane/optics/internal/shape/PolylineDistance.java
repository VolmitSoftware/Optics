package art.arcane.optics.internal.shape;

public final class PolylineDistance {
    private PolylineDistance() {
    }

    public static double segmentDistanceSquared(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared <= 0.0D ? 0.0D : ((px - ax) * dx + (py - ay) * dy) / lengthSquared;
        t = t < 0.0D ? 0.0D : (t > 1.0D ? 1.0D : t);
        double ex = ax + dx * t - px;
        double ey = ay + dy * t - py;
        return ex * ex + ey * ey;
    }

    public static boolean crosses(double px, double py, double ax, double ay, double bx, double by) {
        return (ay > py) != (by > py) && px < ax + (py - ay) * (bx - ax) / (by - ay);
    }

    public static double distanceSquared(double[] loop, double px, double py) {
        int count = loop.length >> 1;
        double best = Double.POSITIVE_INFINITY;
        double ax = loop[(count - 1) << 1];
        double ay = loop[((count - 1) << 1) + 1];
        for (int index = 0; index < count; index++) {
            double bx = loop[index << 1];
            double by = loop[(index << 1) + 1];
            double candidate = segmentDistanceSquared(px, py, ax, ay, bx, by);
            if (candidate < best) {
                best = candidate;
            }
            ax = bx;
            ay = by;
        }
        return best;
    }

    public static boolean contains(double[] loop, double px, double py) {
        int count = loop.length >> 1;
        boolean inside = false;
        double ax = loop[(count - 1) << 1];
        double ay = loop[((count - 1) << 1) + 1];
        for (int index = 0; index < count; index++) {
            double bx = loop[index << 1];
            double by = loop[(index << 1) + 1];
            if (crosses(px, py, ax, ay, bx, by)) {
                inside = !inside;
            }
            ax = bx;
            ay = by;
        }
        return inside;
    }

    public static double signedArea(double[] loop) {
        int count = loop.length >> 1;
        double area = 0.0D;
        double ax = loop[(count - 1) << 1];
        double ay = loop[((count - 1) << 1) + 1];
        for (int index = 0; index < count; index++) {
            double bx = loop[index << 1];
            double by = loop[(index << 1) + 1];
            area += ax * by - bx * ay;
            ax = bx;
            ay = by;
        }
        return area * 0.5D;
    }

    public static double[] reversed(double[] loop) {
        int count = loop.length >> 1;
        double[] out = new double[loop.length];
        for (int index = 0; index < count; index++) {
            out[index << 1] = loop[(count - 1 - index) << 1];
            out[(index << 1) + 1] = loop[((count - 1 - index) << 1) + 1];
        }
        return out;
    }
}
