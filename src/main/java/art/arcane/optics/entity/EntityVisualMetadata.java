package art.arcane.optics.entity;

import art.arcane.optics.crossing.GravityFrame;
import art.arcane.optics.frame.AxisPermutation;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

public record EntityVisualMetadata(GravityFrame gravity, double effectiveScale, byte[] data) {
    public static final int HEADER_BYTES = Byte.BYTES + Double.BYTES;

    public EntityVisualMetadata {
        Objects.requireNonNull(gravity, "gravity");
        if (!Double.isFinite(effectiveScale) || effectiveScale <= 0.0D) {
            throw new IllegalArgumentException("Effective entity scale must be finite and positive: " + effectiveScale);
        }
        data = Objects.requireNonNull(data, "data").clone();
    }

    public static EntityVisualMetadata decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length < HEADER_BYTES) {
            throw new IllegalArgumentException("Entity visual metadata requires " + HEADER_BYTES + " header bytes");
        }
        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        GravityFrame gravity = new GravityFrame(AxisPermutation.ofIndex(Byte.toUnsignedInt(buffer.get())));
        double effectiveScale = buffer.getDouble();
        return new EntityVisualMetadata(gravity, effectiveScale, Arrays.copyOfRange(encoded, HEADER_BYTES, encoded.length));
    }

    public byte[] encode() {
        return ByteBuffer.allocate(Math.addExact(HEADER_BYTES, data.length)).put((byte) gravity.orientation().index())
            .putDouble(effectiveScale).put(data).array();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    public EntityVisualMetadata withData(byte[] data) {
        return new EntityVisualMetadata(gravity, effectiveScale, data);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EntityVisualMetadata metadata && gravity.equals(metadata.gravity)
            && Double.compare(effectiveScale, metadata.effectiveScale) == 0 && Arrays.equals(data, metadata.data);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * gravity.hashCode() + Double.hashCode(effectiveScale)) + Arrays.hashCode(data);
    }
}
