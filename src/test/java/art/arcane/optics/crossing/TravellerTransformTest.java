package art.arcane.optics.crossing;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.frame.Similarity;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TravellerTransformTest {
    private static final Pose POSE = new Pose(new Vec3d(3, 5, 7), new Vec3d(2, 4, 6), new Vec3d(1, 3, 5),
        new Vec3d(0.2, -0.1, 0.3), 725, 23, 721, 21, 700, 696, 730, 726);
    private static final ScaleRule RATIO = ScaleRule.ratio(0.0625, 16);

    @Test
    void everyProperRotationScalesAndRestoresCompleteTravellerState() {
        TravellerState source = new TravellerState(POSE, 1, GravityFrame.WORLD);
        for (int index = 0; index < 48; index++) {
            AxisPermutation rotation = AxisPermutation.ofIndex(index);
            if (rotation.reflects()) {
                continue;
            }
            Similarity toward = Similarity.of(OpticTransform.of(rotation, 20, 30, 40), 0.5);
            TravellerState arrived = TravellerTransform.apply(source, toward, RATIO);
            assertEquals(50, arrived.scalePercentage(), 0);
            assertEquals(rotation.face(Face.D), arrived.gravity().down());
            assertEquals(POSE.yaw(), arrived.pose().yaw());
            assertEquals(POSE.previousYaw(), arrived.pose().previousYaw());
            assertEquals(POSE.bodyYaw(), arrived.pose().bodyYaw());
            assertEquals(POSE.previousBodyYaw(), arrived.pose().previousBodyYaw());
            assertEquals(POSE.headYaw(), arrived.pose().headYaw());
            assertEquals(POSE.previousHeadYaw(), arrived.pose().previousHeadYaw());
            assertEquals(toward.point(source.eyePosition(1.62)), arrived.eyePosition(1.62));
            Vec3d local = new Vec3d(0.25, 1.2, 0.4);
            assertEquals(0, toward.point(source.modelTransform().point(local)).distance(arrived.modelTransform().point(local)), 1.0E-12);
            TravellerState restored = TravellerTransform.apply(arrived, toward.inverse(), RATIO);
            assertEquals(source.gravity(), restored.gravity());
            assertEquals(source.scale(), restored.scale());
            assertEquals(0, POSE.position().distance(restored.pose().position()), 1.0E-12);
            assertEquals(0, POSE.previousPosition().distance(restored.pose().previousPosition()), 1.0E-12);
            assertEquals(0, POSE.oldPosition().distance(restored.pose().oldPosition()), 1.0E-12);
            assertEquals(0, POSE.velocity().distance(restored.pose().velocity()), 1.0E-12);
        }
    }

    @Test
    void bodyScaleRulesClampIndependentlyOfPositionAndMomentumMapping() {
        TravellerState source = new TravellerState(POSE, 3, GravityFrame.WORLD);
        Similarity doubled = Similarity.of(OpticTransform.IDENTITY, 2);
        assertEquals(3, TravellerTransform.apply(source, doubled, ScaleRule.motion()).scale());
        assertEquals(3, TravellerTransform.apply(source, doubled, ScaleRule.OFF).scale());
        TravellerState capped = TravellerTransform.apply(source, doubled, ScaleRule.ratio(0.25, 4));
        assertEquals(4, capped.scale());
        assertEquals(POSE.position().multiply(2), capped.pose().position());
        assertEquals(POSE.velocity().multiply(2), capped.pose().velocity());
    }

    @Test
    void percentageStateAndBodyInputsMustBeValid() {
        TravellerState source = new TravellerState(POSE, 1, GravityFrame.WORLD);
        assertEquals(0.5, source.withScalePercentage(50).scale());
        assertEquals(200, source.withScalePercentage(200).scalePercentage());
        assertThrows(IllegalArgumentException.class, () -> source.withScalePercentage(0));
        assertThrows(IllegalArgumentException.class, () -> source.withScalePercentage(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> source.withScalePercentage(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> source.bounds(-0.6, 1.8));
        assertThrows(IllegalArgumentException.class, () -> source.eyePosition(-1));
        Similarity mirror = Similarity.of(OpticTransform.of(AxisPermutation.of(Face.W, Face.U, Face.S), 0, 0, 0), 1);
        assertThrows(IllegalArgumentException.class, () -> TravellerTransform.apply(source, mirror, RATIO));
    }
}
