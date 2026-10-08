package art.arcane.optics.shape;

import art.arcane.optics.internal.shape.PolylineDistance;

public final class Outline {
    private final double[] points;
    private final double signedArea;
    private final double length;
    private final Bounds2 bounds;

    private Outline(double[] points) {
        this.points = points;
        signedArea = PolylineDistance.signedArea(points);
        length = perimeter(points);
        bounds = Bounds2.of(points);
    }

    public static Outline of(double... uv) {
        if (uv == null || uv.length < 6 || (uv.length & 1) != 0) {
            throw new IllegalArgumentException("An outline needs at least three u,v points");
        }
        for (double value : uv) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Outline points must be finite");
            }
        }
        return new Outline(uv.clone());
    }

    public int size() {
        return points.length >> 1;
    }

    public double u(int index) {
        return points[index << 1];
    }

    public double v(int index) {
        return points[(index << 1) + 1];
    }

    public double[] points() {
        return points.clone();
    }

    public boolean clockwise() {
        return signedArea < 0.0D;
    }

    public double signedArea() {
        return signedArea;
    }

    public double length() {
        return length;
    }

    public Bounds2 bounds() {
        return bounds;
    }

    public boolean contains(double u, double v) {
        return PolylineDistance.contains(points, u, v);
    }

    public double distance(double u, double v) {
        return Math.sqrt(PolylineDistance.distanceSquared(points, u, v));
    }

    public Outline reversed() {
        return new Outline(PolylineDistance.reversed(points));
    }

    public Outline transformed(PlaneTransform transform) {
        double[] mapped = new double[points.length];
        for (int index = 0; index < points.length; index += 2) {
            double u = points[index];
            double v = points[index + 1];
            mapped[index] = transform.a() * u + transform.b() * v + transform.tu();
            mapped[index + 1] = transform.c() * u + transform.d() * v + transform.tv();
        }
        return new Outline(mapped);
    }

    public int sample(double spacing, double[] out) {
        if (!(spacing > 0.0D) || !Double.isFinite(spacing)) {
            throw new IllegalArgumentException("Sample spacing must be positive and finite");
        }
        int count = (int) Math.ceil(length / spacing);
        if (count < 1) {
            count = 1;
        }
        if (out.length < count << 1) {
            throw new IllegalArgumentException("Sample output needs " + (count << 1) + " slots");
        }
        int size = size();
        int written = 0;
        double travelled = 0.0D;
        double target = 0.0D;
        for (int index = 0; index < size && written < count; index++) {
            double ax = points[index << 1];
            double ay = points[(index << 1) + 1];
            int next = index + 1 == size ? 0 : index + 1;
            double bx = points[next << 1];
            double by = points[(next << 1) + 1];
            double segment = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay));
            while (written < count && target < travelled + segment) {
                double t = segment > 0.0D ? (target - travelled) / segment : 0.0D;
                out[written << 1] = ax + (bx - ax) * t;
                out[(written << 1) + 1] = ay + (by - ay) * t;
                written++;
                target = written * spacing;
            }
            travelled += segment;
        }
        return written;
    }

    private static double perimeter(double[] points) {
        int count = points.length >> 1;
        double total = 0.0D;
        for (int index = 0; index < count; index++) {
            int next = index + 1 == count ? 0 : index + 1;
            double dx = points[next << 1] - points[index << 1];
            double dy = points[(next << 1) + 1] - points[(index << 1) + 1];
            total += Math.sqrt(dx * dx + dy * dy);
        }
        return total;
    }
}
