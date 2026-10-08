package art.arcane.optics.animation;

import java.util.Objects;

public record Marker(String name, double time) {
    public Marker {
        Objects.requireNonNull(name, "name");
        if (!Double.isFinite(time) || time < 0.0D) {
            throw new IllegalArgumentException("Marker time must be finite and non-negative: " + time);
        }
    }
}
