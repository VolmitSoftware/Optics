package art.arcane.optics.portal;

import java.util.Objects;

import art.arcane.optics.animation.Clip;
import art.arcane.optics.animation.EasingFunction;
import art.arcane.optics.animation.Interpolators;
import art.arcane.optics.animation.Timeline;
import art.arcane.optics.animation.Tween;
import art.arcane.optics.math.Rgba;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.shape.ShapeDescriptor;
import art.arcane.optics.transform.Affine;

public final class PortalTracks {
    public static final Timeline.TrackKey<Affine> TRANSFORM = new Timeline.TrackKey<Affine>("transform", Affine.class);
    public static final Timeline.TrackKey<ShapeDescriptor> SHAPE = new Timeline.TrackKey<ShapeDescriptor>("shape", ShapeDescriptor.class);
    public static final Timeline.TrackKey<Rgba> TINT = new Timeline.TrackKey<Rgba>("tint", Rgba.class);
    public static final Timeline.TrackKey<Double> OPENING = new Timeline.TrackKey<Double>("opening", Double.class);
    public static final Timeline.TrackKey<Double> EDGE_SOFTNESS = new Timeline.TrackKey<Double>("edgeSoftness", Double.class);
    private static final double FULL_TURN_DEGREES = 360.0D;

    private PortalTracks() {
    }

    public static Clip<Double> opening(double seconds, EasingFunction easing) {
        return Tween.of(0.0D, 1.0D, seconds, easing);
    }

    public static Clip<Double> closing(double seconds, EasingFunction easing) {
        return Tween.of(1.0D, 0.0D, seconds, easing);
    }

    public static Clip<ShapeDescriptor> morph(ShapeDescriptor from, ShapeDescriptor to, double seconds, EasingFunction easing) {
        return Tween.of(from, to, seconds, easing, Interpolators.shapes());
    }

    public static Clip<Affine> spin(Vec3d axis, double degreesPerSecond) {
        Objects.requireNonNull(axis, "axis");
        double length = axis.length();
        if (!(length > 0.0D) || !Double.isFinite(length)) {
            throw new IllegalArgumentException("Spin axis must be finite and non-zero: " + axis);
        }
        if (!Double.isFinite(degreesPerSecond)) {
            throw new IllegalArgumentException("Spin rate must be finite: " + degreesPerSecond);
        }
        return new Spin(axis.multiply(1.0D / length), degreesPerSecond);
    }

    private record Spin(Vec3d axis, double degreesPerSecond) implements Clip<Affine> {
        @Override
        public double duration() {
            return Double.POSITIVE_INFINITY;
        }

        @Override
        public Affine sample(double time) {
            double elapsed = time > 0.0D ? time : 0.0D;
            double degrees = Math.IEEEremainder(degreesPerSecond * elapsed, FULL_TURN_DEGREES);
            return Affine.rotation(axis, Math.toRadians(degrees));
        }
    }
}
