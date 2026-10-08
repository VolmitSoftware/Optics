package art.arcane.optics.aperture;

import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.math.Axis;
import art.arcane.optics.math.Box;

public record SizeRatio(double ratio, boolean exact) {
    public static final SizeRatio UNIT = new SizeRatio(1.0D, true);
    public static final double EXACT_TOLERANCE = 1.0E-6D;

    public SizeRatio {
        if (!Double.isFinite(ratio) || ratio <= 0.0D) {
            throw new IllegalArgumentException("Size ratio must be finite and positive: " + ratio);
        }
    }

    public static SizeRatio between(Frame from, int fromColumns, int fromRows, Frame to, int toColumns, int toRows) {
        if (fromColumns < 1 || fromRows < 1 || toColumns < 1 || toRows < 1) {
            throw new IllegalArgumentException("Aperture sizes must be positive: " + fromColumns + "x" + fromRows + " -> " + toColumns + "x" + toRows);
        }
        Frame fromCanonical = Frame.canonical(from.getNormal());
        Frame toCanonical = Frame.canonical(to.getNormal());
        AxisPermutation map = AxisPermutation.between(from, to);
        Axis toColumnAxis = toCanonical.getRight().getAxis();
        int columnsLandOn = map.axis(fromCanonical.getRight().getAxis()) == toColumnAxis ? toColumns : toRows;
        int rowsLandOn = map.axis(fromCanonical.getUp().getAxis()) == toColumnAxis ? toColumns : toRows;
        return of((double) columnsLandOn / fromColumns, (double) rowsLandOn / fromRows);
    }

    public static SizeRatio between(ApertureDescriptor from, ApertureDescriptor to) {
        return between(from.frame(), from.apertureWidth(), from.apertureHeight(), to.frame(), to.apertureWidth(), to.apertureHeight());
    }

    public static SizeRatio between(Frame fromFrame, Box fromArea, Frame toFrame, Box toArea) {
        AxisPermutation map = AxisPermutation.between(fromFrame, toFrame);
        Axis right = fromFrame.getRight().getAxis();
        Axis up = fromFrame.getUp().getAxis();
        return of(cells(toArea, map.axis(right)) / cells(fromArea, right), cells(toArea, map.axis(up)) / cells(fromArea, up));
    }

    public SizeRatio inverse() {
        return new SizeRatio(1.0D / ratio, exact);
    }

    public boolean isUnit() {
        return Math.abs(ratio - 1.0D) <= EXACT_TOLERANCE;
    }

    private static SizeRatio of(double columnRatio, double rowRatio) {
        return new SizeRatio(Math.min(columnRatio, rowRatio), Math.abs(columnRatio - rowRatio) <= EXACT_TOLERANCE);
    }

    private static double cells(Box area, Axis axis) {
        int index = axis.ordinal();
        return Math.floor(area.max(index)) - Math.floor(area.min(index)) + 1.0D;
    }
}
