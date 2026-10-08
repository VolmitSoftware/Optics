package art.arcane.optics.crossing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.QuarterTurn;
import art.arcane.optics.math.Angles.Look;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

final class LookTransferRollTest {
    private static final Face[] WALLS = {Face.N, Face.S, Face.E, Face.W};
    private static final float ROLL_TOLERANCE = 1.0E-3F;

    @Test
    void floorToWallPairsRollByTheInPlaneTwist() {
        Frame floor = Frame.canonical(Face.U);
        Look straightDown = new Look(0.0F, 90.0F);
        for (Face wall : WALLS) {
            Frame upright = Frame.canonical(wall);
            LookTransfer level = LookTransfer.of(straightDown, AxisPermutation.between(floor, upright));
            assertEquals(0.0F, level.pitch(), 1.0E-4F, wall.name());
            assertEquals(0.0F, level.roll(), ROLL_TOLERANCE, wall.name());
            assertFalse(level.hasRoll(0.5D));
            LookTransfer clockwise = LookTransfer.of(straightDown, AxisPermutation.between(floor, upright.rotateClockwise()));
            assertEquals(90.0F, Math.abs(clockwise.roll()), ROLL_TOLERANCE, wall.name());
            assertTrue(clockwise.hasRoll(0.5D));
            LookTransfer counterClockwise = LookTransfer.of(straightDown, AxisPermutation.between(floor, upright.rotateCounterClockwise()));
            assertEquals(90.0F, Math.abs(counterClockwise.roll()), ROLL_TOLERANCE, wall.name());
            assertEquals(-clockwise.roll(), counterClockwise.roll(), ROLL_TOLERANCE, wall.name());
            LookTransfer upsideDown = LookTransfer.of(straightDown, AxisPermutation.between(floor, upright.rotateClockwise().rotateClockwise()));
            assertEquals(180.0F, Math.abs(upsideDown.roll()), ROLL_TOLERANCE, wall.name());
        }
    }

    @Test
    void wallMirrorsNeverRoll() {
        Random random = new Random(0x3141L);
        for (Face wall : WALLS) {
            AxisPermutation mirror = AxisPermutation.mirror(Frame.canonical(wall), QuarterTurn.DEGREES_0);
            assertEquals(0.0F, LookTransfer.of(new Look(33.0F, 0.0F), mirror).roll(), ROLL_TOLERANCE, wall.name());
            for (int sample = 0; sample < 500; sample++) {
                Look look = new Look((float) (random.nextDouble() * 720.0D - 360.0D), (float) (random.nextDouble() * 170.0D - 85.0D));
                LookTransfer transfer = LookTransfer.of(look, mirror);
                assertEquals(0.0F, transfer.roll(), ROLL_TOLERANCE, wall + " " + look);
                assertFalse(transfer.hasRoll(0.01D));
            }
        }
    }

    @Test
    void uprightRotationsNeverRoll() {
        Random random = new Random(0x2718L);
        for (int index = 0; index < 48; index++) {
            AxisPermutation rotation = AxisPermutation.ofIndex(index);
            if (rotation.y() != Face.U) {
                continue;
            }
            for (int sample = 0; sample < 200; sample++) {
                Look look = new Look((float) (random.nextDouble() * 720.0D - 360.0D), (float) (random.nextDouble() * 180.0D - 90.0D));
                assertEquals(0.0F, LookTransfer.of(look, rotation).roll(), ROLL_TOLERANCE, rotation + " " + look);
            }
        }
    }

    @Test
    void anOrthonormalBasisReportsItsRollAboutTheForward() {
        Vec3d forward = new Vec3d(0.0D, 0.0D, 1.0D);
        double radians = Math.toRadians(30.0D);
        Vec3d tilted = new Vec3d(-Math.sin(radians), Math.cos(radians), 0.0D);
        LookTransfer transfer = LookTransfer.of(forward, tilted);
        assertEquals(0.0F, transfer.yaw(), 1.0E-4F);
        assertEquals(0.0F, transfer.pitch(), 1.0E-4F);
        assertEquals(30.0F, Math.abs(transfer.roll()), ROLL_TOLERANCE);
        LookTransfer mirrored = LookTransfer.of(forward, new Vec3d(Math.sin(radians), Math.cos(radians), 0.0D));
        assertEquals(-transfer.roll(), mirrored.roll(), ROLL_TOLERANCE);
    }
}
