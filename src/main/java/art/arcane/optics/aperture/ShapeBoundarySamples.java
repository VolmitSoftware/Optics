package art.arcane.optics.aperture;

import java.util.List;
import java.util.Objects;

import art.arcane.optics.math.Vec3d;
import art.arcane.optics.shape.Outline;

public final class ShapeBoundarySamples {
    private static final int OUTLINE_SUBDIVISIONS = 4;

    private ShapeBoundarySamples() {
    }

    public static <R> int append(List<R> out, ApertureDescriptor geometry, double spacing, PointFactory<R> factory) {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(factory, "factory");
        if (!(spacing > 0.0D) || !Double.isFinite(spacing)) {
            throw new IllegalArgumentException("Boundary sample spacing must be positive and finite");
        }
        if (geometry.shape().isFull()) {
            return 0;
        }
        AperturePolygon polygon = AperturePolygon.from(geometry);
        int added = 0;
        for (Outline outline : polygon.mesh(OUTLINE_SUBDIVISIONS).outlines()) {
            double[] samples = new double[2 * (int) Math.ceil(outline.length() / spacing) + 2];
            int count = outline.sample(spacing, samples);
            for (int index = 0; index < count; index++) {
                Vec3d point = polygon.point(samples[index << 1], samples[(index << 1) + 1]);
                out.add(factory.create(point.x(), point.y(), point.z()));
                added++;
            }
        }
        return added;
    }
}
