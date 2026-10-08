package art.arcane.optics.animation;

import java.util.Objects;

public final class Loop<T> implements Clip<T> {
    private final Clip<T> clip;
    private final int cycles;
    private final boolean pingPong;
    private final double period;
    private final double duration;

    private Loop(Clip<T> clip, int cycles, boolean pingPong) {
        this.clip = clip;
        this.cycles = cycles;
        this.pingPong = pingPong;
        this.period = clip.duration();
        this.duration = cycles <= 0 ? Double.POSITIVE_INFINITY : period * cycles;
    }

    public static <T> Loop<T> of(Clip<T> clip, int cycles, boolean pingPong) {
        Objects.requireNonNull(clip, "clip");
        double period = clip.duration();
        if (!Double.isFinite(period) || !(period > 0.0D)) {
            throw new IllegalArgumentException("A loop needs a clip with a finite positive duration: " + period);
        }
        return new Loop<T>(clip, cycles, pingPong);
    }

    public Clip<T> clip() {
        return clip;
    }

    public int cycles() {
        return cycles;
    }

    public boolean pingPong() {
        return pingPong;
    }

    @Override
    public double duration() {
        return duration;
    }

    @Override
    public T sample(double time) {
        double clamped = ClipTime.clamp(time, duration);
        double cycle = Math.floor(clamped / period);
        double local = clamped - cycle * period;
        if (cycles > 0 && cycle >= cycles) {
            cycle = cycles - 1;
            local = period;
        }
        if (local > period) {
            local = period;
        }
        if (pingPong && cycle % 2.0D == 1.0D) {
            local = period - local;
        }
        return clip.sample(local);
    }
}
