package art.arcane.optics.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import art.arcane.optics.math.Vec3d;

final class TransformFixtures {
    private TransformFixtures() {
    }

    static Quaternion rotation(SplittableRandom random) {
        double u1 = random.nextDouble();
        double u2 = random.nextDouble() * 2.0D * Math.PI;
        double u3 = random.nextDouble() * 2.0D * Math.PI;
        double low = Math.sqrt(1.0D - u1);
        double high = Math.sqrt(u1);
        return new Quaternion(low * Math.sin(u2), low * Math.cos(u2), high * Math.sin(u3), high * Math.cos(u3));
    }

    static Vec3d vector(SplittableRandom random, double extent) {
        return new Vec3d(random.nextDouble(-extent, extent), random.nextDouble(-extent, extent), random.nextDouble(-extent, extent));
    }

    static Vec3d direction(SplittableRandom random) {
        Vec3d vector = vector(random, 1.0D);
        while (vector.lengthSquared() < 1.0E-4D) {
            vector = vector(random, 1.0D);
        }
        return vector.normalize();
    }

    static Vec3d scale(SplittableRandom random) {
        return new Vec3d(random.nextDouble(0.5D, 2.0D), random.nextDouble(0.5D, 2.0D), random.nextDouble(0.5D, 2.0D));
    }

    static Affine shear(SplittableRandom random) {
        return Affine.shear(random.nextDouble(-0.5D, 0.5D), random.nextDouble(-0.5D, 0.5D), random.nextDouble(-0.5D, 0.5D),
            random.nextDouble(-0.5D, 0.5D), random.nextDouble(-0.5D, 0.5D), random.nextDouble(-0.5D, 0.5D));
    }

    static Affine general(SplittableRandom random) {
        return Affine.trs(vector(random, 50.0D), rotation(random), scale(random)).compose(shear(random));
    }

    static void assertVector(Vec3d expected, Vec3d actual, double tolerance) {
        assertEquals(expected.x(), actual.x(), tolerance, "x of " + actual + " expected " + expected);
        assertEquals(expected.y(), actual.y(), tolerance, "y of " + actual + " expected " + expected);
        assertEquals(expected.z(), actual.z(), tolerance, "z of " + actual + " expected " + expected);
    }

    static void assertAffine(Affine expected, Affine actual, double tolerance) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 4; column++) {
                assertEquals(expected.element(row, column), actual.element(row, column), tolerance,
                    "element " + row + "," + column + " of " + actual + " expected " + expected);
            }
        }
    }

    static void assertSameRotation(Quaternion expected, Quaternion actual, double tolerance) {
        double angle = relativeAngle(expected.normalize(), actual.normalize());
        assertTrue(angle <= tolerance, actual + " is not the rotation " + expected + " (off by " + angle + " radians)");
    }

    static double relativeAngle(Quaternion from, Quaternion to) {
        return from.conjugate().multiply(to).angle();
    }
}
