package art.arcane.optics.math;

public record Vec3d(double x, double y, double z) {
    public static final Vec3d ZERO = new Vec3d(0.0D, 0.0D, 0.0D);
    public static final Vec3d UNIT_X = new Vec3d(1.0D, 0.0D, 0.0D);
    public static final Vec3d UNIT_Y = new Vec3d(0.0D, 1.0D, 0.0D);
    public static final Vec3d UNIT_Z = new Vec3d(0.0D, 0.0D, 1.0D);

    public static Vec3d of(double[] xyz3) {
        return new Vec3d(xyz3[0], xyz3[1], xyz3[2]);
    }

    public double component(int axis) {
        return axis == 0 ? x : axis == 1 ? y : z;
    }

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    public Vec3d add(Vec3d other) {
        return new Vec3d(x + other.x, y + other.y, z + other.z);
    }

    public Vec3d subtract(Vec3d other) {
        return new Vec3d(x - other.x, y - other.y, z - other.z);
    }

    public Vec3d multiply(double scalar) {
        return new Vec3d(x * scalar, y * scalar, z * scalar);
    }

    public double dot(Vec3d other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public Vec3d normalize() {
        return multiply(1.0D / Math.sqrt(x * x + y * y + z * z));
    }

    public double distance(Vec3d other) {
        double deltaX = x - other.x;
        double deltaY = y - other.y;
        double deltaZ = z - other.z;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
    }

    public Vec3d cross(Vec3d other) {
        return new Vec3d(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public Vec3d negate() {
        return new Vec3d(-x, -y, -z);
    }

    public Vec3d multiply(Vec3d other) {
        return new Vec3d(x * other.x, y * other.y, z * other.z);
    }

    public Vec3d lerp(Vec3d to, double t) {
        return new Vec3d(x + (to.x - x) * t, y + (to.y - y) * t, z + (to.z - z) * t);
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    public void into(double[] out3) {
        out3[0] = x;
        out3[1] = y;
        out3[2] = z;
    }
}
