package art.arcane.optics.crossing;

import java.util.Objects;

import art.arcane.optics.aperture.SizeRatio;
import art.arcane.optics.frame.Similarity;

public final class TravellerTransform {
    private TravellerTransform() {
    }

    public static TravellerState apply(TravellerState source, Similarity toward, ScaleRule scale) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(toward, "toward");
        Objects.requireNonNull(scale, "scale");
        GravityFrame gravity = source.gravity().transformed(toward.rigid().permutation());
        Pose pose = source.pose();
        Pose mapped = new Pose(toward.point(pose.position()), toward.point(pose.previousPosition()), toward.point(pose.oldPosition()),
            toward.vector(pose.velocity()), pose.yaw(), pose.pitch(), pose.previousYaw(), pose.previousPitch(),
            pose.bodyYaw(), pose.previousBodyYaw(), pose.headYaw(), pose.previousHeadYaw());
        return new TravellerState(mapped, scale.entityFactor(source.scale(), new SizeRatio(toward.scale(), true)), gravity);
    }
}
