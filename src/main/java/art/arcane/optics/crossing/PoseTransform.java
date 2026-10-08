package art.arcane.optics.crossing;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.frame.Similarity;
import art.arcane.optics.math.Angles;
import art.arcane.optics.math.Angles.Look;
import art.arcane.optics.math.Vec3d;

public final class PoseTransform {
    private static final float HALF_TURN = 180.0F;
    private static final float QUARTER_TURN = 90.0F;

    private PoseTransform() {
    }

    public static Pose apply(Pose pose, OpticTransform transform) {
        return mapped(pose, transform.point(pose.position()), transform.point(pose.previousPosition()), transform.point(pose.oldPosition()),
            transform.vector(pose.velocity()), new Rotation(transform.permutation()));
    }

    public static Pose apply(Pose pose, Similarity transform) {
        return mapped(pose, transform.point(pose.position()), transform.point(pose.previousPosition()), transform.point(pose.oldPosition()),
            transform.vector(pose.velocity()), new Rotation(transform.rigid().permutation()));
    }

    public static Pose arrive(Pose source, PlaneCrossing crossing, Similarity toward, Frame exitFrame, OrientationRule orientation,
                              boolean gravityFlip, MomentumRule momentum, double maxSpeed) {
        return mapped(source, toward.point(source.position()), toward.point(source.previousPosition()), toward.point(source.oldPosition()),
            ArrivalMomentum.apply(toward.vector(source.velocity()), momentum, maxSpeed), new Arrival(crossing, exitFrame, orientation, gravityFlip));
    }

    public static float arrivalRoll(Pose source, PlaneCrossing crossing, Frame exitFrame, OrientationRule orientation, boolean gravityFlip) {
        return new Arrival(crossing, exitFrame, orientation, gravityFlip).transfer(source.yaw(), source.pitch()).roll();
    }

    private static Pose mapped(Pose source, Vec3d position, Vec3d previousPosition, Vec3d oldPosition, Vec3d velocity, LookMap map) {
        LookTransfer current = map.transfer(source.yaw(), source.pitch());
        LookTransfer previous = map.transfer(source.previousYaw(), source.previousPitch()).onBranchOf(current);
        LookTransfer head = map.transfer(source.headYaw(), source.pitch()).onBranchOf(current);
        LookTransfer previousHead = map.transfer(source.previousHeadYaw(), source.previousPitch()).onBranchOf(previous);
        float yaw = Angles.unwrap(current.yaw(), source.yaw());
        float previousYaw = Angles.unwrap(previous.yaw(), yaw);
        float headYaw = Angles.unwrap(head.yaw(), yaw);
        float previousHeadYaw = Angles.unwrap(previousHead.yaw(), headYaw);
        float bodyOffset = besideHead(Angles.unwrap(map.transfer(source.bodyYaw(), source.pitch()).yaw() - headYaw, 0.0F));
        float previousBodyOffset = nearest(map.transfer(source.previousBodyYaw(), source.previousPitch()).yaw() - previousHeadYaw, bodyOffset);
        float bodyYaw = headYaw + bodyOffset;
        float previousBodyYaw = Angles.unwrap(previousHeadYaw + previousBodyOffset, bodyYaw);
        return new Pose(position, previousPosition, oldPosition, velocity, yaw, current.pitch(), previousYaw, previous.pitch(),
            bodyYaw, previousBodyYaw, headYaw, previousHeadYaw);
    }

    private static float besideHead(float offset) {
        return Math.abs(offset) > QUARTER_TURN ? Angles.unwrap(offset + HALF_TURN, 0.0F) : offset;
    }

    private static float nearest(float offset, float reference) {
        float same = Angles.unwrap(offset, reference);
        float turned = Angles.unwrap(offset + HALF_TURN, reference);
        return Math.abs(same - reference) <= Math.abs(turned - reference) ? same : turned;
    }

    private interface LookMap {
        LookTransfer transfer(float yaw, float pitch);
    }

    private record Rotation(AxisPermutation permutation) implements LookMap {
        @Override
        public LookTransfer transfer(float yaw, float pitch) {
            return LookTransfer.of(new Look(yaw, pitch), permutation);
        }
    }

    private record Arrival(PlaneCrossing crossing, Frame exitFrame, OrientationRule rule, boolean gravityFlip) implements LookMap {
        @Override
        public LookTransfer transfer(float yaw, float pitch) {
            PlaneCrossing looking = new PlaneCrossing(crossing.frame(), crossing.origin(), crossing.point(), crossing.velocity(),
                Angles.direction(yaw, pitch), crossing.frontSide());
            return ArrivalOrientation.transfer(looking, LookTransfer.cameraUp(yaw, pitch), exitFrame, rule, gravityFlip);
        }
    }
}
