package art.arcane.optics.crossing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.frame.Similarity;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

final class PoseTransformSimilarityTest {
    private static final Vec3d SOURCE_ORIGIN = new Vec3d(11.5D, 65.0D, 20.0D);
    private static final Vec3d TARGET_ORIGIN = new Vec3d(-300.5D, 90.0D, 41.5D);

    @Test
    void positionsScaleAboutTheOriginsVelocityScalesAndLooksDoNot() {
        Frame source = Frame.canonical(Face.N);
        Frame target = Frame.canonical(Face.E);
        Similarity similarity = Similarity.between(source, SOURCE_ORIGIN, target, TARGET_ORIGIN, 3.0D);
        OpticTransform rigid = similarity.rigid();
        Pose pose = new Pose(SOURCE_ORIGIN.add(new Vec3d(0.5D, 0.25D, -0.1D)), SOURCE_ORIGIN.add(new Vec3d(0.5D, 0.25D, 0.3D)),
            SOURCE_ORIGIN.add(new Vec3d(0.5D, 0.25D, 0.7D)), new Vec3d(0.05D, -0.08D, -0.4D),
            170.0F, -20.0F, 166.0F, -18.0F, 160.0F, 158.0F, 172.0F, 168.0F);
        Pose scaled = PoseTransform.apply(pose, similarity);
        Pose rigidPose = PoseTransform.apply(pose, rigid);
        assertVector(TARGET_ORIGIN.add(rigid.vector(pose.position().subtract(SOURCE_ORIGIN)).multiply(3.0D)), scaled.position());
        assertVector(TARGET_ORIGIN.add(rigid.vector(pose.previousPosition().subtract(SOURCE_ORIGIN)).multiply(3.0D)), scaled.previousPosition());
        assertVector(TARGET_ORIGIN.add(rigid.vector(pose.oldPosition().subtract(SOURCE_ORIGIN)).multiply(3.0D)), scaled.oldPosition());
        assertVector(rigid.vector(pose.velocity()).multiply(3.0D), scaled.velocity());
        assertEquals(3.0D * pose.position().distance(pose.previousPosition()), scaled.position().distance(scaled.previousPosition()), 1.0E-9D);
        assertEquals(rigidPose.yaw(), scaled.yaw(), 0.0F);
        assertEquals(rigidPose.pitch(), scaled.pitch(), 0.0F);
        assertEquals(rigidPose.previousYaw(), scaled.previousYaw(), 0.0F);
        assertEquals(rigidPose.previousPitch(), scaled.previousPitch(), 0.0F);
        assertEquals(rigidPose.bodyYaw(), scaled.bodyYaw(), 0.0F);
        assertEquals(rigidPose.previousBodyYaw(), scaled.previousBodyYaw(), 0.0F);
        assertEquals(rigidPose.headYaw(), scaled.headYaw(), 0.0F);
        assertEquals(rigidPose.previousHeadYaw(), scaled.previousHeadYaw(), 0.0F);
    }

    @Test
    void unitScaleReproducesTheRigidPoseExactly() {
        OpticTransform rigid = OpticTransform.between(Frame.canonical(Face.U), SOURCE_ORIGIN, Frame.canonical(Face.D), TARGET_ORIGIN);
        Pose pose = new Pose(new Vec3d(12.25D, 65.1D, 20.75D), new Vec3d(12.25D, 65.5D, 20.75D), new Vec3d(12.25D, 65.9D, 20.75D),
            new Vec3d(0.0D, -0.4D, 0.0D), 30.0F, 90.0F, 28.0F, 89.0F, 25.0F, 24.0F, 30.0F, 28.0F);
        assertEquals(PoseTransform.apply(pose, rigid), PoseTransform.apply(pose, Similarity.of(rigid, 1.0D)));
    }

    private static void assertVector(Vec3d expected, Vec3d actual) {
        assertEquals(expected.x(), actual.x(), 1.0E-9D);
        assertEquals(expected.y(), actual.y(), 1.0E-9D);
        assertEquals(expected.z(), actual.z(), 1.0E-9D);
    }
}
