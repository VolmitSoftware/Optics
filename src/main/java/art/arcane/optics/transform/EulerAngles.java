package art.arcane.optics.transform;

import java.util.Objects;

public record EulerAngles(double x, double y, double z, EulerOrder order) {
    private static final double FULL_TURN = 360.0D;
    private static final double HALF_TURN = 180.0D;

    public EulerAngles {
        Objects.requireNonNull(order, "order");
    }

    public static EulerAngles yawPitchRoll(double yawDegrees, double pitchDegrees, double rollDegrees) {
        return new EulerAngles(pitchDegrees, -yawDegrees, rollDegrees, EulerOrder.YXZ);
    }

    public Quaternion quaternion() {
        return Quaternion.euler(this);
    }

    public EulerAngles wrapped() {
        return new EulerAngles(wrap(x), wrap(y), wrap(z), order);
    }

    double component(int axis) {
        return axis == 0 ? x : axis == 1 ? y : z;
    }

    private static double wrap(double degrees) {
        return degrees - FULL_TURN * Math.ceil((degrees - HALF_TURN) / FULL_TURN);
    }
}
