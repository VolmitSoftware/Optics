package art.arcane.optics.aperture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.QuarterTurn;
import art.arcane.optics.math.Box;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

final class EndpointDirectoryTravelScaleTest {
    @Test
    void endpointsTravelRigidlyUnlessTheDirectorySaysOtherwise() {
        Gate gate = new Gate(new UUID(1L, 2L), Frame.canonical(Face.N), new Vec3d(0.5D, 64.5D, 0.5D));
        assertEquals(1.0D, new Directory(List.of(gate)).travelScale(gate), 0.0D);
    }

    private record Gate(UUID id, Frame frame, Vec3d origin) implements Endpoint {
    }

    private record Directory(List<Gate> endpoints) implements EndpointDirectory<String, Gate> {
        @Override
        public String world(Gate endpoint) {
            return "test";
        }

        @Override
        public CellAperture aperture(Gate endpoint) {
            return new ApertureCells();
        }

        @Override
        public Box view(Gate endpoint) {
            return new Box(0.0D, 1.0D, 0.0D, 1.0D, 0.0D, 1.0D);
        }

        @Override
        public boolean eligible(Gate endpoint) {
            return true;
        }

        @Override
        public boolean mirror(Gate endpoint) {
            return false;
        }

        @Override
        public QuarterTurn mirrorTurns(Gate endpoint) {
            return QuarterTurn.DEGREES_0;
        }

        @Override
        public Gate destination(Gate endpoint) {
            return endpoint;
        }
    }
}
