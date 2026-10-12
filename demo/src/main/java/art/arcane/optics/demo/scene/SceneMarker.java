package art.arcane.optics.demo.scene;

import java.util.Objects;

import art.arcane.optics.math.Rgba;
import art.arcane.optics.math.Vec3d;

public record SceneMarker(String id, Vec3d position, Rgba color) {
    public SceneMarker {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(color, "color");
    }
}
