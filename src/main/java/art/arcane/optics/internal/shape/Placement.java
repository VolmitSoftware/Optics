package art.arcane.optics.internal.shape;

import art.arcane.optics.shape.FitMode;
import art.arcane.optics.shape.PlaneTransform;

public final class Placement {
    private Placement() {
    }

    public static double scaleU(FitMode fit, int columns, int rows) {
        return switch (fit) {
            case STRETCH -> columns * 0.5D;
            case CONTAIN -> Math.min(columns, rows) * 0.5D;
            case COVER -> Math.max(columns, rows) * 0.5D;
        };
    }

    public static double scaleV(FitMode fit, int columns, int rows) {
        return switch (fit) {
            case STRETCH -> rows * 0.5D;
            case CONTAIN -> Math.min(columns, rows) * 0.5D;
            case COVER -> Math.max(columns, rows) * 0.5D;
        };
    }

    public static double unitU(double column, double row, int columns, int rows, double scaleU, double scaleV, PlaneTransform inverse) {
        double orientedU = (column - columns * 0.5D) / scaleU;
        double orientedV = (row - rows * 0.5D) / scaleV;
        return inverse.a() * orientedU + inverse.b() * orientedV + inverse.tu();
    }

    public static double unitV(double column, double row, int columns, int rows, double scaleU, double scaleV, PlaneTransform inverse) {
        double orientedU = (column - columns * 0.5D) / scaleU;
        double orientedV = (row - rows * 0.5D) / scaleV;
        return inverse.c() * orientedU + inverse.d() * orientedV + inverse.tv();
    }

    public static PlaneTransform placement(int columns, int rows, double scaleU, double scaleV, PlaneTransform orientation) {
        return new PlaneTransform(scaleU, 0.0D, 0.0D, scaleV, columns * 0.5D, rows * 0.5D).compose(orientation);
    }
}
