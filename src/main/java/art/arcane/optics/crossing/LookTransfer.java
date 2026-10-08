package art.arcane.optics.crossing;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.frame.Similarity;
import art.arcane.optics.math.Angles;
import art.arcane.optics.math.Angles.Look;
import art.arcane.optics.math.Vec3d;

public record LookTransfer(float yaw, float pitch, float roll) {
    public static final double VERTICAL_EPSILON = 1.0E-6D;
    private static final float STRAIGHT_DOWN = 90.0F;
    private static final float STRAIGHT_UP = -90.0F;

    public static LookTransfer of(Look look, AxisPermutation rotation) {
        double[] forward = new double[3];
        double[] up = new double[3];
        Angles.directionInto(look.yaw(), look.pitch(), forward);
        Angles.directionInto(look.yaw(), look.pitch() - 90.0F, up);
        rotation.vectorInto(forward[0], forward[1], forward[2], forward);
        rotation.vectorInto(up[0], up[1], up[2], up);
        return resolve(forward, up, look.yaw());
    }

    public static LookTransfer of(Look look, OpticTransform transform) {
        return of(look, transform.permutation());
    }

    public static LookTransfer of(Look look, Similarity transform) {
        return of(look, transform.rigid().permutation());
    }

    public static LookTransfer of(Vec3d forward, Vec3d up) {
        return resolve(new double[] {forward.x(), forward.y(), forward.z()}, new double[] {up.x(), up.y(), up.z()}, 0.0F);
    }

    public static Vec3d cameraUp(float yaw, float pitch) {
        return Angles.direction(yaw, pitch - 90.0F);
    }

    public static float horizontalYaw(Vec3d mapped, float fallbackYaw) {
        return horizontalYaw(mapped.x(), mapped.z(), fallbackYaw);
    }

    public Look look() {
        return new Look(yaw, pitch);
    }

    public boolean hasRoll(double toleranceDegrees) {
        return Math.abs(roll) > toleranceDegrees;
    }

    private static LookTransfer resolve(double[] forward, double[] up, float fallbackYaw) {
        float yaw;
        float pitch;
        if (Math.sqrt(forward[0] * forward[0] + forward[2] * forward[2]) > VERTICAL_EPSILON) {
            yaw = Angles.yaw(forward[0], forward[2]);
            pitch = Angles.pitch(forward[0], forward[1], forward[2]);
        } else {
            boolean down = forward[1] < 0.0D;
            double sign = down ? 1.0D : -1.0D;
            pitch = down ? STRAIGHT_DOWN : STRAIGHT_UP;
            yaw = horizontalYaw(sign * up[0], sign * up[2], fallbackYaw);
        }
        return new LookTransfer(yaw, pitch, roll(yaw, pitch, forward, up));
    }

    private static float horizontalYaw(double x, double z, float fallbackYaw) {
        return Math.sqrt(x * x + z * z) > VERTICAL_EPSILON ? Angles.yaw(x, z) : fallbackYaw;
    }

    private static float roll(float yaw, float pitch, double[] forward, double[] up) {
        double[] free = new double[3];
        Angles.directionInto(yaw, pitch - 90.0F, free);
        double length = Math.sqrt(forward[0] * forward[0] + forward[1] * forward[1] + forward[2] * forward[2]);
        double crossX = free[1] * up[2] - free[2] * up[1];
        double crossY = free[2] * up[0] - free[0] * up[2];
        double crossZ = free[0] * up[1] - free[1] * up[0];
        double sine = length > 0.0D ? (crossX * forward[0] + crossY * forward[1] + crossZ * forward[2]) / length : 0.0D;
        double cosine = free[0] * up[0] + free[1] * up[1] + free[2] * up[2];
        return (float) Math.toDegrees(Math.atan2(sine, cosine));
    }
}
