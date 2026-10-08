package art.arcane.optics.transform;

import java.util.Objects;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.math.Axis;
import art.arcane.optics.math.Box;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

public final class Affine {
    public static final Affine IDENTITY = new Affine(1.0D, 0.0D, 0.0D, 0.0D, 0.0D, 1.0D, 0.0D, 0.0D, 0.0D, 0.0D, 1.0D, 0.0D);
    private static final double SINGULAR_DETERMINANT = 1.0E-12D;
    private static final double DEGENERATE_LENGTH = 1.0E-12D;
    private static final double WELL_CONDITIONED = 0.5D;

    private final double m00;
    private final double m01;
    private final double m02;
    private final double m03;
    private final double m10;
    private final double m11;
    private final double m12;
    private final double m13;
    private final double m20;
    private final double m21;
    private final double m22;
    private final double m23;

    private Affine(double m00, double m01, double m02, double m03, double m10, double m11, double m12, double m13, double m20, double m21,
                   double m22, double m23) {
        this.m00 = m00;
        this.m01 = m01;
        this.m02 = m02;
        this.m03 = m03;
        this.m10 = m10;
        this.m11 = m11;
        this.m12 = m12;
        this.m13 = m13;
        this.m20 = m20;
        this.m21 = m21;
        this.m22 = m22;
        this.m23 = m23;
    }

    public static Affine of(double m00, double m01, double m02, double m03,
                            double m10, double m11, double m12, double m13,
                            double m20, double m21, double m22, double m23) {
        return new Affine(m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23);
    }

    public static Affine translation(double x, double y, double z) {
        return new Affine(1.0D, 0.0D, 0.0D, x, 0.0D, 1.0D, 0.0D, y, 0.0D, 0.0D, 1.0D, z);
    }

    public static Affine translation(Vec3d offset) {
        return translation(offset.x(), offset.y(), offset.z());
    }

    public static Affine rotation(Quaternion rotation) {
        double lengthSquared = rotation.lengthSquared();
        if (!(lengthSquared > 0.0D) || !Double.isFinite(lengthSquared)) {
            throw new IllegalArgumentException("Rotation quaternion must be finite and non-zero: " + rotation);
        }
        double[] matrix = new double[9];
        rotation.matrixInto(matrix);
        return new Affine(matrix[0], matrix[1], matrix[2], 0.0D, matrix[3], matrix[4], matrix[5], 0.0D, matrix[6], matrix[7], matrix[8], 0.0D);
    }

    public static Affine rotation(Vec3d axis, double radians) {
        return rotation(Quaternion.axisAngle(axis, radians));
    }

    public static Affine rotation(EulerAngles angles) {
        return rotation(Quaternion.euler(angles));
    }

    public static Affine scale(double uniform) {
        return scale(uniform, uniform, uniform);
    }

    public static Affine scale(double x, double y, double z) {
        return new Affine(x, 0.0D, 0.0D, 0.0D, 0.0D, y, 0.0D, 0.0D, 0.0D, 0.0D, z, 0.0D);
    }

    public static Affine scale(Vec3d factors) {
        return scale(factors.x(), factors.y(), factors.z());
    }

    public static Affine shear(double xy, double xz, double yx, double yz, double zx, double zy) {
        return new Affine(1.0D, xy, xz, 0.0D, yx, 1.0D, yz, 0.0D, zx, zy, 1.0D, 0.0D);
    }

    public static Affine reflection(Axis axis) {
        return switch (axis) {
            case X -> scale(-1.0D, 1.0D, 1.0D);
            case Y -> scale(1.0D, -1.0D, 1.0D);
            case Z -> scale(1.0D, 1.0D, -1.0D);
        };
    }

    public static Affine reflection(Vec3d planeNormal, Vec3d planePoint) {
        double length = planeNormal.length();
        if (!(length > 0.0D) || !Double.isFinite(length)) {
            throw new IllegalArgumentException("Reflection plane normal must be a finite non-zero vector: " + planeNormal);
        }
        double nx = planeNormal.x() / length;
        double ny = planeNormal.y() / length;
        double nz = planeNormal.z() / length;
        double offset = 2.0D * (nx * planePoint.x() + ny * planePoint.y() + nz * planePoint.z());
        return new Affine(
            1.0D - 2.0D * nx * nx, -2.0D * nx * ny, -2.0D * nx * nz, offset * nx,
            -2.0D * ny * nx, 1.0D - 2.0D * ny * ny, -2.0D * ny * nz, offset * ny,
            -2.0D * nz * nx, -2.0D * nz * ny, 1.0D - 2.0D * nz * nz, offset * nz);
    }

    public static Affine of(OpticTransform rigid) {
        AxisPermutation permutation = rigid.permutation();
        Face imageX = permutation.x();
        Face imageY = permutation.y();
        Face imageZ = permutation.z();
        return new Affine(imageX.x(), imageY.x(), imageZ.x(), rigid.translationX(), imageX.y(), imageY.y(), imageZ.y(), rigid.translationY(),
            imageX.z(), imageY.z(), imageZ.z(), rigid.translationZ());
    }

    public static Affine trs(Vec3d translation, Quaternion rotation, Vec3d scale) {
        Affine turn = rotation(rotation);
        return new Affine(turn.m00 * scale.x(), turn.m01 * scale.y(), turn.m02 * scale.z(), translation.x(),
            turn.m10 * scale.x(), turn.m11 * scale.y(), turn.m12 * scale.z(), translation.y(),
            turn.m20 * scale.x(), turn.m21 * scale.y(), turn.m22 * scale.z(), translation.z());
    }

    public static Affine about(Vec3d pivot, Affine local) {
        return translation(pivot).compose(local).compose(translation(pivot.negate()));
    }

    public static Affine lookAt(Vec3d eye, Vec3d target, Vec3d up) {
        Vec3d forward = target.subtract(eye);
        double length = forward.length();
        if (!(length > 0.0D) || !Double.isFinite(length)) {
            throw new IllegalArgumentException("Look target must differ from the eye: " + eye + " -> " + target);
        }
        return translation(eye).compose(rotation(Quaternion.lookAlong(forward.negate(), up)));
    }

    public double element(int row, int column) {
        if (row < 0 || row > 2 || column < 0 || column > 3) {
            throw new IndexOutOfBoundsException("Affine element out of range: " + row + "," + column);
        }
        return switch (row * 4 + column) {
            case 0 -> m00;
            case 1 -> m01;
            case 2 -> m02;
            case 3 -> m03;
            case 4 -> m10;
            case 5 -> m11;
            case 6 -> m12;
            case 7 -> m13;
            case 8 -> m20;
            case 9 -> m21;
            case 10 -> m22;
            default -> m23;
        };
    }

    public Vec3d translation() {
        return new Vec3d(m03, m13, m23);
    }

    public double determinant() {
        return m00 * (m11 * m22 - m12 * m21) - m01 * (m10 * m22 - m12 * m20) + m02 * (m10 * m21 - m11 * m20);
    }

    public boolean reflects() {
        return determinant() < 0.0D;
    }

    public boolean isIdentity() {
        return m00 == 1.0D && m01 == 0.0D && m02 == 0.0D && m03 == 0.0D
            && m10 == 0.0D && m11 == 1.0D && m12 == 0.0D && m13 == 0.0D
            && m20 == 0.0D && m21 == 0.0D && m22 == 1.0D && m23 == 0.0D;
    }

    public boolean isRigid(double tolerance) {
        return Math.abs(m00 * m00 + m10 * m10 + m20 * m20 - 1.0D) <= tolerance
            && Math.abs(m01 * m01 + m11 * m11 + m21 * m21 - 1.0D) <= tolerance
            && Math.abs(m02 * m02 + m12 * m12 + m22 * m22 - 1.0D) <= tolerance
            && Math.abs(m00 * m01 + m10 * m11 + m20 * m21) <= tolerance
            && Math.abs(m00 * m02 + m10 * m12 + m20 * m22) <= tolerance
            && Math.abs(m01 * m02 + m11 * m12 + m21 * m22) <= tolerance;
    }

    public boolean isFinite() {
        return Double.isFinite(m00) && Double.isFinite(m01) && Double.isFinite(m02) && Double.isFinite(m03)
            && Double.isFinite(m10) && Double.isFinite(m11) && Double.isFinite(m12) && Double.isFinite(m13)
            && Double.isFinite(m20) && Double.isFinite(m21) && Double.isFinite(m22) && Double.isFinite(m23);
    }

    public Affine compose(Affine inner) {
        return new Affine(
            m00 * inner.m00 + m01 * inner.m10 + m02 * inner.m20,
            m00 * inner.m01 + m01 * inner.m11 + m02 * inner.m21,
            m00 * inner.m02 + m01 * inner.m12 + m02 * inner.m22,
            m00 * inner.m03 + m01 * inner.m13 + m02 * inner.m23 + m03,
            m10 * inner.m00 + m11 * inner.m10 + m12 * inner.m20,
            m10 * inner.m01 + m11 * inner.m11 + m12 * inner.m21,
            m10 * inner.m02 + m11 * inner.m12 + m12 * inner.m22,
            m10 * inner.m03 + m11 * inner.m13 + m12 * inner.m23 + m13,
            m20 * inner.m00 + m21 * inner.m10 + m22 * inner.m20,
            m20 * inner.m01 + m21 * inner.m11 + m22 * inner.m21,
            m20 * inner.m02 + m21 * inner.m12 + m22 * inner.m22,
            m20 * inner.m03 + m21 * inner.m13 + m22 * inner.m23 + m23);
    }

    public Affine then(Affine outer) {
        return outer.compose(this);
    }

    public Affine inverse() {
        double determinant = determinant();
        if (!(Math.abs(determinant) >= SINGULAR_DETERMINANT)) {
            throw new IllegalStateException("Affine transform is singular (determinant " + determinant + "): " + this);
        }
        double scale = 1.0D / determinant;
        double i00 = (m11 * m22 - m12 * m21) * scale;
        double i01 = (m02 * m21 - m01 * m22) * scale;
        double i02 = (m01 * m12 - m02 * m11) * scale;
        double i10 = (m12 * m20 - m10 * m22) * scale;
        double i11 = (m00 * m22 - m02 * m20) * scale;
        double i12 = (m02 * m10 - m00 * m12) * scale;
        double i20 = (m10 * m21 - m11 * m20) * scale;
        double i21 = (m01 * m20 - m00 * m21) * scale;
        double i22 = (m00 * m11 - m01 * m10) * scale;
        return new Affine(
            i00, i01, i02, -(i00 * m03 + i01 * m13 + i02 * m23),
            i10, i11, i12, -(i10 * m03 + i11 * m13 + i12 * m23),
            i20, i21, i22, -(i20 * m03 + i21 * m13 + i22 * m23));
    }

    public Vec3d point(Vec3d point) {
        double[] out = new double[3];
        pointInto(point.x(), point.y(), point.z(), out);
        return new Vec3d(out[0], out[1], out[2]);
    }

    public void pointInto(double x, double y, double z, double[] out3) {
        double outX = m00 * x + m01 * y + m02 * z + m03;
        double outY = m10 * x + m11 * y + m12 * z + m13;
        double outZ = m20 * x + m21 * y + m22 * z + m23;
        out3[0] = outX;
        out3[1] = outY;
        out3[2] = outZ;
    }

    public Vec3d vector(Vec3d vector) {
        double[] out = new double[3];
        vectorInto(vector.x(), vector.y(), vector.z(), out);
        return new Vec3d(out[0], out[1], out[2]);
    }

    public void vectorInto(double x, double y, double z, double[] out3) {
        double outX = m00 * x + m01 * y + m02 * z;
        double outY = m10 * x + m11 * y + m12 * z;
        double outZ = m20 * x + m21 * y + m22 * z;
        out3[0] = outX;
        out3[1] = outY;
        out3[2] = outZ;
    }

    public Vec3d normal(Vec3d normal) {
        double sign = determinant() < 0.0D ? -1.0D : 1.0D;
        double x = normal.x();
        double y = normal.y();
        double z = normal.z();
        double outX = sign * ((m11 * m22 - m12 * m21) * x + (m12 * m20 - m10 * m22) * y + (m10 * m21 - m11 * m20) * z);
        double outY = sign * ((m02 * m21 - m01 * m22) * x + (m00 * m22 - m02 * m20) * y + (m01 * m20 - m00 * m21) * z);
        double outZ = sign * ((m01 * m12 - m02 * m11) * x + (m02 * m10 - m00 * m12) * y + (m00 * m11 - m01 * m10) * z);
        double length = Math.sqrt(outX * outX + outY * outY + outZ * outZ);
        if (!(length > 0.0D) || !Double.isFinite(length)) {
            return Vec3d.ZERO;
        }
        return new Vec3d(outX / length, outY / length, outZ / length);
    }

    public Box box(Box box) {
        double[] corner = new double[3];
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < 8; index++) {
            pointInto((index & 1) == 0 ? box.getXa() : box.getXb(), (index & 2) == 0 ? box.getYa() : box.getYb(),
                (index & 4) == 0 ? box.getZa() : box.getZb(), corner);
            minX = Math.min(minX, corner[0]);
            minY = Math.min(minY, corner[1]);
            minZ = Math.min(minZ, corner[2]);
            maxX = Math.max(maxX, corner[0]);
            maxY = Math.max(maxY, corner[1]);
            maxZ = Math.max(maxZ, corner[2]);
        }
        return new Box(minX, maxX, minY, maxY, minZ, maxZ);
    }

    public Frame frame(Frame frame) {
        Vec3d mappedNormal = normal(frame.getNormal().toVector());
        Vec3d mappedUp = vector(frame.getUp().toVector());
        double upLength = mappedUp.length();
        if (mappedNormal.lengthSquared() == 0.0D || !(upLength > DEGENERATE_LENGTH) || !Double.isFinite(upLength)) {
            throw new IllegalArgumentException("Affine transform collapses the frame " + frame.getNormal() + "/" + frame.getUp() + ": " + this);
        }
        Face normalFace = Face.closest(mappedNormal);
        Face upFace = Face.closest(mappedUp);
        if (normalFace.getAxis() == upFace.getAxis()) {
            throw new IllegalArgumentException("Affine transform maps the frame normal and up onto one axis " + normalFace + "/" + upFace + ": " + this);
        }
        return Frame.fromNormalUp(normalFace, upFace);
    }

    public Decomposition decompose() {
        double[] q0 = new double[3];
        double[] q1 = new double[3];
        double[] q2 = new double[3];
        orthonormalBasis(q0, q1, q2);
        double u00 = q0[0] * m00 + q0[1] * m10 + q0[2] * m20;
        double u01 = q0[0] * m01 + q0[1] * m11 + q0[2] * m21;
        double u02 = q0[0] * m02 + q0[1] * m12 + q0[2] * m22;
        double u11 = q1[0] * m01 + q1[1] * m11 + q1[2] * m21;
        double u12 = q1[0] * m02 + q1[1] * m12 + q1[2] * m22;
        double u22 = q2[0] * m02 + q2[1] * m12 + q2[2] * m22;
        if (determinant() < 0.0D) {
            for (int axis = 0; axis < 3; axis++) {
                q0[axis] = -q0[axis];
                q2[axis] = -q2[axis];
            }
            u00 = -u00;
            u01 = -u01;
            u02 = -u02;
            u22 = -u22;
        }
        Quaternion rotation = Quaternion.fromRotationMatrix(q0[0], q1[0], q2[0], q0[1], q1[1], q2[1], q0[2], q1[2], q2[2]);
        return new Decomposition(translation(), rotation, new Vec3d(u00, u11, u22), new Vec3d(ratio(u01, u11), ratio(u02, u22), ratio(u12, u22)));
    }

    public Affine lerp(Affine to, double t) {
        if (t == 0.0D) {
            return this;
        }
        if (t == 1.0D) {
            return to;
        }
        return decompose().lerp(to.decompose(), t).affine();
    }

    public OpticTransform rigid(double tolerance) {
        Face imageX = axisImage(m00, m10, m20, tolerance);
        Face imageY = axisImage(m01, m11, m21, tolerance);
        Face imageZ = axisImage(m02, m12, m22, tolerance);
        if (imageX == null || imageY == null || imageZ == null) {
            return null;
        }
        if (imageX.getAxis() == imageY.getAxis() || imageX.getAxis() == imageZ.getAxis() || imageY.getAxis() == imageZ.getAxis()) {
            return null;
        }
        return OpticTransform.of(AxisPermutation.of(imageX, imageY, imageZ), m03, m13, m23);
    }

    public double[] columnMajor16(double[] out16) {
        out16[0] = m00;
        out16[1] = m10;
        out16[2] = m20;
        out16[3] = 0.0D;
        out16[4] = m01;
        out16[5] = m11;
        out16[6] = m21;
        out16[7] = 0.0D;
        out16[8] = m02;
        out16[9] = m12;
        out16[10] = m22;
        out16[11] = 0.0D;
        out16[12] = m03;
        out16[13] = m13;
        out16[14] = m23;
        out16[15] = 1.0D;
        return out16;
    }

    public double[] rowMajor12(double[] out12) {
        out12[0] = m00;
        out12[1] = m01;
        out12[2] = m02;
        out12[3] = m03;
        out12[4] = m10;
        out12[5] = m11;
        out12[6] = m12;
        out12[7] = m13;
        out12[8] = m20;
        out12[9] = m21;
        out12[10] = m22;
        out12[11] = m23;
        return out12;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Affine affine
            && Double.compare(m00, affine.m00) == 0 && Double.compare(m01, affine.m01) == 0
            && Double.compare(m02, affine.m02) == 0 && Double.compare(m03, affine.m03) == 0
            && Double.compare(m10, affine.m10) == 0 && Double.compare(m11, affine.m11) == 0
            && Double.compare(m12, affine.m12) == 0 && Double.compare(m13, affine.m13) == 0
            && Double.compare(m20, affine.m20) == 0 && Double.compare(m21, affine.m21) == 0
            && Double.compare(m22, affine.m22) == 0 && Double.compare(m23, affine.m23) == 0;
    }

    @Override
    public int hashCode() {
        int hash = Double.hashCode(m00);
        hash = 31 * hash + Double.hashCode(m01);
        hash = 31 * hash + Double.hashCode(m02);
        hash = 31 * hash + Double.hashCode(m03);
        hash = 31 * hash + Double.hashCode(m10);
        hash = 31 * hash + Double.hashCode(m11);
        hash = 31 * hash + Double.hashCode(m12);
        hash = 31 * hash + Double.hashCode(m13);
        hash = 31 * hash + Double.hashCode(m20);
        hash = 31 * hash + Double.hashCode(m21);
        hash = 31 * hash + Double.hashCode(m22);
        return 31 * hash + Double.hashCode(m23);
    }

    @Override
    public String toString() {
        return "Affine[[" + m00 + ", " + m01 + ", " + m02 + ", " + m03 + "], [" + m10 + ", " + m11 + ", " + m12 + ", " + m13 + "], ["
            + m20 + ", " + m21 + ", " + m22 + ", " + m23 + "]]";
    }

    private void orthonormalBasis(double[] q0, double[] q1, double[] q2) {
        double columnLength = Math.sqrt(m00 * m00 + m10 * m10 + m20 * m20);
        if (columnLength > DEGENERATE_LENGTH) {
            set(q0, m00 / columnLength, m10 / columnLength, m20 / columnLength);
        } else {
            double cx = m11 * m22 - m21 * m12;
            double cy = m21 * m02 - m01 * m22;
            double cz = m01 * m12 - m11 * m02;
            double crossLength = Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (crossLength > DEGENERATE_LENGTH) {
                set(q0, cx / crossLength, cy / crossLength, cz / crossLength);
            } else {
                set(q0, 1.0D, 0.0D, 0.0D);
            }
        }
        if (!residual(q0, m01, m11, m21, q1)) {
            double[] fallback = new double[3];
            if (residual(q0, m02, m12, m22, fallback)) {
                set(q1, fallback[1] * q0[2] - fallback[2] * q0[1], fallback[2] * q0[0] - fallback[0] * q0[2], fallback[0] * q0[1] - fallback[1] * q0[0]);
            } else {
                axisPerpendicular(q0, q1);
            }
        }
        set(q2, q0[1] * q1[2] - q0[2] * q1[1], q0[2] * q1[0] - q0[0] * q1[2], q0[0] * q1[1] - q0[1] * q1[0]);
    }

    private static boolean residual(double[] basis, double x, double y, double z, double[] out3) {
        double along = basis[0] * x + basis[1] * y + basis[2] * z;
        double rx = x - basis[0] * along;
        double ry = y - basis[1] * along;
        double rz = z - basis[2] * along;
        double length = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (!(length > DEGENERATE_LENGTH)) {
            return false;
        }
        set(out3, rx / length, ry / length, rz / length);
        return true;
    }

    private static void axisPerpendicular(double[] basis, double[] out3) {
        for (int axis = 1; axis < 4; axis++) {
            int index = axis % 3;
            double along = basis[index];
            double rx = (index == 0 ? 1.0D : 0.0D) - basis[0] * along;
            double ry = (index == 1 ? 1.0D : 0.0D) - basis[1] * along;
            double rz = (index == 2 ? 1.0D : 0.0D) - basis[2] * along;
            double length = Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (length > WELL_CONDITIONED) {
                set(out3, rx / length, ry / length, rz / length);
                return;
            }
        }
        throw new IllegalStateException("No axis is perpendicular to the decomposition basis");
    }

    private static void set(double[] out3, double x, double y, double z) {
        out3[0] = x;
        out3[1] = y;
        out3[2] = z;
    }

    private static double ratio(double numerator, double denominator) {
        return Math.abs(denominator) > DEGENERATE_LENGTH ? numerator / denominator : 0.0D;
    }

    private static Face axisImage(double x, double y, double z, double tolerance) {
        if (Math.abs(Math.abs(x) - 1.0D) <= tolerance && Math.abs(y) <= tolerance && Math.abs(z) <= tolerance) {
            return x > 0.0D ? Face.E : Face.W;
        }
        if (Math.abs(Math.abs(y) - 1.0D) <= tolerance && Math.abs(x) <= tolerance && Math.abs(z) <= tolerance) {
            return y > 0.0D ? Face.U : Face.D;
        }
        if (Math.abs(Math.abs(z) - 1.0D) <= tolerance && Math.abs(x) <= tolerance && Math.abs(y) <= tolerance) {
            return z > 0.0D ? Face.S : Face.N;
        }
        return null;
    }

    public record Decomposition(Vec3d translation, Quaternion rotation, Vec3d scale, Vec3d shear) {
        public Decomposition {
            Objects.requireNonNull(translation, "translation");
            Objects.requireNonNull(rotation, "rotation");
            Objects.requireNonNull(scale, "scale");
            Objects.requireNonNull(shear, "shear");
        }

        public Affine affine() {
            Affine scaledShear = new Affine(scale.x(), shear.x() * scale.y(), shear.y() * scale.z(), 0.0D,
                0.0D, scale.y(), shear.z() * scale.z(), 0.0D,
                0.0D, 0.0D, scale.z(), 0.0D);
            Affine turned = Affine.rotation(rotation).compose(scaledShear);
            return new Affine(turned.m00, turned.m01, turned.m02, translation.x(), turned.m10, turned.m11, turned.m12, translation.y(),
                turned.m20, turned.m21, turned.m22, translation.z());
        }

        public Decomposition lerp(Decomposition to, double t) {
            return new Decomposition(translation.lerp(to.translation, t), rotation.slerp(to.rotation, t), scale.lerp(to.scale, t), shear.lerp(to.shear, t));
        }
    }
}
