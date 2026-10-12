package art.arcane.optics.demo.scene;

import java.util.Objects;

import art.arcane.optics.math.Vec3d;
import art.arcane.optics.portal.PortalCrossing;
import art.arcane.optics.portal.PortalDefinition;

public record CrossingRoute(PortalDefinition source, PortalDefinition destination) {
    public CrossingRoute {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
    }

    public PortalCrossing crossing(Vec3d start, Vec3d end, Vec3d velocity, Vec3d look) {
        return source.crossing(start, end, velocity, look);
    }

    public Vec3d arrival(PortalCrossing crossing) {
        if (!source.id().equals(crossing.portal().id())) {
            throw new IllegalArgumentException("Crossing must belong to the route source");
        }
        return crossing.crossing().outPoint(destination.frame(), destination.origin());
    }
}
