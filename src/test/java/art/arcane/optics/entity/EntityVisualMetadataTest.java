package art.arcane.optics.entity;

import art.arcane.optics.crossing.GravityFrame;
import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.math.Face;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class EntityVisualMetadataTest {
    @Test
    void allProperGravityFramesRoundTripWithScaleAndOpaqueHostData() {
        int rotations = 0;
        for (int index = 0; index < 48; index++) {
            AxisPermutation orientation = AxisPermutation.ofIndex(index);
            if (orientation.reflects()) {
                continue;
            }
            rotations++;
            EntityVisualMetadata metadata = new EntityVisualMetadata(new GravityFrame(orientation), 2.75D, new byte[] {0, 1, -1, 42});
            byte[] encoded = metadata.encode();
            assertEquals(orientation.index(), Byte.toUnsignedInt(encoded[0]));
            assertEquals(2.75D, ByteBuffer.wrap(encoded, Byte.BYTES, Double.BYTES).getDouble(), 0.0D);
            assertEquals(metadata, EntityVisualMetadata.decode(encoded));
            assertEquals(metadata.hashCode(), EntityVisualMetadata.decode(encoded).hashCode());
            assertArrayEquals(metadata.data(), Arrays.copyOfRange(encoded, EntityVisualMetadata.HEADER_BYTES, encoded.length));
        }
        assertEquals(24, rotations);
    }

    @Test
    void payloadOwnershipAndReplacementPreserveTheHeader() {
        byte[] payload = {3, 4};
        EntityVisualMetadata metadata = new EntityVisualMetadata(GravityFrame.WORLD, 0.5D, payload);
        payload[0] = 99;
        byte[] exposed = metadata.data();
        exposed[1] = 99;
        assertArrayEquals(new byte[] {3, 4}, metadata.data());
        EntityVisualMetadata replaced = metadata.withData(new byte[0]);
        assertEquals(GravityFrame.WORLD, replaced.gravity());
        assertEquals(0.5D, replaced.effectiveScale(), 0.0D);
        assertEquals(EntityVisualMetadata.HEADER_BYTES, replaced.encode().length);
        assertEquals(replaced, EntityVisualMetadata.decode(replaced.encode()));
    }

    @Test
    void invalidHeadersAndScalesAreRejected() {
        for (int length = 0; length < EntityVisualMetadata.HEADER_BYTES; length++) {
            byte[] truncated = new byte[length];
            assertThrows(IllegalArgumentException.class, () -> EntityVisualMetadata.decode(truncated));
        }
        int reflected = AxisPermutation.of(Face.E, Face.U, Face.N).index();
        for (int index : new int[] {reflected, 48, 255}) {
            byte[] encoded = new EntityVisualMetadata(GravityFrame.WORLD, 1.0D, new byte[0]).encode();
            encoded[0] = (byte) index;
            assertThrows(IllegalArgumentException.class, () -> EntityVisualMetadata.decode(encoded));
        }
        for (double scale : new double[] {0.0D, -1.0D, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new EntityVisualMetadata(GravityFrame.WORLD, scale, new byte[0]));
            byte[] encoded = ByteBuffer.allocate(EntityVisualMetadata.HEADER_BYTES)
                .put((byte) GravityFrame.WORLD.orientation().index()).putDouble(scale).array();
            assertThrows(IllegalArgumentException.class, () -> EntityVisualMetadata.decode(encoded));
        }
        assertThrows(NullPointerException.class, () -> EntityVisualMetadata.decode(null));
        assertThrows(NullPointerException.class, () -> new EntityVisualMetadata(null, 1.0D, new byte[0]));
        assertThrows(NullPointerException.class, () -> new EntityVisualMetadata(GravityFrame.WORLD, 1.0D, null));
    }
}
