package art.arcane.optics.portal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;

import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.QuarterTurn;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.shape.ShapeDescriptor;

public final class PortalBuilder {
    private final LinkedHashMap<String, String> metadata;
    private UUID id;
    private Frame frame;
    private int originX;
    private int originY;
    private int originZ;
    private boolean placed;
    private Vec3d center;
    private int columns;
    private int rows;
    private double planeOffset;
    private ShapeDescriptor shape;
    private PortalLink link;

    PortalBuilder() {
        metadata = new LinkedHashMap<String, String>();
        columns = 1;
        rows = 1;
        shape = ShapeDescriptor.FULL;
    }

    PortalBuilder(PortalDefinition from) {
        metadata = new LinkedHashMap<String, String>(from.metadata());
        id = from.id();
        frame = from.frame();
        originX = from.originX();
        originY = from.originY();
        originZ = from.originZ();
        placed = true;
        columns = from.columns();
        rows = from.rows();
        planeOffset = from.planeOffset();
        shape = from.shape();
        link = from.link();
    }

    public PortalBuilder id(UUID id) {
        this.id = Objects.requireNonNull(id, "id");
        return this;
    }

    public PortalBuilder frame(Frame frame) {
        this.frame = Objects.requireNonNull(frame, "frame");
        return this;
    }

    public PortalBuilder frame(Face normal, Face up) {
        return frame(Frame.fromNormalUp(normal, up));
    }

    public PortalBuilder facing(Face normal) {
        return frame(Frame.canonical(normal));
    }

    public PortalBuilder origin(int x, int y, int z) {
        originX = x;
        originY = y;
        originZ = z;
        placed = true;
        center = null;
        return this;
    }

    public PortalBuilder center(Vec3d center) {
        this.center = Objects.requireNonNull(center, "center");
        placed = false;
        return this;
    }

    public PortalBuilder size(int columns, int rows) {
        this.columns = columns;
        this.rows = rows;
        return this;
    }

    public PortalBuilder planeOffset(double planeOffset) {
        this.planeOffset = planeOffset;
        return this;
    }

    public PortalBuilder shape(ShapeDescriptor shape) {
        this.shape = Objects.requireNonNull(shape, "shape");
        return this;
    }

    public PortalBuilder shape(String text) {
        return shape(ShapeDescriptor.parse(text));
    }

    public PortalBuilder link(PortalLink link) {
        this.link = link;
        return this;
    }

    public PortalBuilder linkTo(UUID target) {
        return link(PortalLink.to(target));
    }

    public PortalBuilder mirror(QuarterTurn turns) {
        return link(PortalLink.mirror(turns));
    }

    public PortalBuilder metadata(String key, String value) {
        metadata.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
        return this;
    }

    public PortalDefinition build() {
        if (frame == null) {
            throw new IllegalArgumentException("A portal needs a frame: call frame(...) or facing(...)");
        }
        if (!placed && center == null) {
            throw new IllegalArgumentException("A portal needs a placement: call origin(...) or center(...)");
        }
        PortalDefinition.Layout layout = placed ? new PortalDefinition.Layout(frame, originX, originY, originZ, columns, rows, planeOffset)
            : PortalDefinition.Layout.centered(frame, center, columns, rows, planeOffset);
        return new PortalDefinition(id == null ? UUID.randomUUID() : id, layout, shape, link,
            Collections.unmodifiableMap(new LinkedHashMap<String, String>(metadata)), null);
    }
}
