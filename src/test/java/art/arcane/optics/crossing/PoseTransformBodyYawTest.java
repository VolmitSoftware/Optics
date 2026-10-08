package art.arcane.optics.crossing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.math.Angles;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

final class PoseTransformBodyYawTest {
    private static final Vec3d ZERO = new Vec3d(0.0D, 0.0D, 0.0D);
    private static final OpticTransform SOUTH_BECOMES_UP = OpticTransform.of(AxisPermutation.of(Face.E, Face.N, Face.U), 0.0D, 0.0D, 0.0D);

    @Test
    void aBodyFacingMappedToVerticalAdoptsTheHeadYaw() {
        Pose source = new Pose(ZERO, ZERO, ZERO, ZERO, 810.0F, 10.0F, 806.0F, 12.0F, 720.0F, 720.0F, 810.0F, 806.0F);
        Pose mapped = PoseTransform.apply(source, SOUTH_BECOMES_UP);
        assertEquals(mapped.yaw(), mapped.bodyYaw(), 0.0F);
        assertEquals(mapped.previousYaw(), mapped.previousBodyYaw(), 0.0F);
        assertEquals(mapped.yaw() - mapped.previousYaw(), mapped.bodyYaw() - mapped.previousBodyYaw(), 0.0F);
        assertTrue(Math.abs(mapped.bodyYaw() - mapped.previousBodyYaw()) < 180.0F);
        assertEquals(0.0F, Angles.unwrap(mapped.yaw(), 810.0F) - 810.0F, 30.0F);
    }

    @Test
    void aBodyFacingThatStaysHorizontalKeepsItsOwnYaw() {
        Pose source = new Pose(ZERO, ZERO, ZERO, ZERO, 0.0F, 10.0F, 2.0F, 10.0F, -90.0F, -88.0F, 0.0F, 2.0F);
        Pose mapped = PoseTransform.apply(source, SOUTH_BECOMES_UP);
        assertEquals(-90.0F, Angles.unwrap(mapped.bodyYaw(), -90.0F), 1.0E-4F);
        assertEquals(mapped.bodyYaw(), mapped.previousBodyYaw(), 1.0E-3F);
        assertTrue(Math.abs(mapped.bodyYaw() - mapped.yaw()) <= 180.0F);
    }

    @Test
    void floorToWallArrivalsKeepBodyAndHeadConsistentForEntities() {
        Frame floor = Frame.canonical(Face.U);
        Frame wall = Frame.canonical(Face.N);
        OpticTransform toward = OpticTransform.between(floor, new Vec3d(0.5D, 64.0D, 0.5D), wall, new Vec3d(10.5D, 70.0D, 0.0D));
        Pose standing = new Pose(new Vec3d(0.5D, 64.5D, 0.5D), new Vec3d(0.5D, 64.6D, 0.5D), new Vec3d(0.5D, 64.7D, 0.5D),
            new Vec3d(0.0D, -0.4D, 0.0D), 0.0F, 90.0F, 0.0F, 90.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        Pose mapped = PoseTransform.apply(standing, toward);
        assertEquals(0.0F, mapped.pitch(), 1.0E-4F);
        assertEquals(mapped.yaw(), mapped.bodyYaw(), 0.0F);
        assertEquals(mapped.yaw(), mapped.headYaw(), 1.0E-4F);
        assertEquals(mapped.previousYaw(), mapped.previousBodyYaw(), 0.0F);
        assertEquals(mapped.previousYaw(), mapped.previousHeadYaw(), 1.0E-4F);
    }
}
