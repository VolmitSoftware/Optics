package art.arcane.optics.demo;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;

final class ProjectionPacketState<T> {
    private Map<Integer, T> current = Map.of();
    private long batches;
    private long changes;

    void apply(Map<Integer, T> next, IntFunction<T> restore, Consumer<Map<Integer, T>> send) {
        Map<Integer, T> delta = new HashMap<>();
        for (Map.Entry<Integer, T> entry : current.entrySet()) {
            if (!next.containsKey(entry.getKey())) {
                delta.put(entry.getKey(), restore.apply(entry.getKey()));
            }
        }
        for (Map.Entry<Integer, T> entry : next.entrySet()) {
            if (!entry.getValue().equals(current.get(entry.getKey()))) {
                delta.put(entry.getKey(), entry.getValue());
            }
        }
        if (!delta.isEmpty()) {
            send.accept(delta);
            batches++;
            changes += delta.size();
        }
        current = Map.copyOf(next);
    }

    int size() {
        return current.size();
    }

    long batches() {
        return batches;
    }

    long changes() {
        return changes;
    }
}
