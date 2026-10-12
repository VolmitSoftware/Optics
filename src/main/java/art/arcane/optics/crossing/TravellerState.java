package art.arcane.optics.crossing;

import java.util.Objects;

import art.arcane.optics.math.Box;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.transform.Affine;

public record TravellerState(Pose pose, double scale, GravityFrame gravity) {
    public TravellerState {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(gravity, "gravity");
        if (!Double.isFinite(scale) || scale <= 0.0D) {
            throw new IllegalArgumentException("Traveller scale must be finite and positive: " + scale);
        }
    }

    public double scalePercentage() {
        return scale * 100.0D;
    }

    public TravellerState withScalePercentage(double percentage) {
        return new TravellerState(pose, percentage / 100.0D, gravity);
    }

    public LookTransfer camera() {
        return gravity.look(pose.yaw(), pose.pitch());
    }

    public Vec3d eyePosition(double unscaledEyeHeight) {
        return gravity.eyePosition(pose.position(), unscaledEyeHeight * scale);
    }

    public Box bounds(double unscaledWidth, double unscaledHeight) {
        return gravity.bounds(pose.position(), unscaledWidth * scale, unscaledHeight * scale);
    }

    public Affine modelTransform() {
        return Affine.trs(pose.position(), gravity.modelRotation(pose.bodyYaw()), new Vec3d(scale, scale, scale));
    }
}
