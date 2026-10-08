package art.arcane.optics.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import art.arcane.optics.math.Angles.Look;
import art.arcane.optics.math.Box;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

final class SimilarityTest {
    private static final double TOLERANCE = 1.0E-9D;
    private static final Vec3d SOURCE_ORIGIN = new Vec3d(10.5D, 64.0D, 20.5D);
    private static final Vec3d TARGET_ORIGIN = new Vec3d(-40.0D, 90.0D, 300.0D);

    @Test
    void pointsScaleAboutTheOriginPairWhileDirectionsOnlyRotate() {
        Frame north = Frame.canonical(Face.N);
        Frame east = Frame.canonical(Face.E);
        OpticTransform rigid = OpticTransform.between(north, SOURCE_ORIGIN, east, TARGET_ORIGIN);
        Similarity similarity = Similarity.between(north, SOURCE_ORIGIN, east, TARGET_ORIGIN, 3.0D);
        assertEquals(rigid, similarity.rigid());
        assertEquals(3.0D, similarity.scale(), 0.0D);
        assertFalse(similarity.isRigid());
        assertEquals(TARGET_ORIGIN, similarity.point(SOURCE_ORIGIN));
        Vec3d offset = new Vec3d(1.0D, 2.0D, -0.5D);
        Vec3d rotated = rigid.vector(offset);
        assertVector(TARGET_ORIGIN.add(rotated.multiply(3.0D)), similarity.point(SOURCE_ORIGIN.add(offset)));
        assertEquals(rotated.multiply(3.0D), similarity.vector(offset));
        assertEquals(rotated, similarity.direction(offset));
        double[] out = new double[3];
        similarity.vectorInto(offset.x(), offset.y(), offset.z(), out);
        assertEquals(rotated.multiply(3.0D), new Vec3d(out[0], out[1], out[2]));
        similarity.directionInto(offset.x(), offset.y(), offset.z(), out);
        assertEquals(rotated, new Vec3d(out[0], out[1], out[2]));
        Vec3d moved = SOURCE_ORIGIN.add(offset);
        similarity.pointInto(moved.x(), moved.y(), moved.z(), out);
        assertEquals(similarity.point(moved), new Vec3d(out[0], out[1], out[2]));
        Look look = new Look(30.0F, 20.0F);
        assertEquals(rigid.look(look), similarity.look(look));
        assertEquals(rigid.frame(north), similarity.frame(north));
    }

    @Test
    void boxesMapTheirCornersAndScaleTheirVolume() {
        Similarity similarity = Similarity.between(Frame.canonical(Face.N), SOURCE_ORIGIN, Frame.canonical(Face.U), TARGET_ORIGIN, 2.5D);
        Box box = new Box(10.0D, 11.0D, 64.0D, 65.8D, 20.0D, 20.6D);
        Box mapped = similarity.box(box);
        assertEquals(box.volume() * 2.5D * 2.5D * 2.5D, mapped.volume(), 1.0E-9D);
        Vec3d first = similarity.point(box.min());
        Vec3d second = similarity.point(box.max());
        assertVector(new Vec3d(Math.min(first.x(), second.x()), Math.min(first.y(), second.y()), Math.min(first.z(), second.z())), mapped.min());
        assertVector(new Vec3d(Math.max(first.x(), second.x()), Math.max(first.y(), second.y()), Math.max(first.z(), second.z())), mapped.max());
        assertVector(box.min(), similarity.inverse().box(mapped).min());
        assertVector(box.max(), similarity.inverse().box(mapped).max());
    }

    @Test
    void scaleMustBeFiniteAndPositive() {
        OpticTransform rigid = OpticTransform.translation(1.0D, 2.0D, 3.0D);
        assertThrows(IllegalArgumentException.class, () -> Similarity.of(rigid, 0.0D));
        assertThrows(IllegalArgumentException.class, () -> Similarity.of(rigid, -2.0D));
        assertThrows(IllegalArgumentException.class, () -> Similarity.of(rigid, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Similarity.of(rigid, Double.POSITIVE_INFINITY));
        assertThrows(NullPointerException.class, () -> Similarity.of(null, 1.0D));
    }

    @Test
    void identityLeavesEverythingInPlace() {
        Vec3d point = new Vec3d(12.25D, -3.5D, 9.0D);
        assertEquals(point, Similarity.IDENTITY.point(point));
        assertEquals(point, Similarity.IDENTITY.vector(point));
        assertTrue(Similarity.IDENTITY.isRigid());
        assertEquals(OpticTransform.IDENTITY, Similarity.IDENTITY.rigid());
    }

    @Test
    void inverseAndComposeIdentitiesHoldOverRandomSimilarities() {
        Random random = new Random(0x51A1L);
        for (int sample = 0; sample < 1_000; sample++) {
            Similarity first = randomSimilarity(random);
            Similarity second = randomSimilarity(random);
            Vec3d point = randomPoint(random);
            Vec3d vector = randomPoint(random).multiply(0.001D);
            Similarity inverse = first.inverse();
            assertEquals(1.0D / first.scale(), inverse.scale(), 0.0D);
            assertVector(point, inverse.point(first.point(point)));
            assertVector(point, first.point(inverse.point(point)));
            assertVector(vector, inverse.vector(first.vector(vector)));
            assertVector(vector, inverse.direction(first.direction(vector)));
            Similarity composed = first.compose(second);
            assertEquals(first.scale() * second.scale(), composed.scale(), 0.0D);
            assertVector(first.point(second.point(point)), composed.point(point));
            assertVector(first.vector(second.vector(vector)), composed.vector(vector));
            assertVector(first.direction(second.direction(vector)), composed.direction(vector));
            assertVector(point, first.compose(inverse).point(point));
        }
    }

    @Test
    void unitScaleMatchesTheRigidTransformBitForBit() {
        Random random = new Random(0x1D3AL);
        List<Frame> frames = FrameFixtures.all();
        double[] rigidOut = new double[3];
        double[] similarOut = new double[3];
        for (int sample = 0; sample < 1_000; sample++) {
            OpticTransform first = OpticTransform.between(frames.get(random.nextInt(frames.size())), worldPoint(random),
                frames.get(random.nextInt(frames.size())), worldPoint(random));
            OpticTransform second = OpticTransform.mirror(frames.get(random.nextInt(frames.size())), worldPoint(random),
                QuarterTurn.of(random.nextInt(4)));
            Similarity similarity = Similarity.of(first, 1.0D);
            assertTrue(similarity.isRigid());
            Vec3d point = worldPoint(random);
            first.pointInto(point.x(), point.y(), point.z(), rigidOut);
            similarity.pointInto(point.x(), point.y(), point.z(), similarOut);
            assertEquals(new Vec3d(rigidOut[0], rigidOut[1], rigidOut[2]), new Vec3d(similarOut[0], similarOut[1], similarOut[2]));
            assertEquals(first.point(point), similarity.point(point));
            assertEquals(first.vector(point), similarity.vector(point));
            assertEquals(first.vector(point), similarity.direction(point));
            assertEquals(first.inverse(), similarity.inverse().rigid());
            assertEquals(first.compose(second), similarity.compose(Similarity.of(second, 1.0D)).rigid());
            assertEquals(first.compose(second).point(point), similarity.compose(Similarity.of(second, 1.0D)).point(point));
        }
    }

    @Test
    void equalityCoversTheRigidPartAndTheScale() {
        OpticTransform rigid = OpticTransform.between(Frame.canonical(Face.N), SOURCE_ORIGIN, Frame.canonical(Face.S), TARGET_ORIGIN);
        assertEquals(Similarity.of(rigid, 2.0D), Similarity.of(rigid, 2.0D));
        assertEquals(Similarity.of(rigid, 2.0D).hashCode(), Similarity.of(rigid, 2.0D).hashCode());
        assertNotEquals(Similarity.of(rigid, 2.0D), Similarity.of(rigid, 3.0D));
        assertNotEquals(Similarity.of(rigid, 2.0D), Similarity.of(rigid.inverse(), 2.0D));
    }

    private static Similarity randomSimilarity(Random random) {
        List<Frame> frames = FrameFixtures.all();
        double scale = Math.pow(2.0D, random.nextDouble() * 6.0D - 3.0D);
        if (random.nextInt(4) == 0) {
            return Similarity.of(OpticTransform.mirror(frames.get(random.nextInt(frames.size())), randomPoint(random),
                QuarterTurn.of(random.nextInt(4))), scale);
        }
        return Similarity.between(frames.get(random.nextInt(frames.size())), randomPoint(random),
            frames.get(random.nextInt(frames.size())), randomPoint(random), scale);
    }

    private static Vec3d randomPoint(Random random) {
        return new Vec3d(range(random, 1_000.0D), range(random, 320.0D), range(random, 1_000.0D));
    }

    private static Vec3d worldPoint(Random random) {
        return new Vec3d(range(random, 30_000_000.0D), range(random, 320.0D), range(random, 30_000_000.0D));
    }

    private static double range(Random random, double extent) {
        return (random.nextDouble() * 2.0D - 1.0D) * extent;
    }

    private static void assertVector(Vec3d expected, Vec3d actual) {
        assertEquals(expected.x(), actual.x(), TOLERANCE);
        assertEquals(expected.y(), actual.y(), TOLERANCE);
        assertEquals(expected.z(), actual.z(), TOLERANCE);
    }
}
