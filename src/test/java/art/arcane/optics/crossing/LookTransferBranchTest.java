package art.arcane.optics.crossing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.math.Angles;
import art.arcane.optics.math.Angles.Look;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

final class LookTransferBranchTest {
    private static final AxisPermutation FLOOR_TO_UPWARD = AxisPermutation.between(Frame.canonical(Face.U), Frame.canonical(Face.D));

    @Test
    void aPoleLookTakesTheHalfTurnBranchOfAnOffPoleReferenceWithoutMovingTheCamera() {
        LookTransfer reference = LookTransfer.of(new Look(40.0F, 89.0F), FLOOR_TO_UPWARD);
        LookTransfer pole = LookTransfer.of(new Look(40.0F, 90.0F), FLOOR_TO_UPWARD);
        assertEquals(180.0F, Math.abs(reference.roll()), 1.0E-3F);
        assertEquals(0.0F, pole.roll(), 1.0E-3F);

        LookTransfer aligned = pole.onBranchOf(reference);

        assertEquals(-90.0F, aligned.pitch(), 0.0F);
        assertEquals(reference.yaw(), Angles.unwrap(aligned.yaw(), reference.yaw()), 1.0E-3F);
        assertEquals(0.0F, Angles.unwrap(aligned.roll() - reference.roll(), 0.0F), 1.0E-3F);
        assertCamera(pole, aligned, 1.0E-5D);
    }

    @Test
    void aNearPoleLookBesideAPoleReferenceSettlesOnThePoleOfTheReferenceBranch() {
        LookTransfer reference = LookTransfer.of(new Look(40.0F, 90.0F), FLOOR_TO_UPWARD);
        LookTransfer near = LookTransfer.of(new Look(40.0F, 88.0F), FLOOR_TO_UPWARD);

        LookTransfer aligned = near.onBranchOf(reference);

        assertEquals(-90.0F, aligned.pitch(), 0.0F);
        assertEquals(reference.yaw(), Angles.unwrap(aligned.yaw(), reference.yaw()), 1.0E-3F);
        assertEquals(reference.roll(), aligned.roll(), 1.0E-3F);
        assertTrue(angle(forward(near), forward(aligned)) <= 2.0D + 1.0E-3D);
        assertTrue(angle(up(near), up(aligned)) <= 2.0D + 1.0E-3D);
    }

    @Test
    void anOffPoleLookAcrossTheBranchFromANearPoleReferenceAdoptsTheReference() {
        LookTransfer reference = LookTransfer.of(new Look(40.0F, 90.0F), FLOOR_TO_UPWARD);
        LookTransfer far = LookTransfer.of(new Look(40.0F, 40.0F), FLOOR_TO_UPWARD);

        assertSame(reference, far.onBranchOf(reference));
    }

    @Test
    void looksWithinAQuarterTurnOfRollKeepTheirOwnBranch() {
        LookTransfer reference = new LookTransfer(10.0F, -89.0F, 0.0F);
        LookTransfer value = new LookTransfer(100.0F, -90.0F, 90.0F);
        LookTransfer level = new LookTransfer(30.0F, 10.0F, 180.0F);

        assertSame(value, value.onBranchOf(reference));
        assertSame(level, level.onBranchOf(new LookTransfer(0.0F, 0.0F, 0.0F)));
    }

    private static void assertCamera(LookTransfer expected, LookTransfer actual, double tolerance) {
        assertTrue(angle(forward(expected), forward(actual)) <= tolerance, expected + " vs " + actual);
        assertTrue(angle(up(expected), up(actual)) <= tolerance, expected + " vs " + actual);
    }

    private static Vec3d forward(LookTransfer transfer) {
        return Angles.direction(transfer.yaw(), transfer.pitch());
    }

    private static Vec3d up(LookTransfer transfer) {
        Vec3d axis = forward(transfer);
        Vec3d up = LookTransfer.cameraUp(transfer.yaw(), transfer.pitch());
        double radians = Math.toRadians(transfer.roll());
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        Vec3d cross = new Vec3d(axis.y() * up.z() - axis.z() * up.y(), axis.z() * up.x() - axis.x() * up.z(), axis.x() * up.y() - axis.y() * up.x());
        return up.multiply(cos).add(cross.multiply(sin)).add(axis.multiply(axis.dot(up) * (1.0D - cos)));
    }

    private static double angle(Vec3d first, Vec3d second) {
        double dot = first.normalize().dot(second.normalize());
        return Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, dot))));
    }
}
