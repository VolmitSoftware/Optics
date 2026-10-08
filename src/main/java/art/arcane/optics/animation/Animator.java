package art.arcane.optics.animation;

import java.util.List;
import java.util.Objects;

public final class Animator {
    private final Timeline timeline;
    private final TimeSource source;
    private State state;
    private double time;
    private double speed;
    private int cycles;
    private boolean pingPong;
    private long completedPasses;
    private double heading;
    private double anchor;
    private boolean pendingStart;

    public Animator(Timeline timeline, TimeSource source) {
        this.timeline = Objects.requireNonNull(timeline, "timeline");
        this.source = Objects.requireNonNull(source, "source");
        this.state = State.STOPPED;
        this.speed = 1.0D;
        this.heading = 1.0D;
    }

    public Timeline timeline() {
        return timeline;
    }

    public State state() {
        return state;
    }

    public double time() {
        return time;
    }

    public double speed() {
        return speed;
    }

    public void play() {
        if (state == State.PLAYING) {
            return;
        }
        if (state == State.FINISHED) {
            completedPasses = 0L;
            heading = 1.0D;
            time = startTime();
            pendingStart = true;
        } else if (state == State.STOPPED) {
            if (time == 0.0D) {
                time = startTime();
            }
            pendingStart = true;
        }
        state = State.PLAYING;
        anchor = source.seconds();
    }

    public void pause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
        }
    }

    public void stop() {
        state = State.STOPPED;
        time = 0.0D;
        completedPasses = 0L;
        heading = 1.0D;
        pendingStart = false;
    }

    public void seek(double time) {
        if (Double.isNaN(time)) {
            throw new IllegalArgumentException("Seek time must not be NaN");
        }
        this.time = ClipTime.clamp(time, timeline.duration());
        pendingStart = true;
        if (state == State.FINISHED) {
            state = State.PAUSED;
        }
    }

    public void speed(double factor) {
        if (!Double.isFinite(factor)) {
            throw new IllegalArgumentException("Animator speed must be finite: " + factor);
        }
        speed = factor;
    }

    public void loop(int cycles, boolean pingPong) {
        this.cycles = cycles;
        this.pingPong = pingPong;
    }

    public int advance(List<Marker> firedOut) {
        if (state != State.PLAYING) {
            return 0;
        }
        double now = source.seconds();
        double delta = (now - anchor) * speed;
        anchor = now;
        if (delta == 0.0D || !Double.isFinite(delta)) {
            return 0;
        }
        return move(delta, firedOut);
    }

    public <T> T value(Timeline.TrackKey<T> key) {
        return timeline.sample(key, time);
    }

    public boolean finished() {
        return state == State.FINISHED;
    }

    private int move(double delta, List<Marker> out) {
        double duration = timeline.duration();
        if (!(duration > 0.0D)) {
            int fired = fire(0.0D, 0.0D, out);
            time = 0.0D;
            state = State.FINISHED;
            return fired;
        }
        int fired = 0;
        double remaining = Math.abs(delta);
        double sign = Math.signum(delta);
        while (true) {
            double direction = sign * heading;
            double target = time + direction * remaining;
            if (direction > 0.0D ? target < duration : target > 0.0D) {
                fired += fire(time, target, out);
                time = target;
                return fired;
            }
            double boundary = direction > 0.0D ? duration : 0.0D;
            fired += fire(time, boundary, out);
            remaining -= Math.abs(boundary - time);
            time = boundary;
            completedPasses++;
            if (Double.isInfinite(duration) || cycles >= 0 && completedPasses >= Math.max(1, cycles)) {
                state = State.FINISHED;
                return fired;
            }
            if (pingPong) {
                heading = -heading;
            } else {
                time = direction > 0.0D ? 0.0D : duration;
                pendingStart = true;
            }
            if (!(remaining > 0.0D)) {
                return fired;
            }
            remaining = skipWholePasses(remaining, duration);
        }
    }

    private double skipWholePasses(double remaining, double duration) {
        if (timeline.hasMarkers() || remaining <= duration) {
            return remaining;
        }
        double whole = Math.floor(remaining / duration);
        if (cycles > 0) {
            whole = Math.min(whole, cycles - completedPasses - 1L);
        }
        if (whole < 1.0D) {
            return remaining;
        }
        completedPasses += (long) whole;
        if (pingPong && whole % 2.0D == 1.0D) {
            heading = -heading;
            time = time == 0.0D ? duration : 0.0D;
        }
        return Math.max(0.0D, remaining - whole * duration);
    }

    private int fire(double from, double to, List<Marker> out) {
        boolean inclusive = pendingStart;
        pendingStart = false;
        if (to > from) {
            return timeline.markersBetween(inclusive ? Math.nextDown(from) : from, to, out);
        }
        if (to < from) {
            return timeline.markersDescending(inclusive ? Math.nextUp(from) : from, to, out);
        }
        return inclusive ? timeline.markersBetween(Math.nextDown(from), from, out) : 0;
    }

    private double startTime() {
        return speed < 0.0D && !timeline.endless() ? timeline.duration() : 0.0D;
    }

    public enum State {
        STOPPED,
        PLAYING,
        PAUSED,
        FINISHED
    }
}
