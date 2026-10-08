package art.arcane.optics.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.OpticTransform;

final class EnvironmentStateScaleCodecTest {
    @Test
    void scaleRoundTripsThroughTheCodec() throws ViewStreamProtocolException {
        for (float scale : new float[] {1.0F / 3.0F, 1.0F, 3.0F, 16.0F}) {
            EnvironmentState state = ViewStreamFixtures.environment().withScale(scale);
            assertEquals(state, decode(encode(state)));
            assertEquals(scale, decode(encode(state)).scale(), 0.0F);
        }
    }

    @Test
    void scaleIsWrittenAsAFloatRightAfterTheTransform() throws ViewStreamProtocolException {
        EnvironmentState state = ViewStreamFixtures.environment().withScale(0.25F);
        byte[] bytes = encode(state);
        int transformAt = indexOf(bytes, state.transform().encode());
        assertTrue(transformAt > 0);
        int scaleAt = transformAt + OpticTransform.ENCODED_BYTES;
        assertEquals(0.25F, ByteBuffer.wrap(bytes, scaleAt, Float.BYTES).order(ByteOrder.LITTLE_ENDIAN).getFloat(), 0.0F);
    }

    @Test
    void invalidScalesAreRejected() throws ViewStreamProtocolException {
        EnvironmentState state = ViewStreamFixtures.environment();
        assertThrows(IllegalArgumentException.class, () -> state.withScale(0.0F));
        assertThrows(IllegalArgumentException.class, () -> state.withScale(-1.0F));
        assertThrows(IllegalArgumentException.class, () -> state.withScale(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> state.withScale(Float.POSITIVE_INFINITY));
        byte[] bytes = encode(state.withScale(2.0F));
        int scaleAt = indexOf(bytes, state.transform().encode()) + OpticTransform.ENCODED_BYTES;
        ByteBuffer.wrap(bytes, scaleAt, Float.BYTES).order(ByteOrder.LITTLE_ENDIAN).putFloat(0.0F);
        assertThrows(ViewStreamProtocolException.class, () -> decode(bytes));
        ByteBuffer.wrap(bytes, scaleAt, Float.BYTES).order(ByteOrder.LITTLE_ENDIAN).putFloat(Float.NaN);
        assertThrows(ViewStreamProtocolException.class, () -> decode(bytes));
    }

    @Test
    void withersKeepTheOtherComponents() {
        EnvironmentState state = ViewStreamFixtures.environment().withScale(3.0F);
        EnvironmentState moved = state.withTransform(OpticTransform.translation(1.0D, 2.0D, 3.0D));
        assertEquals(3.0F, moved.scale(), 0.0F);
        assertEquals(OpticTransform.translation(1.0D, 2.0D, 3.0D), moved.transform());
        assertEquals(state.transform(), state.withScale(1.0F).transform());
        assertEquals(state.world(), state.withScale(1.0F).world());
        assertEquals(1.0F, state.withScale(1.0F).scale(), 0.0F);
    }

    private static byte[] encode(EnvironmentState state) throws ViewStreamProtocolException {
        ViewStreamWriter writer = new ViewStreamWriter();
        EnvironmentStateCodec.write(writer, state);
        return writer.toByteArray();
    }

    private static EnvironmentState decode(byte[] bytes) throws ViewStreamProtocolException {
        ViewStreamReader reader = new ViewStreamReader(bytes);
        EnvironmentState state = EnvironmentStateCodec.read(reader);
        reader.expectEnd();
        return state;
    }

    private static int indexOf(byte[] bytes, byte[] needle) {
        for (int start = 0; start <= bytes.length - needle.length; start++) {
            if (Arrays.equals(bytes, start, start + needle.length, needle, 0, needle.length)) {
                return start;
            }
        }
        return -1;
    }
}
