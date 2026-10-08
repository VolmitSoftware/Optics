package art.arcane.optics.portal;

import java.util.Objects;

import art.arcane.optics.crossing.PlaneCrossing;
import art.arcane.optics.math.Vec3d;

public record PortalCrossing(PortalDefinition portal, PlaneCrossing crossing, double column, double row, double fraction) {
    public PortalCrossing {
        Objects.requireNonNull(portal, "portal");
        Objects.requireNonNull(crossing, "crossing");
    }

    public Vec3d point() {
        return portal.planePoint(column, row);
    }
}
