package art.arcane.optics.demo.scene;

import java.util.Objects;

import art.arcane.optics.math.Rgba;
import art.arcane.optics.shape.Shape;
import art.arcane.optics.transform.Affine;

public record ScenePortal(String id, Affine pose, Shape aperture, Affine viewTransform, boolean visible,
                          Rgba tint, CrossingMode crossingMode) {
    public ScenePortal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(aperture, "aperture");
        Objects.requireNonNull(viewTransform, "viewTransform");
        Objects.requireNonNull(tint, "tint");
        Objects.requireNonNull(crossingMode, "crossingMode");
        if (!pose.isFinite() || !viewTransform.isFinite()) {
            throw new IllegalArgumentException("Portal transforms must be finite");
        }
        if (visible && Math.abs(pose.determinant()) < 1.0E-12D) {
            throw new IllegalArgumentException("A visible portal pose must be invertible");
        }
    }
}
