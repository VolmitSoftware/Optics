package art.arcane.optics.crossing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.frame.Similarity;
import art.arcane.optics.math.Box;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

final class StraddleGeometrySimilarityTest {
    private static final Vec3d ENTRY_ORIGIN = new Vec3d(11.0D, 65.5D, 20.5D);
    private static final Vec3d EXIT_ORIGIN = new Vec3d(-300.5D, 90.0D, 41.5D);
    private static final double[] SCALES = {1.0D / 3.0D, 1.0D, 3.0D};

    @Test
    void scaledBoxesAndMovesRoundTripThroughTheCrossingSimilarity() {
        Random random = new Random(0x5CA1EL);
        Frame source = Frame.canonical(Face.N);
        for (Face exit : Face.values()) {
            for (boolean front : new boolean[] {true, false}) {
                PlaneCrossing crossing = new PlaneCrossing(source.view(front), ENTRY_ORIGIN, new Vec3d(11.0D, 65.5D, 20.4D),
                    new Vec3d(0.0D, 0.0D, -0.3D), new Vec3d(0.0D, 0.0D, -1.0D), front);
                for (double scale : SCALES) {
                    Similarity toward = crossing.toward(Frame.canonical(exit), EXIT_ORIGIN, scale);
                    assertEquals(crossing.toward(Frame.canonical(exit), EXIT_ORIGIN), toward.rigid());
                    assertEquals(scale, toward.scale(), 0.0D);
                    assertEquals(EXIT_ORIGIN, toward.point(ENTRY_ORIGIN));
                    for (int sample = 0; sample < 100; sample++) {
                        Vec3d move = new Vec3d(random.nextDouble() - 0.5D, random.nextDouble() - 0.5D, random.nextDouble() - 0.5D);
                        Vec3d mapped = StraddleGeometry.mappedMove(move, toward);
                        assertEquals(toward.vector(move), mapped);
                        assertVector(move, StraddleGeometry.unmappedMove(mapped, toward), 1.0E-12D);
                        double x = 10.0D + random.nextDouble() * 2.0D;
                        double y = 64.0D + random.nextDouble() * 2.0D;
                        double z = 19.5D + random.nextDouble() * 2.0D;
                        Box box = new Box(x - 0.3D, x + 0.3D, y, y + 1.8D, z - 0.3D, z + 0.3D);
                        Box mappedBox = StraddleGeometry.mappedBox(box, toward);
                        assertBox(toward.box(box), mappedBox);
                        assertEquals(box.volume() * scale * scale * scale, mappedBox.volume(), 1.0E-9D);
                        assertBox(box, StraddleGeometry.mappedBox(mappedBox, toward.inverse()));
                    }
                }
            }
        }
    }

    @Test
    void unitScaleMatchesTheRigidOverloadsExactly() {
        Random random = new Random(0x0B1L);
        PlaneCrossing crossing = new PlaneCrossing(Frame.canonical(Face.U), ENTRY_ORIGIN, new Vec3d(11.0D, 65.4D, 20.5D),
            new Vec3d(0.0D, -0.4D, 0.0D), new Vec3d(0.0D, -1.0D, 0.0D), true);
        OpticTransform rigid = crossing.toward(Frame.canonical(Face.W), EXIT_ORIGIN);
        Similarity similarity = crossing.toward(Frame.canonical(Face.W), EXIT_ORIGIN, 1.0D);
        for (int sample = 0; sample < 200; sample++) {
            Vec3d move = new Vec3d(random.nextDouble() - 0.5D, random.nextDouble() - 0.5D, random.nextDouble() - 0.5D);
            assertEquals(StraddleGeometry.mappedMove(move, rigid), StraddleGeometry.mappedMove(move, similarity));
            assertEquals(StraddleGeometry.unmappedMove(move, rigid), StraddleGeometry.unmappedMove(move, similarity));
            Box box = new Box(move.x(), move.x() + 0.6D, move.y(), move.y() + 1.8D, move.z(), move.z() + 0.6D);
            assertBox(StraddleGeometry.mappedBox(box, rigid), StraddleGeometry.mappedBox(box, similarity));
        }
    }

    private static void assertVector(Vec3d expected, Vec3d actual, double tolerance) {
        assertEquals(expected.x(), actual.x(), tolerance);
        assertEquals(expected.y(), actual.y(), tolerance);
        assertEquals(expected.z(), actual.z(), tolerance);
    }

    private static void assertBox(Box expected, Box actual) {
        assertEquals(expected.getXa(), actual.getXa(), 1.0E-9D);
        assertEquals(expected.getXb(), actual.getXb(), 1.0E-9D);
        assertEquals(expected.getYa(), actual.getYa(), 1.0E-9D);
        assertEquals(expected.getYb(), actual.getYb(), 1.0E-9D);
        assertEquals(expected.getZa(), actual.getZa(), 1.0E-9D);
        assertEquals(expected.getZb(), actual.getZb(), 1.0E-9D);
    }
}
