package art.arcane.optics.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class Timeline {
    private final Map<TrackKey<?>, Clip<?>> tracks;
    private final Set<TrackKey<?>> keys;
    private final List<Marker> markers;
    private final double[] markerTimes;
    private final double duration;

    private Timeline(Map<TrackKey<?>, Clip<?>> tracks, List<Marker> markers) {
        this.tracks = tracks;
        this.keys = Collections.unmodifiableSet(tracks.keySet());
        this.markers = markers;
        this.markerTimes = new double[markers.size()];
        double longest = 0.0D;
        for (int index = 0; index < markerTimes.length; index++) {
            markerTimes[index] = markers.get(index).time();
            longest = Math.max(longest, markerTimes[index]);
        }
        for (Clip<?> clip : tracks.values()) {
            longest = Math.max(longest, clip.duration());
        }
        this.duration = longest;
    }

    public static Builder builder() {
        return new Builder();
    }

    public double duration() {
        return duration;
    }

    public boolean endless() {
        return duration == Double.POSITIVE_INFINITY;
    }

    public Set<TrackKey<?>> keys() {
        return keys;
    }

    public List<Marker> markers() {
        return markers;
    }

    @SuppressWarnings("unchecked")
    public <T> Clip<T> track(TrackKey<T> key) {
        return (Clip<T>) tracks.get(key);
    }

    public <T> T sample(TrackKey<T> key, double time) {
        Clip<T> clip = track(key);
        return clip == null ? null : clip.sample(time);
    }

    public int markersBetween(double fromExclusive, double toInclusive, List<Marker> out) {
        if (!(toInclusive > fromExclusive)) {
            return 0;
        }
        int count = 0;
        for (int index = firstAfter(fromExclusive); index < markerTimes.length && markerTimes[index] <= toInclusive; index++) {
            out.add(markers.get(index));
            count++;
        }
        return count;
    }

    int markersDescending(double fromExclusive, double toInclusive, List<Marker> out) {
        if (!(toInclusive < fromExclusive)) {
            return 0;
        }
        int count = 0;
        for (int index = firstAtOrAfter(fromExclusive) - 1; index >= 0 && markerTimes[index] >= toInclusive; index--) {
            out.add(markers.get(index));
            count++;
        }
        return count;
    }

    boolean hasMarkers() {
        return markerTimes.length > 0;
    }

    private int firstAfter(double time) {
        int low = 0;
        int high = markerTimes.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (markerTimes[middle] <= time) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private int firstAtOrAfter(double time) {
        int low = 0;
        int high = markerTimes.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (markerTimes[middle] < time) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    public record TrackKey<T>(String name, Class<T> type) {
        public TrackKey {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
        }
    }

    public static final class Builder {
        private final Map<TrackKey<?>, Clip<?>> tracks = new LinkedHashMap<TrackKey<?>, Clip<?>>();
        private final List<Marker> markers = new ArrayList<Marker>();

        private Builder() {
        }

        public <T> Builder track(TrackKey<T> key, Clip<T> clip) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(clip, "clip");
            if (tracks.containsKey(key)) {
                throw new IllegalArgumentException("Timeline already has a track for " + key.name());
            }
            tracks.put(key, clip);
            return this;
        }

        public Builder marker(String name, double time) {
            markers.add(new Marker(name, time));
            return this;
        }

        public Timeline build() {
            List<Marker> sorted = new ArrayList<Marker>(markers);
            sorted.sort(Comparator.comparingDouble(Marker::time));
            return new Timeline(new LinkedHashMap<TrackKey<?>, Clip<?>>(tracks), List.copyOf(sorted));
        }
    }
}
