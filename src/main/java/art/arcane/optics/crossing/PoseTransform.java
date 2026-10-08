package art.arcane.optics.crossing;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.frame.Similarity;
import art.arcane.optics.math.Angles;
import art.arcane.optics.math.Angles.Look;
import art.arcane.optics.math.Vec3d;

public final class PoseTransform {
    private PoseTransform() {
    }

    public static Pose apply(Pose pose, OpticTransform transform) {
        return mapped(pose, transform.point(pose.position()), transform.point(pose.previousPosition()), transform.point(pose.oldPosition()),
            transform.vector(pose.velocity()), transform.permutation());
    }

    public static Pose apply(Pose pose, Similarity transform) {
        return mapped(pose, transform.point(pose.position()), transform.point(pose.previousPosition()), transform.point(pose.oldPosition()),
            transform.vector(pose.velocity()), transform.rigid().permutation());
    }

    public static Pose arrive(Pose crossed, PlaneCrossing crossing, Frame exitFrame, OrientationRule orientation,
                              boolean gravityFlip, MomentumRule momentum, double maxSpeed) {
        Arrival arrival = new Arrival(AxisPermutation.between(exitFrame.view(crossing.frontSide()), crossing.frame()),
            crossing, exitFrame, orientation, gravityFlip);
        LookTransfer current = arrival.transfer(crossed.yaw(), crossed.pitch());
        float yaw = Angles.unwrap(current.yaw(), crossed.yaw());
        LookTransfer previous = arrival.transfer(crossed.previousYaw(), crossed.previousPitch());
        float previousYaw = Angles.unwrap(previous.yaw(), yaw);
        float bodyYaw = Angles.unwrap(LookTransfer.horizontalYaw(arrival.facing(crossed.bodyYaw()), yaw), yaw);
        float previousBodyYaw = Angles.unwrap(LookTransfer.horizontalYaw(arrival.facing(crossed.previousBodyYaw()), previousYaw), bodyYaw);
        float headYaw = Angles.unwrap(arrival.transfer(crossed.headYaw(), crossed.pitch()).yaw(), yaw);
        float previousHeadYaw = Angles.unwrap(arrival.transfer(crossed.previousHeadYaw(), crossed.previousPitch()).yaw(), headYaw);
        return new Pose(crossed.position(), crossed.previousPosition(), crossed.oldPosition(),
            ArrivalMomentum.apply(crossed.velocity(), momentum, maxSpeed), yaw, current.pitch(), previousYaw, previous.pitch(),
            bodyYaw, previousBodyYaw, headYaw, previousHeadYaw);
    }

    public static float arrivalRoll(Pose source, PlaneCrossing crossing, Frame exitFrame, OrientationRule orientation, boolean gravityFlip) {
        PlaneCrossing looking = new PlaneCrossing(crossing.frame(), crossing.origin(), crossing.point(), crossing.velocity(),
            Angles.direction(source.yaw(), source.pitch()), crossing.frontSide());
        return ArrivalOrientation.transfer(looking, LookTransfer.cameraUp(source.yaw(), source.pitch()), exitFrame, orientation, gravityFlip).roll();
    }

    private static Pose mapped(Pose pose, Vec3d position, Vec3d previousPosition, Vec3d oldPosition, Vec3d velocity,
                               AxisPermutation rotation) {
        LookTransfer current = LookTransfer.of(new Look(pose.yaw(), pose.pitch()), rotation);
        float yaw = Angles.unwrap(current.yaw(), pose.yaw());
        LookTransfer previous = LookTransfer.of(new Look(pose.previousYaw(), pose.previousPitch()), rotation);
        float previousYaw = Angles.unwrap(previous.yaw(), yaw);
        float bodyYaw = Angles.unwrap(bodyYaw(pose.bodyYaw(), rotation, yaw), yaw);
        float previousBodyYaw = Angles.unwrap(bodyYaw(pose.previousBodyYaw(), rotation, previousYaw), bodyYaw);
        float headYaw = Angles.unwrap(LookTransfer.of(new Look(pose.headYaw(), pose.pitch()), rotation).yaw(), yaw);
        float previousHeadYaw = Angles.unwrap(LookTransfer.of(new Look(pose.previousHeadYaw(), pose.previousPitch()), rotation).yaw(), headYaw);
        return new Pose(position, previousPosition, oldPosition, velocity,
            yaw, current.pitch(), previousYaw, previous.pitch(), bodyYaw, previousBodyYaw, headYaw, previousHeadYaw);
    }

    private static float bodyYaw(float bodyYaw, AxisPermutation rotation, float fallbackYaw) {
        double[] facing = new double[3];
        Angles.directionInto(bodyYaw, 0.0F, facing);
        rotation.vectorInto(facing[0], facing[1], facing[2], facing);
        return LookTransfer.horizontalYaw(new Vec3d(facing[0], facing[1], facing[2]), fallbackYaw);
    }

    private record Arrival(AxisPermutation back, PlaneCrossing crossing, Frame exitFrame, OrientationRule rule, boolean gravityFlip) {
        LookTransfer transfer(float yaw, float pitch) {
            return ArrivalOrientation.transfer(looking(Angles.direction(yaw, pitch)), back(LookTransfer.cameraUp(yaw, pitch)),
                exitFrame, rule, gravityFlip);
        }

        Vec3d facing(float bodyYaw) {
            return ArrivalOrientation.direction(looking(Angles.direction(bodyYaw, 0.0F)), exitFrame, rule, gravityFlip);
        }

        private PlaneCrossing looking(Vec3d destinationLook) {
            return new PlaneCrossing(crossing.frame(), crossing.origin(), crossing.point(), crossing.velocity(), back(destinationLook),
                crossing.frontSide());
        }

        private Vec3d back(Vec3d vector) {
            double[] out = new double[3];
            back.vectorInto(vector.x(), vector.y(), vector.z(), out);
            return new Vec3d(out[0], out[1], out[2]);
        }
    }
}
