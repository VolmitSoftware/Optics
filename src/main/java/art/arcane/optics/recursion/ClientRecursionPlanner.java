package art.arcane.optics.recursion;

import java.util.ArrayList;
import java.util.List;

import art.arcane.optics.math.Vec3d;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.aperture.ApertureCells;
import art.arcane.optics.volume.PlaneWindow;
import art.arcane.optics.volume.ApertureSlab;
import art.arcane.optics.math.Box;
import art.arcane.optics.math.Face;
import art.arcane.optics.aperture.ApertureDescriptor;

public final class ClientRecursionPlanner {
    private static final double EPSILON = 1.0E-7D;

    private final int depthCap;

    public ClientRecursionPlanner(int depthCap) {
        this.depthCap = Math.max(0, depthCap);
    }

    public List<NestedCone> plan(ApertureDescriptor root, double eyeX, double eyeY, double eyeZ) {
        int limit = Math.min(depthCap, root.recursionDepth());
        if (limit <= 0 || root.nested().isEmpty() || !root.valid()) {
            return List.of();
        }
        List<NestedCone> cones = new ArrayList<NestedCone>(root.nested().size());
        List<Window> chain = List.of(new Window(root, OpticTransform.IDENTITY, List.of(), eyeX, eyeY, eyeZ));
        descend(root, OpticTransform.IDENTITY, List.of(), chain, 1, limit, eyeX, eyeY, eyeZ, cones, new ApertureOverlap());
        return cones;
    }

    public static boolean destinationReaches(ApertureDescriptor parent, OpticTransform transform, Box destinationArea) {
        if (destinationArea == null || transform == null) {
            return false;
        }
        Vec3d center = transform.point(destinationArea.center());
        double[] extent = new double[3];
        transform.vectorInto((destinationArea.getXb() - destinationArea.getXa()) * 0.5D, (destinationArea.getYb() - destinationArea.getYa()) * 0.5D,
            (destinationArea.getZb() - destinationArea.getZa()) * 0.5D, extent);
        double dx = Math.abs(extent[0]);
        double dy = Math.abs(extent[1]);
        double dz = Math.abs(extent[2]);
        Box area = new Box(center.x() - dx, center.x() + dx, center.y() - dy, center.y() + dy, center.z() - dz, center.z() + dz);
        Box aperture = parent.apertureArea();
        ApertureSlab volume = ApertureSlab.of(aperture, parent.frame(), parent.planeCoordinate(), parent.frontSide(),
            parent.depthBlocks(), 0.0D);
        int normalAxis = volume.normalAxis();
        if (!volume.reaches(volume.distance(area.min(normalAxis)), volume.distance(area.max(normalAxis)))) {
            return false;
        }
        double distance = volume.maxDepth();
        for (int axis = 0; axis < 3; axis++) {
            if (axis != normalAxis && (area.max(axis) < aperture.min(axis) - distance
                || area.min(axis) > aperture.max(axis) + distance)) {
                return false;
            }
        }
        return true;
    }

    private static void descend(ApertureDescriptor parent, OpticTransform parentTransform, List<ApertureDescriptor> parentReflections,
                                List<Window> chain, int depth, int limit, double eyeX, double eyeY, double eyeZ, List<NestedCone> out, ApertureOverlap overlap) {
        OpticTransform transform = parent.mirror() ? parentTransform.compose(parent.mirrorTransform()) : parentTransform;
        List<ApertureDescriptor> reflections = parent.mirror() ? reflections(parent, parentReflections) : parentReflections;
        double[] scratch = new double[3];
        for (ApertureDescriptor child : parent.nested()) {
            if (!child.valid()) {
                continue;
            }
            Window window = new Window(child, transform, reflections, eyeX, eyeY, eyeZ);
            if (!window.servesEye() || (!anyCornerVisible(chain, window, scratch) && !overlap.reaches(chain, window))) {
                continue;
            }
            out.add(new NestedCone(child, depth, chain, window));
            int childLimit = Math.min(limit, depth + child.recursionDepth());
            if (depth < childLimit && !child.nested().isEmpty()) {
                List<Window> childChain = new ArrayList<Window>(chain.size() + 1);
                childChain.addAll(chain);
                childChain.add(window);
                descend(child, transform, reflections, List.copyOf(childChain), depth + 1, childLimit, eyeX, eyeY, eyeZ, out, overlap);
            }
        }
    }

    private static List<ApertureDescriptor> reflections(ApertureDescriptor mirror, List<ApertureDescriptor> parentReflections) {
        List<ApertureDescriptor> chain = new ArrayList<ApertureDescriptor>(parentReflections.size() + 1);
        chain.add(mirror);
        chain.addAll(parentReflections);
        return List.copyOf(chain);
    }

    private static boolean anyCornerVisible(List<Window> chain, Window window, double[] scratch) {
        Box area = window.area;
        Vec3d center = area.center();
        window.transform.pointInto(center.x(), center.y(), center.z(), scratch);
        if (visible(chain, scratch[0], scratch[1], scratch[2])) {
            return true;
        }
        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? area.getXa() : area.getXb();
            double y = (corner & 2) == 0 ? area.getYa() : area.getYb();
            double z = (corner & 4) == 0 ? area.getZa() : area.getZb();
            window.transform.pointInto(x, y, z, scratch);
            if (visible(chain, scratch[0], scratch[1], scratch[2])) {
                return true;
            }
        }
        return false;
    }

    private static boolean visible(List<Window> chain, double x, double y, double z) {
        for (int i = 0; i < chain.size(); i++) {
            if (!chain.get(i).passes(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    public static final class NestedCone {
        private final ApertureDescriptor geometry;
        private final int depth;
        private final List<Window> ancestors;
        private final Window window;

        private NestedCone(ApertureDescriptor geometry, int depth, List<Window> ancestors, Window window) {
            this.geometry = geometry;
            this.depth = depth;
            this.ancestors = ancestors;
            this.window = window;
        }

        public ApertureDescriptor geometry() {
            return geometry;
        }

        public int depth() {
            return depth;
        }

        public OpticTransform transform() {
            return window.transform;
        }

        public List<ApertureDescriptor> reflections() {
            return window.reflections;
        }

        public double contentEyeX() {
            return window.eyeX;
        }

        public double contentEyeY() {
            return window.eyeY;
        }

        public double contentEyeZ() {
            return window.eyeZ;
        }

        public List<ApertureDescriptor> ancestors() {
            List<ApertureDescriptor> geometries = new ArrayList<ApertureDescriptor>(ancestors.size());
            for (Window ancestor : ancestors) {
                geometries.add(ancestor.geometry);
            }
            return geometries;
        }

        public boolean visible(double displayX, double displayY, double displayZ) {
            return ClientRecursionPlanner.visible(ancestors, displayX, displayY, displayZ);
        }
    }

    private static final class ApertureOverlap {
        private final double[] first = new double[36];
        private final double[] second = new double[36];
        private final double[] point = new double[3];
        private double[] input;
        private double[] output;
        private int vertices;

        private boolean reaches(List<Window> ancestors, Window child) {
            List<Box> childFaces = child.faces();
            for (Window ancestor : ancestors) {
                if (!reaches(ancestor, child, childFaces)) {
                    return false;
                }
            }
            return true;
        }

        private boolean reaches(Window ancestor, Window child, List<Box> childFaces) {
            double distance = ancestor.signed(ancestor.eyeX, ancestor.eyeY, ancestor.eyeZ, ancestor.normal);
            if (distance <= EPSILON) {
                return true;
            }
            OpticTransform mapping = ancestor.content.compose(child.transform);
            for (Box opening : ancestor.faces()) {
                for (Box face : childFaces) {
                    if (intersects(ancestor, child, mapping, opening, face, distance)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean intersects(Window ancestor, Window child, OpticTransform mapping, Box opening, Box face, double distance) {
            int childNormal = child.normal.axisIndex();
            int firstAxis = (childNormal + 1) % 3;
            int secondAxis = (childNormal + 2) % 3;
            for (int corner = 0; corner < 4; corner++) {
                point[childNormal] = child.geometry.planeCoordinate();
                point[firstAxis] = corner == 0 || corner == 3 ? face.min(firstAxis) : face.max(firstAxis) + 0.001D;
                point[secondAxis] = corner < 2 ? face.min(secondAxis) : face.max(secondAxis) + 0.001D;
                mapping.pointInto(point[0], point[1], point[2], point);
                first[corner * 3] = point[0] - ancestor.eyeX;
                first[corner * 3 + 1] = point[1] - ancestor.eyeY;
                first[corner * 3 + 2] = point[2] - ancestor.eyeZ;
            }
            input = first;
            output = second;
            vertices = 4;
            Face normal = ancestor.normal;
            if (!clip(-normal.x(), -normal.y(), -normal.z(), -distance)) {
                return false;
            }
            double padding = Math.min(ancestor.geometry.aperturePadding(), 1.0D - EPSILON);
            for (int axis = 0; axis < 3; axis++) {
                if (axis == normal.axisIndex()) {
                    continue;
                }
                double eye = axis == 0 ? ancestor.eyeX : axis == 1 ? ancestor.eyeY : ancestor.eyeZ;
                double low = Math.max(opening.min(axis) - padding, ancestor.area.min(axis) - ancestor.geometry.aperturePadding());
                double high = Math.min(opening.max(axis) + 0.001D + padding,
                    ancestor.area.max(axis) + ancestor.geometry.aperturePadding());
                double delta = low - eye;
                if (!clip(delta * normal.x() + (axis == 0 ? distance : 0.0D),
                    delta * normal.y() + (axis == 1 ? distance : 0.0D),
                    delta * normal.z() + (axis == 2 ? distance : 0.0D), 0.0D)) {
                    return false;
                }
                delta = high - eye;
                if (!clip(-delta * normal.x() - (axis == 0 ? distance : 0.0D),
                    -delta * normal.y() - (axis == 1 ? distance : 0.0D),
                    -delta * normal.z() - (axis == 2 ? distance : 0.0D), 0.0D)) {
                    return false;
                }
            }
            return true;
        }

        private boolean clip(double x, double y, double z, double constant) {
            int nextVertices = 0;
            int previous = (vertices - 1) * 3;
            double previousDistance = input[previous] * x + input[previous + 1] * y + input[previous + 2] * z + constant;
            for (int vertex = 0; vertex < vertices; vertex++) {
                int current = vertex * 3;
                double currentDistance = input[current] * x + input[current + 1] * y + input[current + 2] * z + constant;
                boolean previousInside = previousDistance >= -EPSILON;
                boolean currentInside = currentDistance >= -EPSILON;
                if (previousInside != currentInside) {
                    double fraction = (previousDistance + EPSILON) / (previousDistance - currentDistance);
                    int target = nextVertices++ * 3;
                    for (int axis = 0; axis < 3; axis++) {
                        output[target + axis] = input[previous + axis] + fraction * (input[current + axis] - input[previous + axis]);
                    }
                }
                if (currentInside) {
                    System.arraycopy(input, current, output, nextVertices++ * 3, 3);
                }
                previous = current;
                previousDistance = currentDistance;
            }
            double[] swap = input;
            input = output;
            output = swap;
            vertices = nextVertices;
            return vertices > 0;
        }
    }

    private static final class Window {
        private final ApertureDescriptor geometry;
        private final OpticTransform transform;
        private final OpticTransform content;
        private final List<ApertureDescriptor> reflections;
        private final Box area;
        private final double originX;
        private final double originY;
        private final double originZ;
        private final double eyeX;
        private final double eyeY;
        private final double eyeZ;
        private final boolean eyeFrontSide;
        private final Face normal;
        private final PlaneWindow plane;
        private final ApertureCells aperture;
        private final double[] scratch;
        private List<Box> faces;

        private Window(ApertureDescriptor geometry, OpticTransform transform, List<ApertureDescriptor> reflections, double displayEyeX,
                       double displayEyeY, double displayEyeZ) {
            this.geometry = geometry;
            this.transform = transform;
            this.content = transform.inverse();
            this.reflections = reflections;
            this.aperture = geometry.aperture();
            this.area = aperture.getArea();
            Frame frame = geometry.frame();
            Vec3d center = area.center();
            this.originX = frame.getNormal().x() != 0 ? geometry.planeCoordinate() : center.x();
            this.originY = frame.getNormal().y() != 0 ? geometry.planeCoordinate() : center.y();
            this.originZ = frame.getNormal().z() != 0 ? geometry.planeCoordinate() : center.z();
            this.scratch = new double[3];
            content.pointInto(displayEyeX, displayEyeY, displayEyeZ, scratch);
            this.eyeX = scratch[0];
            this.eyeY = scratch[1];
            this.eyeZ = scratch[2];
            Face facing = frame.getNormal();
            this.eyeFrontSide = signed(eyeX, eyeY, eyeZ, facing) >= 0.0D;
            Frame projectionFrame = frame.view(eyeFrontSide);
            this.normal = projectionFrame.getNormal();
            this.plane = PlaneWindow.create(aperture, area, projectionFrame, originX, originY, originZ,
                geometry.aperturePadding(), signed(eyeX, eyeY, eyeZ, normal));
        }

        private List<Box> faces() {
            if (faces == null) {
                faces = aperture.getCachedApertureFaces(normal);
            }
            return faces;
        }

        private boolean servesEye() {
            return eyeFrontSide == geometry.frontSide();
        }

        private boolean passes(double displayX, double displayY, double displayZ) {
            content.pointInto(displayX, displayY, displayZ, scratch);
            double pointDot = signed(scratch[0], scratch[1], scratch[2], normal);
            return plane.containsRayIntersection(eyeX, eyeY, eyeZ, scratch[0], scratch[1], scratch[2], pointDot);
        }

        private double signed(double x, double y, double z, Face direction) {
            return ((x - originX) * direction.x()) + ((y - originY) * direction.y()) + ((z - originZ) * direction.z());
        }
    }
}
