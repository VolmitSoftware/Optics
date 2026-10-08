package art.arcane.optics.transform;

import java.util.Objects;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

public record Quaternion(double x, double y, double z, double w) {
    public static final Quaternion IDENTITY = new Quaternion(0.0D, 0.0D, 0.0D, 1.0D);
    private static final double OPPOSITE_TOLERANCE = 1.0E-12D;
    private static final double PERPENDICULAR_FALLBACK = 0.1D;
    private static final double SLERP_LINEAR_SINE = 1.0E-12D;
    private static final double GIMBAL_COSINE = 1.0E-9D;
    private static final double ORTHOGONAL_UP = 1.0E-9D;
    private static final int PERMUTATIONS = 48;

    public static Quaternion axisAngle(Vec3d axis, double radians) {
        double length = axis.length();
        if (!(length > 0.0D) || !Double.isFinite(length)) {
            throw new IllegalArgumentException("Rotation axis must be a finite non-zero vector: " + axis);
        }
        if (!Double.isFinite(radians)) {
            throw new IllegalArgumentException("Rotation angle must be finite: " + radians);
        }
        double half = radians * 0.5D;
        double sine = Math.sin(half) / length;
        return new Quaternion(axis.x() * sine, axis.y() * sine, axis.z() * sine, Math.cos(half));
    }

    public static Quaternion axisAngleDegrees(Vec3d axis, double degrees) {
        return axisAngle(axis, Math.toRadians(degrees));
    }

    public static Quaternion euler(EulerAngles angles) {
        EulerOrder order = angles.order();
        Quaternion first = elementary(order.first(), angles.component(order.first()));
        Quaternion second = elementary(order.second(), angles.component(order.second()));
        Quaternion third = elementary(order.third(), angles.component(order.third()));
        return first.multiply(second).multiply(third);
    }

    public static Quaternion fromTo(Vec3d from, Vec3d to) {
        double fromLength = requireDirection(from, "from");
        double toLength = requireDirection(to, "to");
        double fx = from.x() / fromLength;
        double fy = from.y() / fromLength;
        double fz = from.z() / fromLength;
        double tx = to.x() / toLength;
        double ty = to.y() / toLength;
        double tz = to.z() / toLength;
        double dot = fx * tx + fy * ty + fz * tz;
        if (1.0D + dot < OPPOSITE_TOLERANCE) {
            return halfTurnPerpendicularTo(fx, fy, fz);
        }
        return new Quaternion(fy * tz - fz * ty, fz * tx - fx * tz, fx * ty - fy * tx, 1.0D + dot).normalize();
    }

    public static Quaternion lookAlong(Vec3d forward, Vec3d up) {
        double forwardLength = requireDirection(forward, "forward");
        double fx = forward.x() / forwardLength;
        double fy = forward.y() / forwardLength;
        double fz = forward.z() / forwardLength;
        double along = up.x() * fx + up.y() * fy + up.z() * fz;
        double ux = up.x() - fx * along;
        double uy = up.y() - fy * along;
        double uz = up.z() - fz * along;
        double upLength = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (!(upLength > ORTHOGONAL_UP * Math.max(1.0D, up.length()))) {
            return fromTo(Vec3d.UNIT_Z, forward);
        }
        ux /= upLength;
        uy /= upLength;
        uz /= upLength;
        double rx = uy * fz - uz * fy;
        double ry = uz * fx - ux * fz;
        double rz = ux * fy - uy * fx;
        return fromRotationMatrix(rx, ux, fx, ry, uy, fy, rz, uz, fz);
    }

    public static Quaternion of(AxisPermutation rotation) {
        Objects.requireNonNull(rotation, "rotation");
        if (rotation.reflects()) {
            throw new IllegalArgumentException("Axis permutation is a reflection, not a rotation: " + rotation);
        }
        Face imageX = rotation.x();
        Face imageY = rotation.y();
        Face imageZ = rotation.z();
        return fromRotationMatrix(imageX.x(), imageY.x(), imageZ.x(), imageX.y(), imageY.y(), imageZ.y(), imageX.z(), imageY.z(), imageZ.z());
    }

    public static Quaternion between(Frame from, Frame to) {
        return of(AxisPermutation.between(from, to));
    }

    public double lengthSquared() {
        return x * x + y * y + z * z + w * w;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public Quaternion normalize() {
        double length = length();
        if (!(length > 0.0D)) {
            throw new IllegalStateException("Cannot normalize a zero-length quaternion: " + this);
        }
        return new Quaternion(x / length, y / length, z / length, w / length);
    }

    public Quaternion conjugate() {
        return new Quaternion(-x, -y, -z, w);
    }

    public Quaternion inverse() {
        double lengthSquared = lengthSquared();
        if (!(lengthSquared > 0.0D)) {
            throw new IllegalStateException("Cannot invert a zero-length quaternion: " + this);
        }
        return new Quaternion(-x / lengthSquared, -y / lengthSquared, -z / lengthSquared, w / lengthSquared);
    }

    public Quaternion negate() {
        return new Quaternion(-x, -y, -z, -w);
    }

    public Quaternion multiply(Quaternion other) {
        return new Quaternion(
            w * other.x + x * other.w + y * other.z - z * other.y,
            w * other.y - x * other.z + y * other.w + z * other.x,
            w * other.z + x * other.y - y * other.x + z * other.w,
            w * other.w - x * other.x - y * other.y - z * other.z);
    }

    public Vec3d rotate(Vec3d vector) {
        double[] out = new double[3];
        rotateInto(vector.x(), vector.y(), vector.z(), out);
        return new Vec3d(out[0], out[1], out[2]);
    }

    public void rotateInto(double x, double y, double z, double[] out3) {
        double inverseLengthSquared = 1.0D / lengthSquared();
        double tx = 2.0D * (this.y * z - this.z * y);
        double ty = 2.0D * (this.z * x - this.x * z);
        double tz = 2.0D * (this.x * y - this.y * x);
        double outX = x + (w * tx + this.y * tz - this.z * ty) * inverseLengthSquared;
        double outY = y + (w * ty + this.z * tx - this.x * tz) * inverseLengthSquared;
        double outZ = z + (w * tz + this.x * ty - this.y * tx) * inverseLengthSquared;
        out3[0] = outX;
        out3[1] = outY;
        out3[2] = outZ;
    }

    public double dot(Quaternion other) {
        return x * other.x + y * other.y + z * other.z + w * other.w;
    }

    public Quaternion slerp(Quaternion to, double t) {
        Quaternion from = normalize();
        Quaternion target = to.normalize();
        double sign = from.dot(target) < 0.0D ? -1.0D : 1.0D;
        double bx = target.x * sign;
        double by = target.y * sign;
        double bz = target.z * sign;
        double bw = target.w * sign;
        double dx = from.x - bx;
        double dy = from.y - by;
        double dz = from.z - bz;
        double dw = from.w - bw;
        double sx = from.x + bx;
        double sy = from.y + by;
        double sz = from.z + bz;
        double sw = from.w + bw;
        double theta = 2.0D * Math.atan2(Math.sqrt(dx * dx + dy * dy + dz * dz + dw * dw), Math.sqrt(sx * sx + sy * sy + sz * sz + sw * sw));
        double sine = Math.sin(theta);
        double weightFrom = 1.0D - t;
        double weightTo = t;
        if (sine > SLERP_LINEAR_SINE) {
            weightFrom = Math.sin((1.0D - t) * theta) / sine;
            weightTo = Math.sin(t * theta) / sine;
        }
        return new Quaternion(from.x * weightFrom + bx * weightTo, from.y * weightFrom + by * weightTo, from.z * weightFrom + bz * weightTo,
            from.w * weightFrom + bw * weightTo).normalize();
    }

    public Quaternion nlerp(Quaternion to, double t) {
        double sign = dot(to) < 0.0D ? -1.0D : 1.0D;
        double weightFrom = 1.0D - t;
        double weightTo = t * sign;
        return new Quaternion(x * weightFrom + to.x * weightTo, y * weightFrom + to.y * weightTo, z * weightFrom + to.z * weightTo,
            w * weightFrom + to.w * weightTo).normalize();
    }

    public double angle() {
        return 2.0D * Math.atan2(Math.sqrt(x * x + y * y + z * z), Math.abs(w));
    }

    public Vec3d axis() {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length == 0.0D) {
            return Vec3d.UNIT_Y;
        }
        double scale = (w < 0.0D ? -1.0D : 1.0D) / length;
        return new Vec3d(x * scale, y * scale, z * scale);
    }

    public EulerAngles euler(EulerOrder order) {
        Objects.requireNonNull(order, "order");
        double[] matrix = new double[9];
        matrixInto(matrix);
        int first = order.first();
        int second = order.second();
        int third = order.third();
        double parity = order.parity();
        double cosineMiddle = Math.hypot(matrix[first * 3 + first], matrix[first * 3 + second]);
        double middle = Math.atan2(parity * matrix[first * 3 + third], cosineMiddle);
        double outer;
        double inner;
        if (cosineMiddle > GIMBAL_COSINE) {
            outer = Math.atan2(-parity * matrix[second * 3 + third], matrix[third * 3 + third]);
            inner = Math.atan2(-parity * matrix[first * 3 + second], matrix[first * 3 + first]);
        } else {
            outer = Math.atan2(parity * matrix[third * 3 + second], matrix[second * 3 + second]);
            inner = 0.0D;
        }
        double[] degrees = new double[3];
        degrees[first] = Math.toDegrees(outer);
        degrees[second] = Math.toDegrees(middle);
        degrees[third] = Math.toDegrees(inner);
        return new EulerAngles(degrees[0], degrees[1], degrees[2], order);
    }

    public AxisPermutation permutation(double tolerance) {
        Quaternion normalized = normalize();
        AxisPermutation nearest = null;
        double nearestAngle = Double.POSITIVE_INFINITY;
        for (int index = 0; index < PERMUTATIONS; index++) {
            AxisPermutation candidate = AxisPermutation.ofIndex(index);
            if (candidate.reflects()) {
                continue;
            }
            double angle = of(candidate).conjugate().multiply(normalized).angle();
            if (angle < nearestAngle) {
                nearestAngle = angle;
                nearest = candidate;
            }
        }
        return nearestAngle <= tolerance ? nearest : null;
    }

    public Affine affine() {
        return Affine.rotation(this);
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Double.isFinite(w);
    }

    public boolean isNormalized(double tolerance) {
        return Math.abs(length() - 1.0D) <= tolerance;
    }

    static Quaternion fromRotationMatrix(double m00, double m01, double m02, double m10, double m11, double m12, double m20, double m21, double m22) {
        double trace = m00 + m11 + m22;
        double qx;
        double qy;
        double qz;
        double qw;
        if (trace > 0.0D) {
            double s = 2.0D * Math.sqrt(trace + 1.0D);
            qw = 0.25D * s;
            qx = (m21 - m12) / s;
            qy = (m02 - m20) / s;
            qz = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            double s = 2.0D * Math.sqrt(1.0D + m00 - m11 - m22);
            qw = (m21 - m12) / s;
            qx = 0.25D * s;
            qy = (m01 + m10) / s;
            qz = (m02 + m20) / s;
        } else if (m11 > m22) {
            double s = 2.0D * Math.sqrt(1.0D + m11 - m00 - m22);
            qw = (m02 - m20) / s;
            qx = (m01 + m10) / s;
            qy = 0.25D * s;
            qz = (m12 + m21) / s;
        } else {
            double s = 2.0D * Math.sqrt(1.0D + m22 - m00 - m11);
            qw = (m10 - m01) / s;
            qx = (m02 + m20) / s;
            qy = (m12 + m21) / s;
            qz = 0.25D * s;
        }
        Quaternion rotation = new Quaternion(qx, qy, qz, qw).normalize();
        return rotation.w < 0.0D ? rotation.negate() : rotation;
    }

    void matrixInto(double[] out9) {
        double scale = 2.0D / lengthSquared();
        double xx = x * x * scale;
        double yy = y * y * scale;
        double zz = z * z * scale;
        double xy = x * y * scale;
        double xz = x * z * scale;
        double yz = y * z * scale;
        double wx = w * x * scale;
        double wy = w * y * scale;
        double wz = w * z * scale;
        out9[0] = 1.0D - (yy + zz);
        out9[1] = xy - wz;
        out9[2] = xz + wy;
        out9[3] = xy + wz;
        out9[4] = 1.0D - (xx + zz);
        out9[5] = yz - wx;
        out9[6] = xz - wy;
        out9[7] = yz + wx;
        out9[8] = 1.0D - (xx + yy);
    }

    private static Quaternion elementary(int axis, double degrees) {
        double half = Math.toRadians(degrees) * 0.5D;
        double sine = Math.sin(half);
        double cosine = Math.cos(half);
        return switch (axis) {
            case 0 -> new Quaternion(sine, 0.0D, 0.0D, cosine);
            case 1 -> new Quaternion(0.0D, sine, 0.0D, cosine);
            default -> new Quaternion(0.0D, 0.0D, sine, cosine);
        };
    }

    private static Quaternion halfTurnPerpendicularTo(double fx, double fy, double fz) {
        double ax = 0.0D;
        double ay = fz;
        double az = -fy;
        double length = Math.sqrt(ay * ay + az * az);
        if (length < PERPENDICULAR_FALLBACK) {
            ax = -fz;
            ay = 0.0D;
            az = fx;
            length = Math.sqrt(ax * ax + az * az);
        }
        return new Quaternion(ax / length, ay / length, az / length, 0.0D);
    }

    private static double requireDirection(Vec3d vector, String name) {
        double length = vector.length();
        if (!(length > 0.0D) || !Double.isFinite(length)) {
            throw new IllegalArgumentException("Direction " + name + " must be a finite non-zero vector: " + vector);
        }
        return length;
    }
}
