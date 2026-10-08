package art.arcane.optics.math;

public record Vec2d(double u, double v) {
    public static final Vec2d ZERO = new Vec2d(0.0D, 0.0D);

    public Vec2d add(Vec2d other) {
        return new Vec2d(u + other.u, v + other.v);
    }

    public Vec2d subtract(Vec2d other) {
        return new Vec2d(u - other.u, v - other.v);
    }

    public Vec2d multiply(double scalar) {
        return new Vec2d(u * scalar, v * scalar);
    }

    public double dot(Vec2d other) {
        return u * other.u + v * other.v;
    }

    public double cross(Vec2d other) {
        return u * other.v - v * other.u;
    }

    public double length() {
        return Math.sqrt(u * u + v * v);
    }

    public double lengthSquared() {
        return u * u + v * v;
    }

    public double distance(Vec2d other) {
        double deltaU = u - other.u;
        double deltaV = v - other.v;
        return Math.sqrt(deltaU * deltaU + deltaV * deltaV);
    }

    public Vec2d normalize() {
        double length = length();
        return new Vec2d(u / length, v / length);
    }

    public Vec2d lerp(Vec2d to, double t) {
        return new Vec2d(u + (to.u - u) * t, v + (to.v - v) * t);
    }

    public Vec2d rotated(double radians) {
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Vec2d(u * cosine - v * sine, u * sine + v * cosine);
    }

    public Vec2d perpendicular() {
        return new Vec2d(-v, u);
    }
}
