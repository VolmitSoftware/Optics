package art.arcane.optics.crossing;

import java.util.Objects;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.math.Angles.Look;
import art.arcane.optics.math.Box;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.transform.Quaternion;

public record GravityFrame(AxisPermutation orientation) {
    public static final GravityFrame WORLD = new GravityFrame(AxisPermutation.IDENTITY);

    public GravityFrame {
        Objects.requireNonNull(orientation, "orientation");
        if (orientation.reflects()) {
            throw new IllegalArgumentException("Gravity frame must be a rotation: " + orientation);
        }
    }

    public Face down() {
        return orientation.face(Face.D);
    }

    public Face up() {
        return orientation.face(Face.U);
    }

    public Vec3d toWorld(Vec3d vector) {
        requireVector(vector, "vector");
        double[] out = new double[3];
        toWorldInto(vector.x(), vector.y(), vector.z(), out);
        return Vec3d.of(out);
    }

    public Vec3d toLocal(Vec3d vector) {
        requireVector(vector, "vector");
        double[] out = new double[3];
        toLocalInto(vector.x(), vector.y(), vector.z(), out);
        return Vec3d.of(out);
    }

    public void toWorldInto(double x, double y, double z, double[] out3) {
        orientation.vectorInto(x, y, z, out3);
    }

    public void toLocalInto(double x, double y, double z, double[] out3) {
        orientation.inverse().vectorInto(x, y, z, out3);
    }

    public Vec3d projectMovement(Vec3d worldVector) {
        requireVector(worldVector, "worldVector");
        Face up = up();
        double vertical = worldVector.component(up.axisIndex());
        return new Vec3d(worldVector.x() - vertical * Math.abs(up.x()), worldVector.y() - vertical * Math.abs(up.y()),
            worldVector.z() - vertical * Math.abs(up.z()));
    }

    public Vec3d acceleration(double magnitude) {
        requireNonnegative(magnitude, "magnitude");
        return down().toVector().multiply(magnitude);
    }

    public Vec3d jump(double speed) {
        requireNonnegative(speed, "speed");
        return up().toVector().multiply(speed);
    }

    public LookTransfer look(float yaw, float pitch) {
        requireAngle(yaw, "yaw");
        requireAngle(pitch, "pitch");
        return LookTransfer.of(new Look(yaw, pitch), orientation);
    }

    public Quaternion modelRotation(float bodyYaw) {
        requireAngle(bodyYaw, "bodyYaw");
        return Quaternion.of(orientation).multiply(Quaternion.axisAngleDegrees(Vec3d.UNIT_Y, -bodyYaw));
    }

    public Vec3d eyePosition(Vec3d feet, double eyeHeight) {
        requireVector(feet, "feet");
        requireNonnegative(eyeHeight, "eyeHeight");
        return feet.add(up().toVector().multiply(eyeHeight));
    }

    public Box bounds(Vec3d feet, double width, double height) {
        requireVector(feet, "feet");
        requirePositive(width, "width");
        requirePositive(height, "height");
        double halfWidth = width * 0.5D;
        Face up = up();
        return new Box(feet.x() + minimumOffset(up.x(), halfWidth, height), feet.x() + maximumOffset(up.x(), halfWidth, height),
            feet.y() + minimumOffset(up.y(), halfWidth, height), feet.y() + maximumOffset(up.y(), halfWidth, height),
            feet.z() + minimumOffset(up.z(), halfWidth, height), feet.z() + maximumOffset(up.z(), halfWidth, height));
    }

    public GravityFrame transformed(AxisPermutation portalRotation) {
        Objects.requireNonNull(portalRotation, "portalRotation");
        return new GravityFrame(portalRotation.compose(orientation));
    }

    private static void requireVector(Vec3d vector, String name) {
        Objects.requireNonNull(vector, name);
        if (!vector.isFinite()) {
            throw new IllegalArgumentException(name + " must be finite: " + vector);
        }
    }

    private static void requireNonnegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and nonnegative: " + value);
        }
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and positive: " + value);
        }
    }

    private static void requireAngle(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }

    private static double minimumOffset(int upComponent, double halfWidth, double height) {
        return upComponent == 0 ? -halfWidth : Math.min(0, upComponent) * height;
    }

    private static double maximumOffset(int upComponent, double halfWidth, double height) {
        return upComponent == 0 ? halfWidth : Math.max(0, upComponent) * height;
    }
}
