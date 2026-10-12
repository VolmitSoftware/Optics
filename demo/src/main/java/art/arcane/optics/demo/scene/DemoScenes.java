package art.arcane.optics.demo.scene;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import art.arcane.optics.animation.Clip;
import art.arcane.optics.animation.Constant;
import art.arcane.optics.animation.Easing;
import art.arcane.optics.animation.Interpolators;
import art.arcane.optics.animation.Keyframe;
import art.arcane.optics.animation.Sequence;
import art.arcane.optics.animation.Timeline;
import art.arcane.optics.animation.Track;
import art.arcane.optics.animation.Tween;
import art.arcane.optics.math.Axis;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Rgba;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.portal.PortalCrossing;
import art.arcane.optics.portal.PortalDefinition;
import art.arcane.optics.portal.PortalTracks;
import art.arcane.optics.shape.FitMode;
import art.arcane.optics.shape.Shape;
import art.arcane.optics.shape.ShapeDescriptor;
import art.arcane.optics.shape.Shapes;
import art.arcane.optics.transform.Affine;

public final class DemoScenes {
    private static final CameraPose CAMERA = new CameraPose(new Vec3d(0.0D, 2.0D, 6.0D), new Vec3d(0.0D, 2.0D, 0.0D));
    private static final Vec3d Z_AXIS = new Vec3d(0.0D, 0.0D, 1.0D);
    private static final Shape APERTURE = Shapes.roundedRectangle(2.0D, 2.0D, 0.16D);
    private static final Rgba GOLD = Rgba.rgb(0xE7BA65);
    private static final Rgba GREEN = Rgba.rgb(0x64C98A);
    private static final Rgba RED = Rgba.rgb(0xDA675D);
    private static final List<DemoScene> SCENES = List.of(growVanish(), axisScale(), continuousSpin(), easedMotion(), easingComparison(),
        keyframeLoop(), booleanMasks(), shapeMorph(), affineShear(), linkedTransform(), mirrorTransform(), maskedCrossing());

    private DemoScenes() {
    }

    public static List<DemoScene> all() {
        return SCENES;
    }

    public static Optional<DemoScene> find(String id) {
        for (DemoScene scene : SCENES) {
            if (scene.id().equals(id)) {
                return Optional.of(scene);
            }
        }
        return Optional.empty();
    }

    private static DemoScene growVanish() {
        Clip<Double> opening = Sequence.of(List.of(Constant.of(0.15D, 1.5D), Tween.of(0.15D, 1.0D, 3.5D, Easing.SMOOTHER_STEP),
            Constant.of(1.0D, 2.0D), PortalTracks.closing(2.0D, Easing.SMOOTHER_STEP), Constant.of(0.0D, 2.0D)));
        Timeline timeline = Timeline.builder().track(PortalTracks.OPENING, opening).marker("fully-open", 5.0D).marker("vanished", 9.0D).build();
        return scene("grow-vanish", "Small, big, gone", "Opening track scales a render aperture, then removes it at zero opening.", 11.0D,
            seconds -> growthFrame(timeline, seconds));
    }

    private static SceneFrame growthFrame(Timeline timeline, double seconds) {
        double opening = timeline.sample(PortalTracks.OPENING, seconds);
        Affine pose = pose(0.0D, 2.0D, 1.7D * opening, 1.7D * opening);
        String label = seconds < 1.5D ? "Small aperture" : seconds < 5.0D ? "Growing" : seconds < 7.0D ? "Fully open"
            : seconds < 9.0D ? "Closing" : "Vanished";
        return frame(label, List.of(portal("opening", pose, Shapes.circle(1.0D), Affine.IDENTITY,
            Math.abs(pose.determinant()) >= 1.0E-12D, CrossingMode.RENDER_ONLY)));
    }

    private static DemoScene axisScale() {
        Track<Vec3d> scale = Track.of(Interpolators.vectors(), List.of(
            new Keyframe<Vec3d>(0.0D, new Vec3d(1.0D, 1.0D, 1.0D)),
            new Keyframe<Vec3d>(3.0D, new Vec3d(1.8D, 0.55D, 1.0D), Easing.SINE_IN_OUT),
            new Keyframe<Vec3d>(6.0D, new Vec3d(0.55D, 1.8D, 1.0D), Easing.SINE_IN_OUT),
            new Keyframe<Vec3d>(9.0D, new Vec3d(1.4D, 1.4D, 2.0D), Easing.SINE_IN_OUT),
            new Keyframe<Vec3d>(12.0D, new Vec3d(1.0D, 1.0D, 1.0D), Easing.SINE_IN_OUT)));
        return scene("axis-scale", "Scale each axis", "Independent X and Y aperture scaling; Z scaling changes destination depth.", 14.0D,
            seconds -> scaleFrame(scale, seconds));
    }

    private static SceneFrame scaleFrame(Clip<Vec3d> scale, double seconds) {
        Vec3d factors = scale.sample(seconds);
        Affine pose = Affine.translation(0.0D, 2.0D, 0.0D).compose(Affine.scale(factors.x(), factors.y(), 1.0D));
        Affine view = Affine.scale(1.0D, 1.0D, factors.z());
        return frame("Per-axis scale", List.of(portal("scaled", pose, APERTURE, view, true, CrossingMode.RENDER_ONLY)));
    }

    private static DemoScene continuousSpin() {
        Timeline timeline = Timeline.builder().track(PortalTracks.TRANSFORM, PortalTracks.spin(Z_AXIS, 90.0D)).build();
        Shape star = Shapes.star(5, 1.0D, 0.45D, 0.0D);
        return scene("continuous-spin", "Continuous spin", "An endless spin track performs four full turns without shortest-path quaternion interpolation.",
            16.0D, seconds -> spinFrame(timeline, star, seconds));
    }

    private static SceneFrame spinFrame(Timeline timeline, Shape star, double seconds) {
        Affine pose = pose(0.0D, 2.0D, 1.6D, 1.6D).compose(timeline.sample(PortalTracks.TRANSFORM, seconds));
        SceneMarker marker = new SceneMarker("rotation-tip", pose.point(new Vec3d(0.0D, 1.07D, 0.08D)), GOLD);
        return new SceneFrame(CAMERA, "90 degrees per second", List.of(portal("spinning", pose, star, Affine.IDENTITY, true,
            CrossingMode.RENDER_ONLY)), List.of(marker));
    }

    private static DemoScene easedMotion() {
        Affine left = pose(-2.2D, 2.0D, 0.85D, 0.85D).compose(Affine.rotation(Z_AXIS, Math.toRadians(-25.0D)));
        Affine right = pose(2.2D, 2.0D, 1.3D, 1.3D).compose(Affine.rotation(Z_AXIS, Math.toRadians(25.0D)));
        Clip<Affine> motion = Tween.of(left, right, 4.0D, Easing.cubicBezier(0.42D, 0.0D, 0.58D, 1.0D), Interpolators.affines())
            .looped(3, true);
        return scene("eased-motion", "Eased transform lerp", "Affine decomposition interpolates translation, rotation and scale on a ping-pong tween.",
            12.0D, seconds -> frame("Smooth transform lerp", List.of(portal("moving", motion.sample(seconds), APERTURE, Affine.IDENTITY,
                true, CrossingMode.RENDER_ONLY))));
    }

    private static DemoScene easingComparison() {
        Clip<Double> linear = Tween.of(0.7D, 1.55D, 3.0D, Easing.LINEAR).looped(4, true);
        Clip<Double> smooth = Tween.of(0.7D, 1.55D, 3.0D, Easing.SMOOTHER_STEP).looped(4, true);
        return scene("easing-comparison", "Linear and eased", "Matched scale endpoints show linear motion beside smoother-step motion.", 12.0D,
            seconds -> frame("Left: linear / right: smoother step", List.of(
                portal("linear", pose(-2.0D, 2.0D, 0.85D, linear.sample(seconds)), APERTURE, Affine.translation(2.0D, 0.0D, 0.0D), true,
                    CrossingMode.RENDER_ONLY),
                portal("eased", pose(2.0D, 2.0D, 0.85D, smooth.sample(seconds)), APERTURE, Affine.translation(-2.0D, 0.0D, 0.0D), true,
                    CrossingMode.RENDER_ONLY))));
    }

    private static DemoScene keyframeLoop() {
        Track<Affine> path = Track.of(Interpolators.affines(), List.of(
            new Keyframe<Affine>(0.0D, pose(-2.0D, 1.5D, 0.8D, 0.8D)),
            new Keyframe<Affine>(2.0D, pose(0.0D, 3.0D, 1.0D, 1.0D), Easing.SINE_IN_OUT),
            new Keyframe<Affine>(4.0D, pose(2.0D, 1.5D, 0.8D, 0.8D), Easing.SINE_IN_OUT),
            new Keyframe<Affine>(6.0D, pose(-2.0D, 1.5D, 0.8D, 0.8D), Easing.SINE_IN_OUT)));
        Clip<Affine> loop = path.looped(3, false);
        return scene("keyframe-loop", "Keyframes and loops", "Three repetitions of a closed eased keyframe path.", 18.0D,
            seconds -> frame("Three-keyframe route / cycle " + Math.min(3, 1 + (int) (seconds / 6.0D)),
                List.of(portal("keyframed", loop.sample(seconds), Shapes.ellipse(1.0D, 0.8D), Affine.IDENTITY, true, CrossingMode.RENDER_ONLY))));
    }

    private static DemoScene booleanMasks() {
        Shape left = Shapes.circle(0.7D).translated(-0.3D, 0.0D);
        Shape right = Shapes.circle(0.7D).translated(0.3D, 0.0D);
        List<Shape> masks = List.of(left.union(right), left.intersect(right), left.subtract(right), Shapes.ring(1.0D, 0.45D));
        List<String> labels = List.of("Union", "Intersection", "Difference", "Ring with a closed center");
        return scene("boolean-masks", "Boolean masks", "Union, intersection, difference and a ring clip the same virtual scene.", 16.0D,
            seconds -> booleanFrame(masks, labels, seconds));
    }

    private static SceneFrame booleanFrame(List<Shape> masks, List<String> labels, double seconds) {
        int index = Math.min(3, (int) (seconds / 4.0D));
        return frame(labels.get(index), List.of(portal("boolean", pose(0.0D, 2.0D, 1.7D, 1.7D), masks.get(index), Affine.IDENTITY,
            true, CrossingMode.RENDER_ONLY)));
    }

    private static DemoScene shapeMorph() {
        ShapeDescriptor sharp = ShapeDescriptor.of(Shapes.star(5, 1.0D, 0.25D, 0.0D), FitMode.CONTAIN);
        ShapeDescriptor soft = ShapeDescriptor.of(Shapes.star(5, 1.0D, 0.82D, 0.0D), FitMode.CONTAIN);
        Clip<ShapeDescriptor> morph = PortalTracks.morph(sharp, soft, 4.0D, Easing.SINE_IN_OUT).looped(3, true);
        Timeline timeline = Timeline.builder().track(PortalTracks.SHAPE, morph).build();
        return scene("shape-morph", "Continuous shape morph", "Compatible five-point stars interpolate their inner radii continuously.", 12.0D,
            seconds -> frame("Star inner radius morph", List.of(portal("morph", pose(0.0D, 2.0D, 1.7D, 1.7D),
                timeline.sample(PortalTracks.SHAPE, seconds).shape(), Affine.IDENTITY, true, CrossingMode.RENDER_ONLY))));
    }

    private static DemoScene affineShear() {
        Clip<Double> shear = Tween.of(-0.5D, 0.5D, 4.0D, Easing.SINE_IN_OUT).looped(3, true);
        return scene("affine-shear", "Affine shear", "Aperture and destination rays use sampled affine shear matrices.", 12.0D,
            seconds -> shearFrame(shear.sample(seconds)));
    }

    private static SceneFrame shearFrame(double amount) {
        Affine shear = Affine.shear(amount, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        return frame("Sheared aperture and view", List.of(portal("shear", pose(0.0D, 2.0D, 1.2D, 1.2D).compose(shear), APERTURE,
            shear.inverse(), true, CrossingMode.RENDER_ONLY)));
    }

    private static DemoScene linkedTransform() {
        PortalDefinition source = definition(1L, -4, 0, 0, ShapeDescriptor.FULL);
        PortalDefinition destination = definition(2L, 0, 0, -4, ShapeDescriptor.FULL);
        PortalDefinition linked = PortalDefinition.builder(source).linkTo(destination.id()).build();
        Affine toward = Affine.of(linked.toward(destination, true));
        return scene("linked-transform", "Linked endpoint transform", "A real PortalDefinition link maps source rays to a translated destination endpoint.",
            10.0D, seconds -> frame("Left: linked rays / right: direct rays", List.of(
                portal("linked-source", pose(-2.0D, 2.0D, 1.2D, 1.4D), APERTURE, toward, true, CrossingMode.RENDER_ONLY),
                portal("direct-control", pose(2.0D, 2.0D, 1.2D, 1.4D), APERTURE, Affine.IDENTITY, true, CrossingMode.RENDER_ONLY))));
    }

    private static DemoScene mirrorTransform() {
        Affine reflected = Affine.reflection(Axis.X).compose(Affine.translation(-2.0D, 0.0D, 0.0D));
        return scene("mirror-transform", "Lateral reflection", "A reflected virtual camera reverses the asymmetric destination geometry across X.", 10.0D,
            seconds -> frame("Left: direct / right: X reflection", List.of(
                portal("direct", pose(-2.0D, 2.0D, 1.2D, 1.4D), APERTURE, Affine.translation(2.0D, 0.0D, 0.0D), true,
                    CrossingMode.RENDER_ONLY),
                portal("reflected", pose(2.0D, 2.0D, 1.2D, 1.4D), APERTURE, reflected, true, CrossingMode.MIRROR_VIEW))));
    }

    private static DemoScene maskedCrossing() {
        ShapeDescriptor ring = ShapeDescriptor.of(Shapes.ring(1.0D, 0.4D), FitMode.CONTAIN);
        PortalDefinition source = definition(3L, -4, 0, 0, ring);
        PortalDefinition destination = definition(4L, 0, 0, -4, ring);
        CrossingRoute route = new CrossingRoute(source, destination);
        return new DemoScene("masked-crossing", "Exact masked crossing", "A marker crosses the open ring; a second pass through its hole is rejected.",
            12.0D, CAMERA, seconds -> crossingFrame(route, seconds), Optional.of(route));
    }

    private static SceneFrame crossingFrame(CrossingRoute route, double seconds) {
        boolean hole = seconds >= 6.0D;
        double local = hole ? seconds - 6.0D : seconds;
        double progress = Easing.SMOOTHER_STEP.apply(Math.max(0.0D, Math.min(1.0D, (local - 1.0D) / 3.0D)));
        double x = hole ? 0.0D : 0.65D;
        Affine source = route.source().affine();
        Vec3d start = source.point(new Vec3d(x, 0.0D, 1.0D));
        Vec3d end = source.point(new Vec3d(x, 0.0D, -1.0D));
        Vec3d position = start.lerp(end, progress);
        Vec3d velocity = end.subtract(start);
        PortalCrossing crossing = route.crossing(start, position, velocity, velocity);
        List<SceneMarker> markers = crossing == null ? List.of(new SceneMarker("probe", position, hole ? RED : GOLD))
            : List.of(new SceneMarker("probe", route.arrival(crossing), GREEN));
        String label = hole ? "Closed center: crossing rejected" : progress < 0.5D ? "Approaching the open ring" : "Open ring: crossing accepted";
        return new SceneFrame(CAMERA, label, List.of(
            portal("crossing-source", source, route.source().shape().shape(), Affine.IDENTITY, true, CrossingMode.BLOCK_ALIGNED),
            portal("crossing-destination", route.destination().affine(), route.destination().shape().shape(), Affine.IDENTITY, true,
                CrossingMode.BLOCK_ALIGNED)), markers);
    }

    private static PortalDefinition definition(long id, int x, int y, int z, ShapeDescriptor shape) {
        return PortalDefinition.builder().id(new UUID(0L, id)).facing(Face.S).origin(x, y, z).size(4, 4).shape(shape).build();
    }

    private static DemoScene scene(String id, String title, String description, double duration, SceneSampler sampler) {
        return new DemoScene(id, title, description, duration, CAMERA, sampler, Optional.empty());
    }

    private static SceneFrame frame(String label, List<ScenePortal> portals) {
        return new SceneFrame(CAMERA, label, portals, List.of());
    }

    private static Affine pose(double x, double y, double scaleX, double scaleY) {
        return Affine.translation(x, y, 0.0D).compose(Affine.scale(scaleX, scaleY, 1.0D));
    }

    private static ScenePortal portal(String id, Affine pose, Shape aperture, Affine view, boolean visible, CrossingMode mode) {
        return new ScenePortal(id, pose, aperture, view, visible, GOLD, mode);
    }
}
