package art.arcane.optics.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import art.arcane.optics.crossing.LookTransfer;
import art.arcane.optics.math.Angles;
import art.arcane.optics.math.Angles.Look;

final class OpticTransformLookRegressionTest {
    private static final int SAMPLES = 10_000;

    @Test
    void nonVerticalLooksAndYawsMatchThePreviousFormulaExactly() {
        Random random = new Random(0x4E6AL);
        int looks = 0;
        int yaws = 0;
        for (int sample = 0; sample < SAMPLES; sample++) {
            AxisPermutation permutation = AxisPermutation.ofIndex(random.nextInt(48));
            OpticTransform transform = OpticTransform.of(permutation, random.nextDouble() * 100.0D, 0.0D, -random.nextDouble() * 100.0D);
            Look look = new Look((float) (random.nextDouble() * 720.0D - 360.0D), (float) (random.nextDouble() * 180.0D - 90.0D));
            double[] forward = new double[3];
            Angles.directionInto(look.yaw(), look.pitch(), forward);
            permutation.vectorInto(forward[0], forward[1], forward[2], forward);
            if (Math.sqrt(forward[0] * forward[0] + forward[2] * forward[2]) > LookTransfer.VERTICAL_EPSILON) {
                assertEquals(Angles.look(forward[0], forward[1], forward[2]), transform.look(look), permutation + " " + look);
                looks++;
            }
            double radians = Math.toRadians(look.yaw());
            double[] facing = new double[3];
            permutation.vectorInto(-Math.sin(radians), 0.0D, Math.cos(radians), facing);
            if (Math.sqrt(facing[0] * facing[0] + facing[2] * facing[2]) > LookTransfer.VERTICAL_EPSILON) {
                float previous = Angles.yaw(facing[0], facing[2]);
                assertEquals(previous, Angles.unwrap(transform.yaw(look.yaw()), previous), 0.0F, permutation + " " + look.yaw());
                yaws++;
            }
        }
        assertTrue(looks > SAMPLES * 9 / 10);
        assertTrue(yaws > SAMPLES / 2);
    }

    @Test
    void verticalResultsUseTheLookTransferPoleRule() {
        Random random = new Random(0x90E5L);
        for (int index = 0; index < 48; index++) {
            AxisPermutation permutation = AxisPermutation.ofIndex(index);
            OpticTransform transform = OpticTransform.of(permutation, 0.0D, 0.0D, 0.0D);
            for (int sample = 0; sample < 20; sample++) {
                float yaw = (float) (random.nextDouble() * 720.0D - 360.0D);
                Look down = new Look(yaw, 90.0F);
                assertEquals(LookTransfer.of(down, permutation).look(), transform.look(down));
                assertEquals(LookTransfer.of(new Look(yaw, 0.0F), permutation).yaw(), transform.yaw(yaw), 0.0F);
                float pitch = transform.look(down).pitch();
                assertTrue(Math.abs(pitch) == 90.0F || Math.abs(pitch) < 1.0E-4F, permutation + " " + pitch);
            }
        }
    }
}
