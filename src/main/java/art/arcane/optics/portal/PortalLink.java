package art.arcane.optics.portal;

import java.util.Objects;
import java.util.UUID;

import art.arcane.optics.crossing.MomentumRule;
import art.arcane.optics.crossing.OrientationRule;
import art.arcane.optics.crossing.ScaleRule;
import art.arcane.optics.frame.QuarterTurn;
import art.arcane.optics.math.Vec3d;

public record PortalLink(UUID target, boolean mirror, QuarterTurn mirrorTurns, OrientationRule orientation, MomentumRule momentum, ScaleRule scale) {
    private static final MomentumRule PRESERVE = new MomentumRule(MomentumRule.Mode.PRESERVE, 1.0D, 0.0D, Vec3d.ZERO);

    public PortalLink {
        if (mirror && target != null) {
            throw new IllegalArgumentException("A mirror link reflects its own portal and cannot name a target: " + target);
        }
        if (!mirror && target == null) {
            throw new IllegalArgumentException("A portal link needs a target unless it is a mirror");
        }
        mirrorTurns = mirror && mirrorTurns != null ? mirrorTurns : QuarterTurn.DEGREES_0;
        orientation = orientation == null ? OrientationRule.FRAME : orientation;
        momentum = momentum == null ? PRESERVE : momentum;
        scale = scale == null ? ScaleRule.OFF : scale;
    }

    public static PortalLink to(UUID target) {
        return new PortalLink(Objects.requireNonNull(target, "target"), false, QuarterTurn.DEGREES_0, OrientationRule.FRAME, PRESERVE, ScaleRule.OFF);
    }

    public static PortalLink mirror(QuarterTurn turns) {
        return new PortalLink(null, true, Objects.requireNonNull(turns, "turns"), OrientationRule.FRAME, PRESERVE, ScaleRule.OFF);
    }

    public boolean isMirror() {
        return mirror;
    }

    public PortalLink withScale(ScaleRule scale) {
        return new PortalLink(target, mirror, mirrorTurns, orientation, momentum, Objects.requireNonNull(scale, "scale"));
    }

    PortalLink retargeted(UUID next) {
        return new PortalLink(Objects.requireNonNull(next, "target"), false, QuarterTurn.DEGREES_0, orientation, momentum, scale);
    }

    PortalLink mirrored(QuarterTurn turns) {
        return new PortalLink(null, true, Objects.requireNonNull(turns, "turns"), orientation, momentum, scale);
    }
}
