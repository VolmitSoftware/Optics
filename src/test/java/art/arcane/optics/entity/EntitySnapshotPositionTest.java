package art.arcane.optics.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

import org.junit.jupiter.api.Test;

final class EntitySnapshotPositionTest {
    @Test
    void absolutePositionsRoundTripAtBothWorldBorders() throws IOException {
        for (double position : new double[] {-30_000_000.0D, -29_999_999.875D, -600_000.0D, 600_000.0D, 29_999_999.875D, 30_000_000.0D}) {
            EntitySnapshot original = visual(position, -position, position + 0.25D);
            EntitySnapshot decoded = roundTrip(original);
            assertEquals(original.x(), decoded.x());
            assertEquals(original.y(), decoded.y());
            assertEquals(original.z(), decoded.z());
        }
    }

    @Test
    void oneQuantumMovementProducesAndAppliesADeltaAtBothWorldBorders() throws IOException {
        for (double position : new double[] {-30_000_000.0D, 30_000_000.0D}) {
            EntitySnapshot previous = visual(position, 64.0D, -position);
            EntitySnapshot current = visual(position + EntitySnapshot.POSITION_QUANTUM, 64.0D, -position - EntitySnapshot.POSITION_QUANTUM);
            int mask = EntityDeltaCodec.computeMask(current, previous);
            assertTrue((mask & EntitySnapshot.FIELD_POSITION) != 0);
            EntitySnapshot delta = roundTrip(EntityDeltaCodec.buildDelta(current, previous, 1, mask));
            EntitySnapshot applied = EntityDeltaCodec.applyDelta(delta, roundTrip(previous));
            assertEquals(current.x(), applied.x());
            assertEquals(current.y(), applied.y());
            assertEquals(current.z(), applied.z());
        }
    }

    private static EntitySnapshot visual(double x, double y, double z) {
        return EntitySnapshot.full(new UUID(1L, 2L), "minecraft:pig", x, y, z, 0.9D, 0.0D, 0.0D, 1.0D,
            0.0F, 0.0F, 0.0D, 0.0D, 0.0D, true, "", "", "", null, null, EntitySnapshot.EMPTY, EntitySnapshot.EMPTY, 0);
    }

    private static EntitySnapshot roundTrip(EntitySnapshot visual) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        visual.write(new DataOutputStream(bytes));
        return EntitySnapshot.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }
}
