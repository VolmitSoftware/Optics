package art.arcane.optics.animation;

import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

public interface TimeSource {
    double seconds();

    static TimeSource ticks(LongSupplier ticks, double secondsPerTick) {
        Objects.requireNonNull(ticks, "ticks");
        if (!Double.isFinite(secondsPerTick) || !(secondsPerTick > 0.0D)) {
            throw new IllegalArgumentException("Seconds per tick must be finite and positive: " + secondsPerTick);
        }
        return () -> ticks.getAsLong() * secondsPerTick;
    }

    static TimeSource nanos(LongSupplier nanoTime) {
        Objects.requireNonNull(nanoTime, "nanoTime");
        return () -> nanoTime.getAsLong() / 1.0E9D;
    }

    static TimeSource of(DoubleSupplier seconds) {
        Objects.requireNonNull(seconds, "seconds");
        return seconds::getAsDouble;
    }
}
