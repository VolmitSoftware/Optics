package art.arcane.optics.aperture;

import java.util.ArrayList;
import java.util.List;

import art.arcane.optics.claim.BlockClaim;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.math.Box;
import art.arcane.optics.math.Face;
import art.arcane.optics.shape.ShapeDescriptor;

final class ShapedApertures {
    static final int ORIGIN_X = 7;
    static final int ORIGIN_Y = 64;
    static final int ORIGIN_Z = -3;

    private ShapedApertures() {
    }

    static ApertureCells flat(Face normal, int columns, int rows) {
        Frame canonical = Frame.canonical(normal);
        int[] min = {ORIGIN_X, ORIGIN_Y, ORIGIN_Z};
        int[] max = {ORIGIN_X, ORIGIN_Y, ORIGIN_Z};
        max[canonical.getRight().axisIndex()] += columns - 1;
        max[canonical.getUp().axisIndex()] += rows - 1;
        ApertureCells aperture = new ApertureCells();
        aperture.setArea(new Box(min[0], max[0] + 0.999D, min[1], max[1] + 0.999D, min[2], max[2] + 0.999D));
        return aperture;
    }

    static ApertureDescriptor descriptor(Frame frame, int columns, int rows, ShapeDescriptor shape) {
        return ApertureDescriptor.fromPortal(source(flat(frame.getNormal(), columns, rows), frame, shape, 0.0D)).orElseThrow();
    }

    static ApertureDescriptor.Source source(CellAperture aperture, Frame frame, ShapeDescriptor shape, double planeOffset) {
        return new ApertureDescriptor.Source(aperture, frame, true, false, 0, 0.0D, 0.0D, 1.0D, 32, 0,
            ApertureDescriptor.BLACKOUT_OFF, 0, ApertureDescriptor.MASK_AIR_PROJECT, BlockClaim.LightingPolicy.LOCAL, 0, 0, planeOffset, 0,
            0L, shape, List.of());
    }

    static List<Frame> frames() {
        ArrayList<Frame> frames = new ArrayList<Frame>();
        for (Face normal : Face.values()) {
            Frame frame = Frame.canonical(normal);
            for (int turn = 0; turn < 4; turn++) {
                frames.add(frame);
                frame = frame.rotateClockwise();
            }
        }
        return List.copyOf(frames);
    }
}
