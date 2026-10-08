package art.arcane.optics.crossing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.math.Angles;
import art.arcane.optics.math.Angles.Look;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

final class ArrivalOrientationTransferTest {
    private static final Vec3d ENTRY_ORIGIN = new Vec3d(10.0D, 64.0D, 20.0D);
    private static final Vec3d EXIT_ORIGIN = new Vec3d(-40.5D, 90.0D, 300.5D);
    private static final Look[] LOOKS = {new Look(30.0F, 90.0F), new Look(-120.0F, -90.0F), new Look(0.0F, 0.0F),
        new Look(75.0F, 40.0F), new Look(200.0F, -65.0F), new Look(-45.0F, 89.0F)};

    @Test
    void lookingStraightDownIntoAFloorThatExitsUpwardArrivesLookingStraightUp() {
        Frame floor = Frame.canonical(Face.U);
        Frame upwardExit = Frame.canonical(Face.D);
        PlaneCrossing crossing = entering(floor, Angles.direction(30.0F, 90.0F), true);
        LookTransfer carried = ArrivalOrientation.transfer(crossing, LookTransfer.cameraUp(30.0F, 90.0F), upwardExit, OrientationRule.FRAME, false);
        assertEquals(-90.0F, carried.pitch(), 0.0F);
        assertEquals(-30.0F, carried.yaw(), 1.0E-4F);
        assertEquals(0.0F, carried.roll(), 1.0E-3F);
        LookTransfer upright = ArrivalOrientation.transfer(crossing, LookTransfer.cameraUp(30.0F, 90.0F), upwardExit, OrientationRule.FRAME, true);
        assertEquals(0.0F, upright.pitch(), 1.0E-4F);

        Pose source = new Pose(new Vec3d(10.5D, 64.1D, 20.5D), new Vec3d(10.5D, 64.5D, 20.5D), new Vec3d(10.5D, 64.9D, 20.5D),
            new Vec3d(0.0D, -0.4D, 0.0D), 30.0F, 90.0F, 30.0F, 90.0F, 30.0F, 30.0F, 30.0F, 30.0F);
        Pose crossed = PoseTransform.apply(source, crossing.toward(upwardExit, EXIT_ORIGIN));
        Pose arrived = PoseTransform.arrive(crossed, crossing, upwardExit, OrientationRule.FRAME, false, null, 0.0D);
        assertEquals(-90.0F, crossed.pitch(), 0.0F);
        assertEquals(-90.0F, arrived.pitch(), 0.0F);
        assertEquals(-90.0F, arrived.previousPitch(), 0.0F);
        assertEquals(-30.0F, Angles.unwrap(arrived.yaw(), -30.0F), 1.0E-3F);
        assertEquals(arrived.yaw(), arrived.previousYaw(), 1.0E-3F);
        assertEquals(arrived.yaw(), arrived.headYaw(), 1.0E-3F);
        assertEquals(arrived.headYaw(), arrived.previousHeadYaw(), 1.0E-3F);
        assertEquals(150.0F, Angles.unwrap(arrived.bodyYaw(), 150.0F), 1.0E-3F);
        assertEquals(arrived.bodyYaw(), arrived.previousBodyYaw(), 1.0E-3F);
        assertEquals(0.0F, PoseTransform.arrivalRoll(source, crossing, upwardExit, OrientationRule.FRAME, false), 1.0E-3F);
    }

    @Test
    void lookingStraightDownIntoAFloorThatExitsAWallArrivesLevelAlongTheExit() {
        PlaneCrossing crossing = entering(Frame.canonical(Face.U), Angles.direction(0.0F, 90.0F), true);
        for (Face wall : new Face[] {Face.N, Face.S, Face.E, Face.W}) {
            Frame exit = Frame.canonical(wall);
            LookTransfer transfer = ArrivalOrientation.transfer(crossing, LookTransfer.cameraUp(0.0F, 90.0F), exit, OrientationRule.FRAME, false);
            Vec3d exitDirection = exit.getNormal().toVector().multiply(-1.0D);
            assertEquals(0.0F, transfer.pitch(), 1.0E-4F, wall.name());
            assertVector(exitDirection, Angles.direction(transfer.yaw(), transfer.pitch()), 1.0E-6D, wall.name());
            assertEquals(0.0F, transfer.roll(), 1.0E-3F, wall.name());
        }
    }

    @Test
    void snapOnVerticalExitsUsesTheExitViewUp() {
        PlaneCrossing crossing = entering(Frame.canonical(Face.N), new Vec3d(0.0D, 0.6D, -0.8D), true);
        Vec3d up = LookTransfer.cameraUp(180.0F, -36.869896F);
        Frame floorExit = Frame.canonical(Face.U);
        Frame ceilingExit = Frame.canonical(Face.D);
        LookTransfer floorFlip = ArrivalOrientation.transfer(crossing, up, floorExit, OrientationRule.SNAP, true);
        assertVector(floorExit.getUp().toVector(), Angles.direction(floorFlip.yaw(), floorFlip.pitch()), 1.0E-6D, "floor flip");
        assertEquals(0.0F, floorFlip.roll(), 1.0E-3F);
        LookTransfer ceilingFlip = ArrivalOrientation.transfer(crossing, up, ceilingExit, OrientationRule.SNAP, true);
        assertVector(ceilingExit.getUp().toVector().multiply(-1.0D), Angles.direction(ceilingFlip.yaw(), ceilingFlip.pitch()), 1.0E-6D, "ceiling flip");
        LookTransfer floorSnap = ArrivalOrientation.transfer(crossing, up, floorExit, OrientationRule.SNAP, false);
        assertEquals(90.0F, floorSnap.pitch(), 0.0F);
        assertEquals(Angles.yaw(floorExit.getUp().x(), floorExit.getUp().z()), floorSnap.yaw(), 1.0E-4F);
        LookTransfer ceilingSnap = ArrivalOrientation.transfer(crossing, up, ceilingExit, OrientationRule.SNAP, false);
        assertEquals(-90.0F, ceilingSnap.pitch(), 0.0F);
        assertEquals(Angles.yaw(-ceilingExit.getUp().x(), -ceilingExit.getUp().z()), ceilingSnap.yaw(), 1.0E-4F);
    }

    @Test
    void everyRuleOverEveryVerticalHorizontalPairAgreesWithTheForwardDirection() {
        for (Frame entry : frames()) {
            for (Frame exit : frames()) {
                if (entry.getNormal().isVertical() == exit.getNormal().isVertical()) {
                    continue;
                }
                for (boolean front : new boolean[] {true, false}) {
                    for (OrientationRule rule : OrientationRule.values()) {
                        for (boolean flip : new boolean[] {false, true}) {
                            for (Look look : LOOKS) {
                                checkPair(entry, exit, front, rule, flip, look);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void frameCarriesTheScreenUpThroughEveryPair() {
        for (Frame entry : frames()) {
            for (Frame exit : frames()) {
                for (boolean front : new boolean[] {true, false}) {
                    for (Look look : LOOKS) {
                        PlaneCrossing crossing = entering(entry, Angles.direction(look.yaw(), look.pitch()), front);
                        Vec3d up = LookTransfer.cameraUp(look.yaw(), look.pitch());
                        LookTransfer transfer = ArrivalOrientation.transfer(crossing, up, exit, OrientationRule.FRAME, false);
                        AxisPermutation rotation = AxisPermutation.between(crossing.frame(), exit.view(front));
                        Vec3d forward = mapped(rotation, Angles.direction(look.yaw(), look.pitch()));
                        Vec3d expectedUp = mapped(rotation, up);
                        Vec3d rolled = rotate(LookTransfer.cameraUp(transfer.yaw(), transfer.pitch()), forward, Math.toRadians(transfer.roll()));
                        assertVector(expectedUp, rolled, 1.0E-5D, entry.getNormal() + "" + entry.getUp() + "->" + exit.getNormal() + exit.getUp() + " " + look);
                    }
                }
            }
        }
    }

    private static void checkPair(Frame entry, Frame exit, boolean front, OrientationRule rule, boolean flip, Look look) {
        Vec3d forward = Angles.direction(look.yaw(), look.pitch());
        PlaneCrossing crossing = entering(entry, forward, front);
        String context = entry.getNormal() + "" + entry.getUp() + "->" + exit.getNormal() + exit.getUp() + " " + front + " " + rule + " " + flip + " " + look;
        Look entryLook = Angles.look(forward.x(), forward.y(), forward.z());
        LookTransfer transfer = ArrivalOrientation.transfer(crossing, LookTransfer.cameraUp(entryLook.yaw(), entryLook.pitch()), exit, rule, flip);
        Vec3d direction = ArrivalOrientation.direction(crossing, exit, rule, flip).normalize();
        assertTrue(transfer.pitch() >= -90.0F && transfer.pitch() <= 90.0F, context);
        assertVector(direction, Angles.direction(transfer.yaw(), transfer.pitch()), 1.0E-5D, context);
        if (Math.abs(direction.y()) > 1.0D - 1.0E-12D) {
            assertEquals(direction.y() < 0.0D ? 90.0F : -90.0F, transfer.pitch(), 0.0F, context);
        }
        assertEquals(transfer.look(), ArrivalOrientation.apply(crossing, exit, rule, flip), context);
    }

    private static PlaneCrossing entering(Frame frame, Vec3d look, boolean frontSide) {
        return new PlaneCrossing(frame.view(frontSide), ENTRY_ORIGIN, new Vec3d(10.5D, 64.5D, 20.0D), new Vec3d(0.0D, 0.0D, -0.4D), look, frontSide);
    }

    private static List<Frame> frames() {
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

    private static Vec3d rotate(Vec3d vector, Vec3d axis, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        Vec3d cross = new Vec3d(axis.y() * vector.z() - axis.z() * vector.y(), axis.z() * vector.x() - axis.x() * vector.z(),
            axis.x() * vector.y() - axis.y() * vector.x());
        return vector.multiply(cos).add(cross.multiply(sin)).add(axis.multiply(axis.dot(vector) * (1.0D - cos)));
    }

    private static void assertVector(Vec3d expected, Vec3d actual, double tolerance, String context) {
        assertEquals(expected.x(), actual.x(), tolerance, context + " x");
        assertEquals(expected.y(), actual.y(), tolerance, context + " y");
        assertEquals(expected.z(), actual.z(), tolerance, context + " z");
    }
}
