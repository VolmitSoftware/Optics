package art.arcane.optics.demo.scene;

import java.util.Objects;
import java.util.Optional;

public record DemoScene(String id, String title, String description, double durationSeconds, CameraPose camera,
                        SceneSampler sampler, Optional<CrossingRoute> crossingRoute) {
    public DemoScene {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(sampler, "sampler");
        Objects.requireNonNull(crossingRoute, "crossingRoute");
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0.0D) {
            throw new IllegalArgumentException("Scene duration must be finite and positive");
        }
    }

    public int durationTicks() {
        return (int) Math.ceil(durationSeconds * 20.0D);
    }

    public SceneFrame sample(double seconds) {
        if (!Double.isFinite(seconds)) {
            throw new IllegalArgumentException("Scene time must be finite");
        }
        return sampler.sample(Math.max(0.0D, Math.min(durationSeconds, seconds)));
    }
}
