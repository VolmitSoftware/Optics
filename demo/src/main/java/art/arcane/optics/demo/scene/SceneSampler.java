package art.arcane.optics.demo.scene;

@FunctionalInterface
public interface SceneSampler {
    SceneFrame sample(double seconds);
}
