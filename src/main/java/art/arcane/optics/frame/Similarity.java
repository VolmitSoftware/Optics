package art.arcane.optics.frame;

import java.util.Objects;

import art.arcane.optics.math.Angles.Look;
import art.arcane.optics.math.Box;
import art.arcane.optics.math.Vec3d;

public final class Similarity {
    public static final Similarity IDENTITY = new Similarity(OpticTransform.IDENTITY, 1.0D);

    private final OpticTransform rigid;
    private final double scale;
    private final AxisPermutation permutation;
    private final double fromX;
    private final double fromY;
    private final double fromZ;
    private final double toX;
    private final double toY;
    private final double toZ;

    private Similarity(OpticTransform rigid, double scale) {
        this.rigid = rigid;
        this.scale = scale;
        permutation = rigid.permutation();
        Vec3d from = rigid.sourceAnchor();
        Vec3d to = rigid.targetAnchor();
        fromX = from.x();
        fromY = from.y();
        fromZ = from.z();
        toX = to.x();
        toY = to.y();
        toZ = to.z();
    }

    public static Similarity of(OpticTransform rigid, double scale) {
        Objects.requireNonNull(rigid, "rigid");
        if (!Double.isFinite(scale) || scale <= 0.0D) {
            throw new IllegalArgumentException("Similarity scale must be finite and positive: " + scale);
        }
        return new Similarity(rigid, scale);
    }

    public static Similarity between(Frame from, Vec3d fromOrigin, Frame to, Vec3d toOrigin, double scale) {
        return of(OpticTransform.between(from, fromOrigin, to, toOrigin), scale);
    }

    public OpticTransform rigid() {
        return rigid;
    }

    public double scale() {
        return scale;
    }

    public boolean isRigid() {
        return scale == 1.0D;
    }

    public Similarity inverse() {
        return of(rigid.inverse(), 1.0D / scale);
    }

    public Similarity compose(Similarity inner) {
        double[] target = new double[3];
        pointInto(inner.toX, inner.toY, inner.toZ, target);
        OpticTransform composed = OpticTransform.anchored(permutation.compose(inner.permutation),
            new Vec3d(inner.fromX, inner.fromY, inner.fromZ), new Vec3d(target[0], target[1], target[2]));
        return of(composed, scale * inner.scale);
    }

    public Vec3d point(Vec3d point) {
        double[] out = new double[3];
        pointInto(point.x(), point.y(), point.z(), out);
        return new Vec3d(out[0], out[1], out[2]);
    }

    public void pointInto(double x, double y, double z, double[] out3) {
        permutation.vectorInto(x - fromX, y - fromY, z - fromZ, out3);
        out3[0] = toX + scale * out3[0];
        out3[1] = toY + scale * out3[1];
        out3[2] = toZ + scale * out3[2];
    }

    public Vec3d vector(Vec3d vector) {
        double[] out = new double[3];
        vectorInto(vector.x(), vector.y(), vector.z(), out);
        return new Vec3d(out[0], out[1], out[2]);
    }

    public void vectorInto(double x, double y, double z, double[] out3) {
        permutation.vectorInto(x, y, z, out3);
        out3[0] = scale * out3[0];
        out3[1] = scale * out3[1];
        out3[2] = scale * out3[2];
    }

    public Vec3d direction(Vec3d direction) {
        double[] out = new double[3];
        directionInto(direction.x(), direction.y(), direction.z(), out);
        return new Vec3d(out[0], out[1], out[2]);
    }

    public void directionInto(double x, double y, double z, double[] out3) {
        permutation.vectorInto(x, y, z, out3);
    }

    public Look look(Look look) {
        return rigid.look(look);
    }

    public Box box(Box box) {
        double[] min = new double[3];
        double[] max = new double[3];
        pointInto(box.getXa(), box.getYa(), box.getZa(), min);
        pointInto(box.getXb(), box.getYb(), box.getZb(), max);
        return new Box(min[0], max[0], min[1], max[1], min[2], max[2]);
    }

    public Frame frame(Frame frame) {
        return rigid.frame(frame);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Similarity similarity
            && rigid.equals(similarity.rigid)
            && Double.compare(scale, similarity.scale) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * rigid.hashCode() + Double.hashCode(scale);
    }

    @Override
    public String toString() {
        return "Similarity[" + rigid + " scale " + scale + "]";
    }
}
