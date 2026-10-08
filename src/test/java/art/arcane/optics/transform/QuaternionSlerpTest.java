package art.arcane.optics.transform;

import static art.arcane.optics.transform.TransformFixtures.assertSameRotation;
import static art.arcane.optics.transform.TransformFixtures.relativeAngle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import art.arcane.optics.math.Vec3d;

final class QuaternionSlerpTest {
    @Test
    void slerpHitsBothEndpoints() {
        SplittableRandom random = new SplittableRandom(31L);
        for (int sample = 0; sample < 1_000; sample++) {
            Quaternion a = TransformFixtures.rotation(random);
            Quaternion b = TransformFixtures.rotation(random);
            assertSameRotation(a, a.slerp(b, 0.0D), 1.0E-9D);
            assertSameRotation(b, a.slerp(b, 1.0D), 1.0E-9D);
            assertSameRotation(a, a.nlerp(b, 0.0D), 1.0E-9D);
            assertSameRotation(b, a.nlerp(b, 1.0D), 1.0E-9D);
        }
    }

    @Test
    void slerpMovesAtAConstantAngularRate() {
        SplittableRandom random = new SplittableRandom(32L);
        for (int sample = 0; sample < 1_000; sample++) {
            Quaternion a = TransformFixtures.rotation(random);
            Quaternion b = TransformFixtures.rotation(random);
            double total = relativeAngle(a, b);
            assertEquals(total * 0.5D, relativeAngle(a, a.slerp(b, 0.5D)), 1.0E-9D);
            double t = random.nextDouble();
            Quaternion between = a.slerp(b, t);
            assertTrue(between.isNormalized(1.0E-12D));
            assertEquals(total * t, relativeAngle(a, between), 1.0E-9D);
            assertEquals(total * (1.0D - t), relativeAngle(between, b), 1.0E-9D);
        }
    }

    @Test
    void slerpTakesTheShortestPath() {
        SplittableRandom random = new SplittableRandom(33L);
        for (int sample = 0; sample < 1_000; sample++) {
            Quaternion a = TransformFixtures.rotation(random);
            Quaternion b = TransformFixtures.rotation(random);
            double t = random.nextDouble();
            assertSameRotation(a.slerp(b, t), a.slerp(b.negate(), t), 1.0E-9D);
            assertTrue(relativeAngle(a, b) <= Math.PI + 1.0E-12D);
        }
        Quaternion start = Quaternion.axisAngleDegrees(Vec3d.UNIT_Y, 10.0D);
        Quaternion end = Quaternion.axisAngleDegrees(Vec3d.UNIT_Y, 350.0D);
        assertSameRotation(Quaternion.IDENTITY, start.slerp(end, 0.5D), 1.0E-12D);
        assertSameRotation(Quaternion.IDENTITY, start.nlerp(end, 0.5D), 1.0E-12D);
    }

    @Test
    void nearlyIdenticalRotationsStayFinite() {
        Quaternion a = Quaternion.axisAngleDegrees(new Vec3d(1.0D, 1.0D, 0.0D), 33.0D);
        Quaternion b = a.multiply(Quaternion.axisAngle(Vec3d.UNIT_Z, 1.0E-13D));
        for (double t = 0.0D; t <= 1.0D; t += 0.125D) {
            Quaternion same = a.slerp(a, t);
            Quaternion close = a.slerp(b, t);
            assertTrue(same.isFinite() && close.isFinite());
            assertSameRotation(a, same, 1.0E-12D);
            assertSameRotation(a, close, 1.0E-12D);
        }
    }

    @Test
    void nlerpNormalizesItsResult() {
        SplittableRandom random = new SplittableRandom(34L);
        for (int sample = 0; sample < 1_000; sample++) {
            Quaternion a = TransformFixtures.rotation(random);
            Quaternion b = TransformFixtures.rotation(random);
            assertTrue(a.nlerp(b, random.nextDouble()).isNormalized(1.0E-12D));
        }
    }
}
