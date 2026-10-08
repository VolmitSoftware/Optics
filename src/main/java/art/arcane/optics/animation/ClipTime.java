package art.arcane.optics.animation;

final class ClipTime {
    private ClipTime() {
    }

    static double clamp(double time, double duration) {
        if (time > duration) {
            return duration;
        }
        return time > 0.0D ? time : 0.0D;
    }

    static double requireDuration(double duration, boolean allowEndless, String owner) {
        boolean valid = duration >= 0.0D && (Double.isFinite(duration) || allowEndless && duration == Double.POSITIVE_INFINITY);
        if (!valid) {
            throw new IllegalArgumentException(owner + " duration must be " + (allowEndless ? "non-negative" : "finite and non-negative") + ": " + duration);
        }
        return duration;
    }
}
