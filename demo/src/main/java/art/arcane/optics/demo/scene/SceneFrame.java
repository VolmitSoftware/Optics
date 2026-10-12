package art.arcane.optics.demo.scene;

import java.util.List;
import java.util.Objects;

public record SceneFrame(CameraPose camera, String label, List<ScenePortal> portals, List<SceneMarker> markers) {
    public SceneFrame {
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(label, "label");
        portals = List.copyOf(portals);
        markers = List.copyOf(markers);
    }
}
