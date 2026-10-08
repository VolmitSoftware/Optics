package art.arcane.optics.animation;

import art.arcane.optics.math.Rgba;
import art.arcane.optics.math.Vec2d;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.transform.Affine;
import art.arcane.optics.transform.Quaternion;

public final class Interpolators {
    private Interpolators() {
    }

    public static Interpolator<Double> doubles() {
        return (from, to, t) -> from + (to - from) * t;
    }

    public static Interpolator<Vec3d> vectors() {
        return Vec3d::lerp;
    }

    public static Interpolator<Vec2d> points() {
        return Vec2d::lerp;
    }

    public static Interpolator<Quaternion> rotations() {
        return Quaternion::slerp;
    }

    public static Interpolator<Affine> affines() {
        return Affine::lerp;
    }

    public static Interpolator<Rgba> colors() {
        return Rgba::lerp;
    }

    public static Interpolator<Rgba> colorsHsv() {
        return Rgba::lerpHsv;
    }

    public static <T> Interpolator<T> step() {
        return (from, to, t) -> t >= 1.0D ? to : from;
    }
}
