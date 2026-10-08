package art.arcane.optics.crossing;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.math.Angles;
import art.arcane.optics.math.Angles.Look;
import art.arcane.optics.math.Vec3d;

public final class ArrivalOrientation {
    private static final Vec3d WORLD_UP = new Vec3d(0.0D, 1.0D, 0.0D);

    private ArrivalOrientation() {
    }

    public static Look apply(PlaneCrossing crossing, Frame exitFrame, OrientationRule rule, boolean gravityFlip) {
        Vec3d look = crossing.look();
        Look entry = Angles.look(look.x(), look.y(), look.z());
        return transfer(crossing, LookTransfer.cameraUp(entry.yaw(), entry.pitch()), exitFrame, rule, gravityFlip).look();
    }

    public static LookTransfer transfer(PlaneCrossing crossing, Vec3d cameraUp, Frame exitFrame, OrientationRule rule, boolean gravityFlip) {
        OrientationRule active = rule == null ? OrientationRule.FRAME : rule;
        Frame exitView = exitFrame.view(crossing.frontSide());
        Vec3d forward = forward(crossing, exitView, active);
        Vec3d up = up(crossing, cameraUp, exitView, active);
        if (flips(active, gravityFlip, exitView)) {
            return LookTransfer.of(flipUpright(forward, exitView), flipUpright(up, exitView));
        }
        return LookTransfer.of(forward, up);
    }

    public static Vec3d direction(PlaneCrossing crossing, Frame exitFrame, OrientationRule rule, boolean gravityFlip) {
        OrientationRule active = rule == null ? OrientationRule.FRAME : rule;
        Frame exitView = exitFrame.view(crossing.frontSide());
        Vec3d forward = forward(crossing, exitView, active);
        return flips(active, gravityFlip, exitView) ? flipUpright(forward, exitView) : forward;
    }

    private static Vec3d forward(PlaneCrossing crossing, Frame exitView, OrientationRule rule) {
        return switch (rule) {
            case FRAME -> rotated(crossing, exitView, crossing.look());
            case LOOK -> crossing.look();
            case SNAP -> exitView.getNormal().toVector().multiply(-1.0D);
            case MIRROR -> Angles.reflect(rotated(crossing, exitView, crossing.look()), exitView.getNormal().toVector());
        };
    }

    private static Vec3d up(PlaneCrossing crossing, Vec3d cameraUp, Frame exitView, OrientationRule rule) {
        return switch (rule) {
            case FRAME -> rotated(crossing, exitView, cameraUp);
            case LOOK -> cameraUp;
            case SNAP -> exitView.getNormal().isVertical() ? exitView.getUp().toVector() : WORLD_UP;
            case MIRROR -> Angles.reflect(rotated(crossing, exitView, cameraUp), exitView.getNormal().toVector());
        };
    }

    private static Vec3d rotated(PlaneCrossing crossing, Frame exitView, Vec3d vector) {
        double[] out = new double[3];
        AxisPermutation.between(crossing.frame(), exitView).vectorInto(vector.x(), vector.y(), vector.z(), out);
        return new Vec3d(out[0], out[1], out[2]);
    }

    private static boolean flips(OrientationRule rule, boolean gravityFlip, Frame exitView) {
        return rule != OrientationRule.LOOK && gravityFlip && exitView.getNormal().isVertical();
    }

    private static Vec3d flipUpright(Vec3d vector, Frame exitView) {
        Vec3d right = exitView.getRight().toVector();
        Vec3d up = exitView.getUp().toVector();
        Vec3d exitDirection = exitView.getNormal().toVector().multiply(-1.0D);
        double sign = exitDirection.dot(WORLD_UP);
        double alongRight = vector.dot(right);
        double alongUp = vector.dot(up);
        double alongExit = vector.dot(exitDirection);
        return right.multiply(alongRight)
            .add(WORLD_UP.multiply(alongUp))
            .add(up.multiply(-sign * alongExit));
    }
}
