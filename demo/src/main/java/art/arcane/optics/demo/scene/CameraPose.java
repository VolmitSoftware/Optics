package art.arcane.optics.demo.scene;

import java.util.Objects;

import art.arcane.optics.math.Vec3d;

public record CameraPose(Vec3d eye, Vec3d target) {
    public CameraPose {
        Objects.requireNonNull(eye, "eye");
        Objects.requireNonNull(target, "target");
        if (eye.subtract(target).lengthSquared() == 0.0D) {
            throw new IllegalArgumentException("Camera target must differ from its eye");
        }
    }
}
