package art.arcane.optics.shape;

public record Bounds2(double minU, double minV, double maxU, double maxV) {
    public static final Bounds2 EMPTY = new Bounds2(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
    public static final Bounds2 UNIT = new Bounds2(-1.0D, -1.0D, 1.0D, 1.0D);

    public Bounds2 {
        if (Double.isNaN(minU) || Double.isNaN(minV) || Double.isNaN(maxU) || Double.isNaN(maxV)) {
            throw new IllegalArgumentException("Bounds must not be NaN");
        }
    }

    public static Bounds2 of(double[] uvPoints) {
        if ((uvPoints.length & 1) != 0) {
            throw new IllegalArgumentException("Points need u,v pairs");
        }
        double minU = Double.POSITIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY;
        double maxU = Double.NEGATIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < uvPoints.length; index += 2) {
            minU = Math.min(minU, uvPoints[index]);
            maxU = Math.max(maxU, uvPoints[index]);
            minV = Math.min(minV, uvPoints[index + 1]);
            maxV = Math.max(maxV, uvPoints[index + 1]);
        }
        return uvPoints.length == 0 ? EMPTY : new Bounds2(minU, minV, maxU, maxV);
    }

    public double width() {
        return isEmpty() ? 0.0D : maxU - minU;
    }

    public double height() {
        return isEmpty() ? 0.0D : maxV - minV;
    }

    public double centerU() {
        return (minU + maxU) * 0.5D;
    }

    public double centerV() {
        return (minV + maxV) * 0.5D;
    }

    public boolean isEmpty() {
        return minU > maxU || minV > maxV;
    }

    public boolean contains(double u, double v) {
        return u >= minU && u <= maxU && v >= minV && v <= maxV;
    }

    public Bounds2 union(Bounds2 other) {
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        return new Bounds2(Math.min(minU, other.minU), Math.min(minV, other.minV), Math.max(maxU, other.maxU), Math.max(maxV, other.maxV));
    }

    public Bounds2 intersect(Bounds2 other) {
        double lowU = Math.max(minU, other.minU);
        double lowV = Math.max(minV, other.minV);
        double highU = Math.min(maxU, other.maxU);
        double highV = Math.min(maxV, other.maxV);
        return lowU > highU || lowV > highV ? EMPTY : new Bounds2(lowU, lowV, highU, highV);
    }

    public Bounds2 grown(double margin) {
        if (isEmpty()) {
            return EMPTY;
        }
        Bounds2 grown = new Bounds2(minU - margin, minV - margin, maxU + margin, maxV + margin);
        return grown.isEmpty() ? EMPTY : grown;
    }

    public Bounds2 transformed(PlaneTransform transform) {
        if (isEmpty()) {
            return EMPTY;
        }
        double[] corners = {
            transform.a() * minU + transform.b() * minV + transform.tu(), transform.c() * minU + transform.d() * minV + transform.tv(),
            transform.a() * maxU + transform.b() * minV + transform.tu(), transform.c() * maxU + transform.d() * minV + transform.tv(),
            transform.a() * maxU + transform.b() * maxV + transform.tu(), transform.c() * maxU + transform.d() * maxV + transform.tv(),
            transform.a() * minU + transform.b() * maxV + transform.tu(), transform.c() * minU + transform.d() * maxV + transform.tv()};
        return of(corners);
    }
}
