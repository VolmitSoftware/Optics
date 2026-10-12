package art.arcane.optics.view;

import art.arcane.optics.math.Vec3d;
import art.arcane.optics.shape.Shape;
import art.arcane.optics.transform.Affine;
import java.util.Objects;

public final class AffineProjection {
    private final Shape aperture;
    private final Affine sourceToAperture;
    private final Affine sourceToDestination;
    private final Vec3d camera;

    public AffineProjection(Options options) {
        Objects.requireNonNull(options, "options");
        aperture = Objects.requireNonNull(options.aperture(), "aperture");
        Affine apertureToSource = Objects.requireNonNull(options.apertureToSource(), "apertureToSource");
        sourceToDestination = Objects.requireNonNull(options.sourceToDestination(), "sourceToDestination");
        Vec3d sourceCamera = Objects.requireNonNull(options.camera(), "camera");
        if (!apertureToSource.isFinite() || !sourceToDestination.isFinite()
            || !Double.isFinite(sourceCamera.x() + sourceCamera.y() + sourceCamera.z())) {
            throw new IllegalArgumentException("Projection transforms and camera must be finite");
        }
        sourceToAperture = apertureToSource.inverse();
        camera = sourceToAperture.point(sourceCamera);
        if (Math.abs(camera.z()) < 1.0E-12D) {
            throw new IllegalArgumentException("Projection camera must be off the aperture plane");
        }
    }

    public boolean contains(double x, double y, double z) {
        double localZ = component(2, x, y, z);
        double deltaZ = localZ - camera.z();
        if (Math.abs(deltaZ) < 1.0E-12D) {
            return false;
        }
        double parameter = -camera.z() / deltaZ;
        if (!(parameter >= 0.0D && parameter <= 1.0D)) {
            return false;
        }
        double u = camera.x() + parameter * (component(0, x, y, z) - camera.x());
        double v = camera.y() + parameter * (component(1, x, y, z) - camera.y());
        return aperture.contains(u, v);
    }

    public void destinationInto(double x, double y, double z, double[] destination) {
        sourceToDestination.pointInto(x, y, z, destination);
    }

    private double component(int row, double x, double y, double z) {
        return sourceToAperture.element(row, 0) * x + sourceToAperture.element(row, 1) * y
            + sourceToAperture.element(row, 2) * z + sourceToAperture.element(row, 3);
    }

    public record Options(Shape aperture, Affine apertureToSource, Affine sourceToDestination, Vec3d camera) {}
}
