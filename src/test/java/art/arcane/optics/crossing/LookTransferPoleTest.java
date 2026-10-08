package art.arcane.optics.crossing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.math.Angles;
import art.arcane.optics.math.Angles.Look;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

final class LookTransferPoleTest {
    private static final float[] YAWS = {0.0F, 30.0F, 90.0F, -135.0F, 180.0F, 412.5F};

    @Test
    void straightDownIntoAFloorExitingUpwardLooksStraightUpWithTheYawOfTheScreenUp() {
        AxisPermutation floorToCeiling = AxisPermutation.between(Frame.canonical(Face.U), Frame.canonical(Face.D));
        LookTransfer transfer = LookTransfer.of(new Look(30.0F, 90.0F), floorToCeiling);
        assertEquals(-90.0F, transfer.pitch(), 0.0F);
        assertEquals(-30.0F, transfer.yaw(), 1.0E-4F);
        assertEquals(0.0F, transfer.roll(), 1.0E-4F);
        LookTransfer same = LookTransfer.of(new Look(30.0F, 90.0F), AxisPermutation.IDENTITY);
        assertEquals(90.0F, same.pitch(), 0.0F);
        assertEquals(30.0F, same.yaw(), 1.0E-4F);
    }

    @Test
    void straightDownThroughEveryFloorAndCeilingIntoEveryExitResolvesThePoleFromTheScreenUp() {
        for (Frame entry : verticalFrames()) {
            for (Frame exit : allFrames()) {
                AxisPermutation rotation = AxisPermutation.between(entry, exit);
                for (float yaw : YAWS) {
                    LookTransfer transfer = LookTransfer.of(new Look(yaw, 90.0F), rotation);
                    Vec3d forward = mapped(rotation, Angles.direction(yaw, 90.0F));
                    Vec3d up = mapped(rotation, LookTransfer.cameraUp(yaw, 90.0F));
                    String context = "entry " + entry.getNormal() + entry.getUp() + " exit " + exit.getNormal() + exit.getUp() + " yaw " + yaw;
                    if (Math.abs(forward.y()) < 0.5D) {
                        assertEquals(0.0F, transfer.pitch(), 1.0E-4F, context);
                        assertEquals(0.0F, Angles.unwrap(transfer.yaw(), Angles.yaw(forward.x(), forward.z())) - Angles.yaw(forward.x(), forward.z()), 1.0E-4F, context);
                        continue;
                    }
                    float pole = forward.y() < 0.0D ? 90.0F : -90.0F;
                    assertEquals(pole, transfer.pitch(), 0.0F, context);
                    double sign = forward.y() < 0.0D ? 1.0D : -1.0D;
                    float expectedYaw = Angles.yaw(sign * up.x(), sign * up.z());
                    assertEquals(0.0F, Angles.unwrap(transfer.yaw(), expectedYaw) - expectedYaw, 1.0E-4F, context);
                    assertEquals(0.0F, transfer.roll(), 1.0E-3F, context);
                }
            }
        }
    }

    @Test
    void nearVerticalPerturbationsKeepTheSamePoleYaw() {
        Random random = new Random(0x9013L);
        for (Frame entry : verticalFrames()) {
            for (Frame exit : allFrames()) {
                AxisPermutation rotation = AxisPermutation.between(entry, exit);
                if (Math.abs(mapped(rotation, new Vec3d(0.0D, -1.0D, 0.0D)).y()) < 0.5D) {
                    continue;
                }
                LookTransfer reference = LookTransfer.of(new Look(37.5F, 90.0F), rotation);
                for (int sample = 0; sample < 1_000; sample++) {
                    float pitch = 90.0F - (float) (random.nextDouble() * 5.0E-5D);
                    LookTransfer perturbed = LookTransfer.of(new Look(37.5F, pitch), rotation);
                    assertEquals(reference.pitch(), perturbed.pitch(), 0.0F);
                    assertEquals(0.0F, Angles.unwrap(perturbed.yaw(), reference.yaw()) - reference.yaw(), 1.0E-3F);
                    assertEquals(perturbed, LookTransfer.of(new Look(37.5F, pitch), rotation));
                }
            }
        }
    }

    @Test
    void residualNoiseInAVerticalForwardDoesNotSteerTheYaw() {
        Random random = new Random(0x7E57L);
        Vec3d up = Angles.direction(-60.0F, 0.0F);
        for (int sample = 0; sample < 1_000; sample++) {
            double noiseX = (random.nextDouble() * 2.0D - 1.0D) * 5.0E-7D;
            double noiseZ = (random.nextDouble() * 2.0D - 1.0D) * 5.0E-7D;
            LookTransfer down = LookTransfer.of(new Vec3d(noiseX, -1.0D, noiseZ), up);
            assertEquals(90.0F, down.pitch(), 0.0F);
            assertEquals(-60.0F, down.yaw(), 1.0E-4F);
            LookTransfer upward = LookTransfer.of(new Vec3d(noiseX, 1.0D, noiseZ), up.multiply(-1.0D));
            assertEquals(-90.0F, upward.pitch(), 0.0F);
            assertEquals(-60.0F, upward.yaw(), 1.0E-4F);
        }
    }

    @Test
    void opticTransformLookAndYawUseThePoleRule() {
        OpticTransform floorToWall = OpticTransform.between(Frame.canonical(Face.N), new Vec3d(0.0D, 0.0D, 0.0D),
            Frame.canonical(Face.U), new Vec3d(0.0D, 0.0D, 0.0D));
        for (float yaw : YAWS) {
            Look look = new Look(yaw, 0.0F);
            assertEquals(LookTransfer.of(look, floorToWall.permutation()).look(), floorToWall.look(look));
            assertEquals(LookTransfer.of(look, floorToWall.permutation()).yaw(), floorToWall.yaw(yaw), 0.0F);
        }
        Look level = floorToWall.look(new Look(180.0F, 0.0F));
        assertEquals(90.0F, Math.abs(level.pitch()), 0.0F);
    }

    @Test
    void cameraUpIsTheScreenTopOfTheLook() {
        assertVector(new Vec3d(0.0D, 1.0D, 0.0D), LookTransfer.cameraUp(45.0F, 0.0F));
        assertVector(Angles.direction(45.0F, 0.0F), LookTransfer.cameraUp(45.0F, 90.0F));
        assertVector(Angles.direction(45.0F, 0.0F).multiply(-1.0D), LookTransfer.cameraUp(45.0F, -90.0F));
        assertEquals(0.0D, LookTransfer.cameraUp(12.0F, 33.0F).dot(Angles.direction(12.0F, 33.0F)), 1.0E-12D);
    }

    private static List<Frame> verticalFrames() {
        List<Frame> frames = new ArrayList<Frame>(8);
        for (Frame frame : allFrames()) {
            if (frame.getNormal().isVertical()) {
                frames.add(frame);
            }
        }
        return frames;
    }

    private static List<Frame> allFrames() {
        List<Frame> frames = new ArrayList<Frame>(24);
        for (Face normal : Face.values()) {
            for (Face up : Face.values()) {
                if (normal.getAxis() != up.getAxis()) {
                    frames.add(Frame.fromNormalUp(normal, up));
                }
            }
        }
        return frames;
    }

    private static Vec3d mapped(AxisPermutation rotation, Vec3d vector) {
        double[] out = new double[3];
        rotation.vectorInto(vector.x(), vector.y(), vector.z(), out);
        return new Vec3d(out[0], out[1], out[2]);
    }

    private static void assertVector(Vec3d expected, Vec3d actual) {
        assertEquals(expected.x(), actual.x(), 1.0E-9D);
        assertEquals(expected.y(), actual.y(), 1.0E-9D);
        assertEquals(expected.z(), actual.z(), 1.0E-9D);
    }
}
