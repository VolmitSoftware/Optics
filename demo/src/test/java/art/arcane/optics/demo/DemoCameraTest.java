package art.arcane.optics.demo;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoCameraTest {
    @Test
    void equivalentRotationsAndNetworkPrecisionKeepTheCamera() {
        Location camera = new Location(null, 96.0D, 64.38D, 6.0D, 180.0F, -0.0F);
        Location acknowledgment = new Location(null, 96.0D, 64.38000001D, 6.0D, -180.0F, 0.0F);
        assertTrue(DemoSession.cameraMatches(camera, acknowledgment));
    }

    @Test
    void movementAndChangedLookRequireCorrection() {
        Location camera = new Location(null, 96.0D, 64.38D, 6.0D, 180.0F, 0.0F);
        assertFalse(DemoSession.cameraMatches(camera, camera.clone().add(0.001D, 0.0D, 0.0D)));
        Location turned = camera.clone();
        turned.setYaw(179.0F);
        assertFalse(DemoSession.cameraMatches(camera, turned));
        turned.setYaw(180.0F);
        turned.setPitch(0.001F);
        assertFalse(DemoSession.cameraMatches(camera, turned));
    }
}
