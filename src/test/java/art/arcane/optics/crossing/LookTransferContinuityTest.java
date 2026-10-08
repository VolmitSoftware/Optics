package art.arcane.optics.crossing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.math.Angles;
import art.arcane.optics.math.Angles.Look;
import art.arcane.optics.math.Vec3d;

final class LookTransferContinuityTest {
    private static final int LOOKS_PER_PERMUTATION = 2_000;
    private static final double SEPARATION_SLACK_DEGREES = 1.0E-3D;

    @Test
    void nonVerticalResultsMatchTheMappedForwardExactly() {
        Random random = new Random(0x10C4L);
        for (int index = 0; index < 48; index++) {
            AxisPermutation rotation = AxisPermutation.ofIndex(index);
            for (int sample = 0; sample < LOOKS_PER_PERMUTATION; sample++) {
                Look look = new Look((float) range(random, 360.0D), (float) range(random, 90.0D));
                double[] forward = new double[3];
                Angles.directionInto(look.yaw(), look.pitch(), forward);
                rotation.vectorInto(forward[0], forward[1], forward[2], forward);
                LookTransfer transfer = LookTransfer.of(look, rotation);
                assertTrue(transfer.pitch() >= -90.0F && transfer.pitch() <= 90.0F);
                if (Math.sqrt(forward[0] * forward[0] + forward[2] * forward[2]) <= LookTransfer.VERTICAL_EPSILON) {
                    continue;
                }
                assertEquals(Angles.look(forward[0], forward[1], forward[2]), transfer.look(), rotation + " " + look);
            }
        }
    }

    @Test
    void neighbouringLooksStayNeighboursAfterMappingAndUnwrapping() {
        Random random = new Random(0xC0417L);
        for (int index = 0; index < 48; index++) {
            AxisPermutation rotation = AxisPermutation.ofIndex(index);
            for (int sample = 0; sample < LOOKS_PER_PERMUTATION; sample++) {
                float yaw = (float) range(random, 360.0D);
                float pitch = (float) range(random, 89.5D);
                float previousYaw = yaw + (float) range(random, 0.25D);
                float previousPitch = pitch + (float) range(random, 0.25D);
                double separation = degreesBetween(Angles.direction(yaw, pitch), Angles.direction(previousYaw, previousPitch));
                if (separation > 0.5D) {
                    continue;
                }
                LookTransfer current = LookTransfer.of(new Look(yaw, pitch), rotation);
                LookTransfer previous = LookTransfer.of(new Look(previousYaw, previousPitch), rotation);
                float mappedYaw = Angles.unwrap(current.yaw(), yaw);
                float mappedPreviousYaw = Angles.unwrap(previous.yaw(), mappedYaw);
                double mappedSeparation = degreesBetween(Angles.direction(mappedYaw, current.pitch()),
                    Angles.direction(mappedPreviousYaw, previous.pitch()));
                String context = rotation + " " + yaw + "/" + pitch + " <- " + previousYaw + "/" + previousPitch;
                assertEquals(separation, mappedSeparation, SEPARATION_SLACK_DEGREES, context);
                assertTrue(mappedSeparation <= 0.5D + SEPARATION_SLACK_DEGREES, context);
                if (Math.abs(current.pitch()) <= 60.0F && Math.abs(previous.pitch()) <= 60.0F) {
                    assertTrue(Math.abs(mappedYaw - mappedPreviousYaw) <= 2.0D * separation + SEPARATION_SLACK_DEGREES, context);
                    assertTrue(Math.abs(current.pitch() - previous.pitch()) <= separation + SEPARATION_SLACK_DEGREES, context);
                }
            }
        }
    }

    private static double degreesBetween(Vec3d first, Vec3d second) {
        double cross = Math.sqrt(square(first.y() * second.z() - first.z() * second.y())
            + square(first.z() * second.x() - first.x() * second.z())
            + square(first.x() * second.y() - first.y() * second.x()));
        return Math.toDegrees(Math.atan2(cross, first.dot(second)));
    }

    private static double square(double value) {
        return value * value;
    }

    private static double range(Random random, double extent) {
        return (random.nextDouble() * 2.0D - 1.0D) * extent;
    }
}
