package art.arcane.optics.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ProjectionPacketStateTest {
    @Test
    void sendsOnlyChangesAndRestoresLiveWorldState() {
        ProjectionPacketState<String> state = new ProjectionPacketState<>();
        List<Map<Integer, String>> sent = new ArrayList<>();
        state.apply(Map.of(1, "quartz", 2, "air"), key -> "deepslate", sent::add);
        state.apply(Map.of(1, "quartz", 2, "air"), key -> "deepslate", sent::add);
        assertEquals(1, sent.size());
        state.apply(Map.of(1, "gold"), key -> "changed-world-" + key, sent::add);
        assertEquals(Map.of(1, "gold", 2, "changed-world-2"), sent.getLast());
        state.apply(Map.of(), key -> "live-world-" + key, sent::add);
        assertEquals(Map.of(1, "live-world-1"), sent.getLast());
        assertEquals(0, state.size());
        assertEquals(3, state.batches());
        assertEquals(5, state.changes());
    }

    @Test
    void failedTransportDoesNotForgetAnUnrestoredProjection() {
        ProjectionPacketState<String> state = new ProjectionPacketState<>();
        List<Map<Integer, String>> sent = new ArrayList<>();
        state.apply(Map.of(4, "quartz"), key -> "air", sent::add);
        assertThrows(IllegalStateException.class, () -> state.apply(Map.of(), key -> "live-stone", delta -> {
            throw new IllegalStateException("packet transport failed");
        }));
        assertEquals(1, state.size());
        assertEquals(1, state.batches());
        state.apply(Map.of(), key -> "live-stone", sent::add);
        assertEquals(Map.of(4, "live-stone"), sent.getLast());
        assertEquals(0, state.size());
    }
}
