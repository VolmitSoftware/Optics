package art.arcane.optics.portal;

import java.util.List;

import art.arcane.optics.aperture.CellAperture;
import art.arcane.optics.aperture.EndpointDirectory;
import art.arcane.optics.frame.QuarterTurn;
import art.arcane.optics.math.Box;

public final class PortalDirectory<W> implements EndpointDirectory<W, PortalDefinition> {
    private final PortalRegistry<W> registry;

    PortalDirectory(PortalRegistry<W> registry) {
        this.registry = registry;
    }

    @Override
    public List<PortalDefinition> endpoints() {
        return registry.all();
    }

    @Override
    public W world(PortalDefinition endpoint) {
        return registry.world(endpoint.id());
    }

    @Override
    public CellAperture aperture(PortalDefinition endpoint) {
        return endpoint.aperture();
    }

    @Override
    public Box view(PortalDefinition endpoint) {
        return endpoint.aperture().getArea();
    }

    @Override
    public boolean eligible(PortalDefinition endpoint) {
        PortalLink link = endpoint.link();
        return link != null && (link.isMirror() || registry.destination(endpoint) != null);
    }

    @Override
    public boolean mirror(PortalDefinition endpoint) {
        PortalLink link = endpoint.link();
        return link != null && link.isMirror();
    }

    @Override
    public QuarterTurn mirrorTurns(PortalDefinition endpoint) {
        PortalLink link = endpoint.link();
        return link != null && link.isMirror() ? link.mirrorTurns() : QuarterTurn.DEGREES_0;
    }

    @Override
    public PortalDefinition destination(PortalDefinition endpoint) {
        return registry.destination(endpoint);
    }

    @Override
    public double travelScale(PortalDefinition endpoint) {
        return endpoint.travelScale(registry.destination(endpoint));
    }
}
