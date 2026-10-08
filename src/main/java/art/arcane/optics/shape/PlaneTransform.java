package art.arcane.optics.shape;

public record PlaneTransform(double a, double b, double c, double d, double tu, double tv) {
    public static final PlaneTransform IDENTITY = new PlaneTransform(1.0D, 0.0D, 0.0D, 1.0D, 0.0D, 0.0D);
    private static final double SINGULAR = 1.0E-12D;

    public PlaneTransform {
        if (!Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(c) || !Double.isFinite(d)
            || !Double.isFinite(tu) || !Double.isFinite(tv)) {
            throw new IllegalArgumentException("Plane transform components must be finite");
        }
        a += 0.0D;
        b += 0.0D;
        c += 0.0D;
        d += 0.0D;
        tu += 0.0D;
        tv += 0.0D;
    }

    public static PlaneTransform translation(double du, double dv) {
        return new PlaneTransform(1.0D, 0.0D, 0.0D, 1.0D, du, dv);
    }

    public static PlaneTransform rotation(double degrees) {
        if (!Double.isFinite(degrees)) {
            throw new IllegalArgumentException("Rotation must be finite");
        }
        double turns = degrees / 90.0D;
        if (turns == Math.rint(turns) && Math.abs(turns) < 1.0E15D) {
            return switch ((int) Math.floorMod((long) turns, 4L)) {
                case 0 -> IDENTITY;
                case 1 -> new PlaneTransform(0.0D, -1.0D, 1.0D, 0.0D, 0.0D, 0.0D);
                case 2 -> new PlaneTransform(-1.0D, 0.0D, 0.0D, -1.0D, 0.0D, 0.0D);
                default -> new PlaneTransform(0.0D, 1.0D, -1.0D, 0.0D, 0.0D, 0.0D);
            };
        }
        double radians = Math.toRadians(degrees);
        double cos = StrictMath.cos(radians);
        double sin = StrictMath.sin(radians);
        return new PlaneTransform(cos, -sin, sin, cos, 0.0D, 0.0D);
    }

    public static PlaneTransform scale(double factor) {
        return scale(factor, factor);
    }

    public static PlaneTransform scale(double factorU, double factorV) {
        return new PlaneTransform(factorU, 0.0D, 0.0D, factorV, 0.0D, 0.0D);
    }

    public static PlaneTransform flipU() {
        return scale(-1.0D, 1.0D);
    }

    public static PlaneTransform flipV() {
        return scale(1.0D, -1.0D);
    }

    public static PlaneTransform mapping(Bounds2 from, Bounds2 to) {
        if (from.isEmpty() || to.isEmpty() || from.width() <= 0.0D || from.height() <= 0.0D) {
            throw new IllegalArgumentException("Mapping needs a source box with area");
        }
        double scaleU = to.width() / from.width();
        double scaleV = to.height() / from.height();
        return new PlaneTransform(scaleU, 0.0D, 0.0D, scaleV, to.minU() - scaleU * from.minU(), to.minV() - scaleV * from.minV());
    }

    public PlaneTransform compose(PlaneTransform inner) {
        return new PlaneTransform(a * inner.a + b * inner.c, a * inner.b + b * inner.d,
            c * inner.a + d * inner.c, c * inner.b + d * inner.d,
            a * inner.tu + b * inner.tv + tu, c * inner.tu + d * inner.tv + tv);
    }

    public PlaneTransform inverse() {
        double determinant = determinant();
        if (Math.abs(determinant) < SINGULAR) {
            throw new IllegalStateException("Plane transform is singular");
        }
        double ia = d / determinant;
        double ib = -b / determinant;
        double ic = -c / determinant;
        double id = a / determinant;
        return new PlaneTransform(ia, ib, ic, id, -(ia * tu + ib * tv), -(ic * tu + id * tv));
    }

    public double determinant() {
        return a * d - b * c;
    }

    public boolean reflects() {
        return determinant() < 0.0D;
    }

    public boolean isConformal(double tolerance) {
        return (Math.abs(a - d) <= tolerance && Math.abs(b + c) <= tolerance)
            || (Math.abs(a + d) <= tolerance && Math.abs(b - c) <= tolerance);
    }

    public double scaleU() {
        return Math.sqrt(a * a + c * c);
    }

    public double scaleV() {
        double scaleU = scaleU();
        return scaleU > 0.0D ? determinant() / scaleU : Math.sqrt(b * b + d * d);
    }

    public double rotationDegrees() {
        return Math.toDegrees(StrictMath.atan2(c, a));
    }

    public void pointInto(double u, double v, double[] out2) {
        out2[0] = a * u + b * v + tu;
        out2[1] = c * u + d * v + tv;
    }

    public PlaneTransform lerp(PlaneTransform to, double t) {
        double fromScaleU = scaleU();
        double toScaleU = to.scaleU();
        double fromAngle = fromScaleU > 0.0D ? StrictMath.atan2(c, a) : 0.0D;
        double toAngle = toScaleU > 0.0D ? StrictMath.atan2(to.c, to.a) : 0.0D;
        double delta = toAngle - fromAngle;
        delta -= 2.0D * Math.PI * Math.floor((delta + Math.PI) / (2.0D * Math.PI));
        double angle = fromAngle + delta * t;
        double scaleU = fromScaleU + (toScaleU - fromScaleU) * t;
        double scaleV = scaleV() + (to.scaleV() - scaleV()) * t;
        double shear = shear() + (to.shear() - shear()) * t;
        double cos = StrictMath.cos(angle);
        double sin = StrictMath.sin(angle);
        return new PlaneTransform(cos * scaleU, cos * shear - sin * scaleV, sin * scaleU, sin * shear + cos * scaleV,
            tu + (to.tu - tu) * t, tv + (to.tv - tv) * t);
    }

    private double shear() {
        double scaleU = scaleU();
        return scaleU > 0.0D ? (a * b + c * d) / scaleU : 0.0D;
    }
}
