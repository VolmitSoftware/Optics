package art.arcane.optics.portal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import art.arcane.optics.aperture.ApertureCells;
import art.arcane.optics.aperture.ApertureDescriptor;
import art.arcane.optics.aperture.Endpoint;
import art.arcane.optics.aperture.SizeRatio;
import art.arcane.optics.claim.BlockClaim;
import art.arcane.optics.crossing.PlaneCrossing;
import art.arcane.optics.crossing.ScaleRule;
import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.frame.QuarterTurn;
import art.arcane.optics.frame.Similarity;
import art.arcane.optics.frame.ViewWindow;
import art.arcane.optics.math.Axis;
import art.arcane.optics.math.Box;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.shape.PlaneShape;
import art.arcane.optics.shape.PlaneTransform;
import art.arcane.optics.shape.ShapeDescriptor;
import art.arcane.optics.shape.ShapeRaster;
import art.arcane.optics.transform.Affine;
import art.arcane.optics.transform.Quaternion;

public final class PortalDefinition implements Endpoint {
    private static final double BLOCK_EXTENT = 0.999D;
    private static final double PERMUTATION_TOLERANCE = 1.0E-6D;

    private final UUID id;
    private final Frame frame;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final int columns;
    private final int rows;
    private final double planeOffset;
    private final ShapeDescriptor shape;
    private final PortalLink link;
    private final Map<String, String> metadata;
    private final int normalAxis;
    private final int columnAxis;
    private final int rowAxis;
    private final double normalSign;
    private final double planeCoordinate;
    private final boolean rightOnColumns;
    private final Vec3d origin;
    private final PlaneTransform orientation;
    private final PlaneShape planeShape;
    private final ShapeRaster raster;
    private volatile ApertureCells aperture;

    PortalDefinition(UUID id, Layout layout, ShapeDescriptor shape, PortalLink link, Map<String, String> metadata, PortalDefinition previous) {
        this.id = Objects.requireNonNull(id, "id");
        frame = Objects.requireNonNull(layout.frame(), "frame");
        originX = layout.originX();
        originY = layout.originY();
        originZ = layout.originZ();
        columns = layout.columns();
        rows = layout.rows();
        planeOffset = layout.planeOffset();
        requirePlacement(columns, rows, planeOffset);
        if (link != null && !link.isMirror() && link.target().equals(id)) {
            throw new IllegalArgumentException("Portal " + id + " cannot link to itself");
        }
        this.shape = shape == null ? ShapeDescriptor.FULL : shape;
        this.link = link;
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        Face normal = frame.getNormal();
        Frame canonical = Frame.canonical(normal);
        normalAxis = normal.axisIndex();
        columnAxis = canonical.getRight().axisIndex();
        rowAxis = canonical.getUp().axisIndex();
        normalSign = normal.sign();
        rightOnColumns = frame.getRight().axisIndex() == columnAxis;
        planeCoordinate = originComponent(normalAxis) + 0.5D + normalSign * planeOffset;
        double[] center = new double[3];
        center[normalAxis] = planeCoordinate;
        center[columnAxis] = originComponent(columnAxis) + columns * 0.5D;
        center[rowAxis] = originComponent(rowAxis) + rows * 0.5D;
        origin = Vec3d.of(center);
        orientation = orientation(frame, columnAxis, rowAxis);
        planeShape = reusable(previous) ? previous.planeShape
            : PlaneShape.fit(this.shape.shape(), this.shape.fit(), columns, rows, orientation);
        raster = planeShape.isFull() ? null : planeShape.raster(ShapeRaster.DEFAULT_SUBSAMPLES);
        if (raster != null && raster.insideCount() == 0) {
            throw new IllegalArgumentException("Shape " + this.shape + " leaves no open cell on a " + columns + "x" + rows + " portal");
        }
        aperture = reusable(previous) && sameCells(previous) ? previous.aperture : null;
    }

    public static PortalBuilder builder() {
        return new PortalBuilder();
    }

    public static PortalBuilder builder(PortalDefinition from) {
        return new PortalBuilder(from);
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public Frame frame() {
        return frame;
    }

    @Override
    public Vec3d origin() {
        return origin;
    }

    public int originX() {
        return originX;
    }

    public int originY() {
        return originY;
    }

    public int originZ() {
        return originZ;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public double planeOffset() {
        return planeOffset;
    }

    public ShapeDescriptor shape() {
        return shape;
    }

    public PortalLink link() {
        return link;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    public String metadata(String key) {
        return metadata.get(key);
    }

    public Box area() {
        return new Box(originX, maxCell(0) + BLOCK_EXTENT, originY, maxCell(1) + BLOCK_EXTENT, originZ, maxCell(2) + BLOCK_EXTENT);
    }

    public Vec3d center() {
        return origin;
    }

    public double width() {
        return columns;
    }

    public double height() {
        return rows;
    }

    public PlaneShape planeShape() {
        return planeShape;
    }

    public ApertureCells aperture() {
        ApertureCells cells = aperture;
        if (cells != null) {
            return cells;
        }
        ApertureCells built = new ApertureCells();
        if (raster == null) {
            built.setArea(area());
        } else {
            List<Vec3d> open = new ArrayList<Vec3d>(raster.insideCount());
            raster.cells(originX, originY, originZ, frame.getNormal(), null, open);
            built.restore(area(), open);
        }
        aperture = built;
        return built;
    }

    public ApertureDescriptor.Source source(boolean frontSide, int depthBlocks) {
        boolean mirror = link != null && link.isMirror();
        long targetIdentity = link == null || mirror ? 0L : link.target().getMostSignificantBits() ^ link.target().getLeastSignificantBits();
        return new ApertureDescriptor.Source(aperture(), frame, frontSide, mirror, mirror ? link.mirrorTurns().getQuarterTurns() : 0, 0.0D, 0.0D,
            1.0D, depthBlocks, 0, ApertureDescriptor.BLACKOUT_OFF, 0, ApertureDescriptor.MASK_AIR_PROJECT, BlockClaim.LightingPolicy.LOCAL, 0, 0,
            planeOffset, 0, targetIdentity, shape, List.of());
    }

    public Affine affine() {
        Face right = frame.getRight();
        Face up = frame.getUp();
        Face normal = frame.getNormal();
        double halfWidth = frameWidth() * 0.5D;
        double halfHeight = frameHeight() * 0.5D;
        return Affine.of(right.x() * halfWidth, up.x() * halfHeight, normal.x(), origin.x(),
            right.y() * halfWidth, up.y() * halfHeight, normal.y(), origin.y(),
            right.z() * halfWidth, up.z() * halfHeight, normal.z(), origin.z());
    }

    public Similarity toward(PortalDefinition destination, boolean frontSide) {
        if (link != null && link.isMirror()) {
            return Similarity.of(ViewWindow.mirror(origin, frame, link.mirrorTurns(), frontSide, 0.0D).toward(), 1.0D);
        }
        Objects.requireNonNull(destination, "destination");
        OpticTransform rigid = OpticTransform.between(frame.view(frontSide), origin, destination.frame.view(frontSide), destination.origin);
        return Similarity.of(rigid, travelScale(destination));
    }

    public SizeRatio sizeRatio(PortalDefinition destination) {
        return SizeRatio.between(frame, columns, rows, destination.frame, destination.columns, destination.rows);
    }

    public PortalDefinition moved(int dx, int dy, int dz) {
        return movedTo(originX + dx, originY + dy, originZ + dz);
    }

    public PortalDefinition movedTo(int originX, int originY, int originZ) {
        return with(new Layout(frame, originX, originY, originZ, columns, rows, planeOffset), shape, link, metadata);
    }

    public PortalDefinition centeredAt(Vec3d center) {
        return with(Layout.centered(frame, center, columns, rows, planeOffset), shape, link, metadata);
    }

    public PortalDefinition rotated(QuarterTurn turns) {
        Frame next = frame;
        for (int turn = 0; turn < turns.getQuarterTurns(); turn++) {
            next = next.rotateClockwise();
        }
        return reoriented(next, origin);
    }

    public PortalDefinition rotatedAbout(Axis axis, QuarterTurn turns) {
        AxisPermutation permutation = Quaternion.axisAngleDegrees(axis.positive(), turns.getDegrees()).permutation(PERMUTATION_TOLERANCE);
        double[] rotated = new double[3];
        permutation.vectorInto(origin.x(), origin.y(), origin.z(), rotated);
        return transformed(OpticTransform.of(permutation, origin.x() - rotated[0], origin.y() - rotated[1], origin.z() - rotated[2]));
    }

    public PortalDefinition facing(Face normal) {
        return reoriented(frame.withNormal(normal), origin);
    }

    public PortalDefinition flipped() {
        return with(new Layout(frame.flipNormal(), originX, originY, originZ, columns, rows, -planeOffset), shape, link, metadata);
    }

    public PortalDefinition mirroredU() {
        return withShape(shape.isFull() ? shape : shape.transformed(PlaneTransform.flipU()));
    }

    public PortalDefinition mirroredV() {
        return withShape(shape.isFull() ? shape : shape.transformed(PlaneTransform.flipV()));
    }

    public PortalDefinition scaled(double factor) {
        requireFactor(factor);
        return resized(scaledCount(columns, factor), scaledCount(rows, factor));
    }

    public PortalDefinition scaled(double factorU, double factorV) {
        requireFactor(factorU);
        requireFactor(factorV);
        int width = scaledCount(frameWidth(), factorU);
        int height = scaledCount(frameHeight(), factorV);
        return rightOnColumns ? resized(width, height) : resized(height, width);
    }

    public PortalDefinition resized(int columns, int rows) {
        return with(Layout.centered(frame, origin, columns, rows, planeOffset), shape, link, metadata);
    }

    public PortalDefinition stretched(int deltaColumns, int deltaRows) {
        int nextColumns = Math.max(1, columns + deltaColumns);
        int nextRows = Math.max(1, rows + deltaRows);
        int[] next = {originX, originY, originZ};
        if (growthSign(columnAxis) < 0) {
            next[columnAxis] += columns - nextColumns;
        }
        if (growthSign(rowAxis) < 0) {
            next[rowAxis] += rows - nextRows;
        }
        return with(new Layout(frame, next[0], next[1], next[2], nextColumns, nextRows, planeOffset), shape, link, metadata);
    }

    public PortalDefinition withShape(ShapeDescriptor shape) {
        return with(layout(), Objects.requireNonNull(shape, "shape"), link, metadata);
    }

    public PortalDefinition withShape(String text) {
        return withShape(ShapeDescriptor.parse(text));
    }

    public PortalDefinition shapeRotated(double degrees) {
        return withShape(shape.transformed(PlaneTransform.rotation(degrees)));
    }

    public PortalDefinition shapeScaled(double factor) {
        return withShape(shape.transformed(PlaneTransform.scale(factor)));
    }

    public PortalDefinition withPlaneOffset(double planeOffset) {
        return with(new Layout(frame, originX, originY, originZ, columns, rows, planeOffset), shape, link, metadata);
    }

    public PortalDefinition linked(PortalLink link) {
        return with(layout(), shape, Objects.requireNonNull(link, "link"), metadata);
    }

    public PortalDefinition unlinked() {
        return link == null ? this : with(layout(), shape, null, metadata);
    }

    public PortalDefinition withMetadata(String key, String value) {
        LinkedHashMap<String, String> next = new LinkedHashMap<String, String>(metadata);
        next.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
        return with(layout(), shape, link, Collections.unmodifiableMap(next));
    }

    public PortalDefinition withoutMetadata(String key) {
        if (!metadata.containsKey(key)) {
            return this;
        }
        LinkedHashMap<String, String> next = new LinkedHashMap<String, String>(metadata);
        next.remove(key);
        return with(layout(), shape, link, Collections.unmodifiableMap(next));
    }

    public PortalDefinition transformed(OpticTransform rigid) {
        return reoriented(rigid.frame(frame), rigid.point(origin));
    }

    public boolean containsCell(int x, int y, int z) {
        if (Axis.component(normalAxis, x, y, z) != originComponent(normalAxis)) {
            return false;
        }
        int column = Axis.component(columnAxis, x, y, z) - originComponent(columnAxis);
        int row = Axis.component(rowAxis, x, y, z) - originComponent(rowAxis);
        if (column < 0 || row < 0 || column >= columns || row >= rows) {
            return false;
        }
        return raster == null || raster.inside(column, row);
    }

    public boolean containsPoint(Vec3d point, double planeTolerance) {
        if (!(Math.abs(signedDistance(point.x(), point.y(), point.z())) <= planeTolerance)) {
            return false;
        }
        return open(Axis.component(columnAxis, point.x(), point.y(), point.z()) - originComponent(columnAxis),
            Axis.component(rowAxis, point.x(), point.y(), point.z()) - originComponent(rowAxis));
    }

    public void cellCoordinates(double x, double y, double z, double[] out2) {
        out2[0] = Axis.component(columnAxis, x, y, z) - originComponent(columnAxis);
        out2[1] = Axis.component(rowAxis, x, y, z) - originComponent(rowAxis);
    }

    public PortalCrossing crossing(Vec3d start, Vec3d end, Vec3d velocity, Vec3d look) {
        double fraction = crossingFraction(start, end);
        if (Double.isNaN(fraction)) {
            return null;
        }
        double hitX = start.x() + (end.x() - start.x()) * fraction;
        double hitY = start.y() + (end.y() - start.y()) * fraction;
        double hitZ = start.z() + (end.z() - start.z()) * fraction;
        double startDistance = signedDistance(start.x(), start.y(), start.z());
        Vec3d motion = velocity == null ? end.subtract(start) : velocity;
        Vec3d facing = look == null ? motion : look;
        boolean frontSide = startDistance == 0.0D ? Axis.component(normalAxis, motion.x(), motion.y(), motion.z()) * normalSign <= 0.0D
            : startDistance > 0.0D;
        PlaneCrossing crossing = new PlaneCrossing(frame.view(frontSide), origin, end, motion, facing, frontSide);
        return new PortalCrossing(this, crossing, Axis.component(columnAxis, hitX, hitY, hitZ) - originComponent(columnAxis),
            Axis.component(rowAxis, hitX, hitY, hitZ) - originComponent(rowAxis), fraction);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof PortalDefinition that
            && id.equals(that.id)
            && frame.equals(that.frame)
            && originX == that.originX && originY == that.originY && originZ == that.originZ
            && columns == that.columns && rows == that.rows
            && Double.compare(planeOffset, that.planeOffset) == 0
            && shape.equals(that.shape)
            && Objects.equals(link, that.link)
            && metadata.equals(that.metadata);
    }

    @Override
    public int hashCode() {
        int hash = id.hashCode();
        hash = 31 * hash + frame.hashCode();
        hash = 31 * hash + originX;
        hash = 31 * hash + originY;
        hash = 31 * hash + originZ;
        hash = 31 * hash + columns;
        hash = 31 * hash + rows;
        hash = 31 * hash + Double.hashCode(planeOffset);
        hash = 31 * hash + shape.hashCode();
        hash = 31 * hash + Objects.hashCode(link);
        return 31 * hash + metadata.hashCode();
    }

    @Override
    public String toString() {
        return "PortalDefinition[" + id + " facing " + frame.getNormal() + " up " + frame.getUp() + " at " + originX + "," + originY + "," + originZ
            + " " + columns + "x" + rows + (planeOffset == 0.0D ? "" : " offset " + planeOffset) + " shape " + shape.format()
            + (link == null ? "" : link.isMirror() ? " mirror " + link.mirrorTurns() : " to " + link.target())
            + (metadata.isEmpty() ? "" : " " + metadata) + "]";
    }

    Layout layout() {
        return new Layout(frame, originX, originY, originZ, columns, rows, planeOffset);
    }

    Vec3d planePoint(double column, double row) {
        double[] point = new double[3];
        point[normalAxis] = planeCoordinate;
        point[columnAxis] = originComponent(columnAxis) + column;
        point[rowAxis] = originComponent(rowAxis) + row;
        return Vec3d.of(point);
    }

    double travelScale(PortalDefinition destination) {
        if (link == null || link.isMirror() || destination == null || link.scale().mode() == ScaleRule.Mode.OFF) {
            return 1.0D;
        }
        return link.scale().travelScale(sizeRatio(destination));
    }

    double crossingFraction(Vec3d start, Vec3d end) {
        double startDistance = signedDistance(start.x(), start.y(), start.z());
        double endDistance = signedDistance(end.x(), end.y(), end.z());
        if (startDistance == endDistance || startDistance > 0.0D && endDistance > 0.0D || startDistance < 0.0D && endDistance < 0.0D) {
            return Double.NaN;
        }
        double fraction = startDistance / (startDistance - endDistance);
        double hitX = start.x() + (end.x() - start.x()) * fraction;
        double hitY = start.y() + (end.y() - start.y()) * fraction;
        double hitZ = start.z() + (end.z() - start.z()) * fraction;
        return open(Axis.component(columnAxis, hitX, hitY, hitZ) - originComponent(columnAxis),
            Axis.component(rowAxis, hitX, hitY, hitZ) - originComponent(rowAxis)) ? fraction : Double.NaN;
    }

    double planeDistance(double x, double y, double z) {
        return Math.abs(signedDistance(x, y, z));
    }

    double boundMin(int axis) {
        return axis == normalAxis ? Math.min(originComponent(axis), planeCoordinate) : originComponent(axis);
    }

    double boundMax(int axis) {
        double cells = maxCell(axis) + BLOCK_EXTENT;
        return axis == normalAxis ? Math.max(cells, planeCoordinate) : cells;
    }

    boolean areaOverlaps(Box box) {
        return maxCell(0) + BLOCK_EXTENT >= box.getXa() && originX <= box.getXb()
            && maxCell(1) + BLOCK_EXTENT >= box.getYa() && originY <= box.getYb()
            && maxCell(2) + BLOCK_EXTENT >= box.getZa() && originZ <= box.getZb();
    }

    private PortalDefinition with(Layout layout, ShapeDescriptor shape, PortalLink link, Map<String, String> metadata) {
        PortalDefinition next = new PortalDefinition(id, layout, shape, link, metadata, this);
        return next.equals(this) ? this : next;
    }

    private PortalDefinition reoriented(Frame next, Vec3d center) {
        int width = frameWidth();
        int height = frameHeight();
        boolean nextRightOnColumns = next.getRight().axisIndex() == Frame.canonical(next.getNormal()).getRight().axisIndex();
        return with(Layout.centered(next, center, nextRightOnColumns ? width : height, nextRightOnColumns ? height : width, planeOffset), shape,
            link, metadata);
    }

    private int frameWidth() {
        return rightOnColumns ? columns : rows;
    }

    private int frameHeight() {
        return rightOnColumns ? rows : columns;
    }

    private int growthSign(int axis) {
        Face right = frame.getRight();
        return right.axisIndex() == axis ? right.sign() : frame.getUp().sign();
    }

    private boolean open(double column, double row) {
        if (!(column >= 0.0D && row >= 0.0D && column < columns && row < rows)) {
            return false;
        }
        return raster == null || raster.inside((int) column, (int) row) && planeShape.contains(column, row);
    }

    private double signedDistance(double x, double y, double z) {
        return (Axis.component(normalAxis, x, y, z) - planeCoordinate) * normalSign;
    }

    private int originComponent(int axis) {
        return Axis.component(axis, originX, originY, originZ);
    }

    private int maxCell(int axis) {
        if (axis == columnAxis) {
            return originComponent(axis) + columns - 1;
        }
        if (axis == rowAxis) {
            return originComponent(axis) + rows - 1;
        }
        return originComponent(axis);
    }

    private boolean reusable(PortalDefinition previous) {
        return previous != null && previous.columns == columns && previous.rows == rows && previous.shape.equals(shape)
            && previous.orientation.equals(orientation);
    }

    private boolean sameCells(PortalDefinition previous) {
        return previous.frame.getNormal().axisIndex() == normalAxis && previous.originX == originX && previous.originY == originY
            && previous.originZ == originZ;
    }

    private static PlaneTransform orientation(Frame frame, int columnAxis, int rowAxis) {
        Face right = frame.getRight();
        Face up = frame.getUp();
        return new PlaneTransform(right.component(columnAxis), up.component(columnAxis), right.component(rowAxis), up.component(rowAxis), 0.0D,
            0.0D);
    }

    private static void requirePlacement(int columns, int rows, double planeOffset) {
        if (columns < 1 || rows < 1 || columns > ApertureDescriptor.MAX_APERTURE_EDGE || rows > ApertureDescriptor.MAX_APERTURE_EDGE
            || (long) columns * rows > ApertureDescriptor.MAX_APERTURE_CELLS) {
            throw new IllegalArgumentException("Portal size " + columns + "x" + rows + " is outside 1.." + ApertureDescriptor.MAX_APERTURE_EDGE
                + " per edge and " + ApertureDescriptor.MAX_APERTURE_CELLS + " cells");
        }
        if (!Double.isFinite(planeOffset)) {
            throw new IllegalArgumentException("Portal plane offset must be finite: " + planeOffset);
        }
    }

    private static int scaledCount(int count, double factor) {
        double scaled = Math.floor(count * factor + 0.5D);
        return scaled >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, (int) scaled);
    }

    private static void requireFactor(double factor) {
        if (!Double.isFinite(factor) || factor <= 0.0D) {
            throw new IllegalArgumentException("Portal scale factor must be finite and positive: " + factor);
        }
    }

    record Layout(Frame frame, int originX, int originY, int originZ, int columns, int rows, double planeOffset) {
        static Layout centered(Frame frame, Vec3d center, int columns, int rows, double planeOffset) {
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(center, "center");
            Face normal = frame.getNormal();
            Frame canonical = Frame.canonical(normal);
            int normalAxis = normal.axisIndex();
            int columnAxis = canonical.getRight().axisIndex();
            int rowAxis = canonical.getUp().axisIndex();
            int[] cell = new int[3];
            cell[normalAxis] = (int) Math.floor(center.component(normalAxis) - normal.sign() * planeOffset);
            cell[columnAxis] = (int) Math.floor(center.component(columnAxis) - columns * 0.5D + 0.5D);
            cell[rowAxis] = (int) Math.floor(center.component(rowAxis) - rows * 0.5D + 0.5D);
            return new Layout(frame, cell[0], cell[1], cell[2], columns, rows, planeOffset);
        }
    }
}
