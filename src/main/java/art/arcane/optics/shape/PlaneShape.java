package art.arcane.optics.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import art.arcane.optics.internal.shape.Placement;

public final class PlaneShape {
    private static final int MAX_EDGE = 0xFFFF;
    private static final double HALF_DIAGONAL = Math.sqrt(0.5D);
    private static final double CERTIFY_MARGIN = 1.5D;

    private final Shape shape;
    private final FitMode fit;
    private final int columns;
    private final int rows;
    private final PlaneTransform inverseOrientation;
    private final PlaneTransform placement;
    private final double scaleU;
    private final double scaleV;
    private final double meanScale;
    private final double minScale;
    private final double maxScale;
    private final boolean full;
    private final Bounds2 bounds;
    private final ShapeRaster[] rasters = new ShapeRaster[9];

    private PlaneShape(Shape shape, FitMode fit, int columns, int rows, PlaneTransform orientation) {
        this.shape = Objects.requireNonNull(shape, "shape");
        this.fit = Objects.requireNonNull(fit, "fit");
        Objects.requireNonNull(orientation, "orientation");
        if (columns < 1 || rows < 1 || columns > MAX_EDGE || rows > MAX_EDGE) {
            throw new IllegalArgumentException("A plane shape needs 1.." + MAX_EDGE + " columns and rows, got " + columns + "x" + rows);
        }
        this.columns = columns;
        this.rows = rows;
        inverseOrientation = orientation.inverse();
        scaleU = Placement.scaleU(fit, columns, rows);
        scaleV = Placement.scaleV(fit, columns, rows);
        placement = Placement.placement(columns, rows, scaleU, scaleV, orientation);
        meanScale = Math.sqrt(Math.abs(placement.determinant()));
        minScale = ShapeMath.minStretch(placement);
        maxScale = ShapeMath.maxStretch(placement);
        full = fit == FitMode.STRETCH && Shapes.FULL.equals(shape) && signedPermutation(orientation);
        Bounds2 rectangle = new Bounds2(0.0D, 0.0D, columns, rows);
        bounds = full ? rectangle : shape.bounds().transformed(placement).intersect(rectangle);
    }

    public static PlaneShape fit(Shape shape, FitMode fit, int columns, int rows) {
        return new PlaneShape(shape, fit, columns, rows, PlaneTransform.IDENTITY);
    }

    public static PlaneShape fit(Shape shape, FitMode fit, int columns, int rows, PlaneTransform frameOrientation) {
        return new PlaneShape(shape, fit, columns, rows, frameOrientation);
    }

    public static PlaneShape full(int columns, int rows) {
        return new PlaneShape(Shapes.FULL, FitMode.STRETCH, columns, rows, PlaneTransform.IDENTITY);
    }

    public Shape shape() {
        return shape;
    }

    public FitMode fit() {
        return fit;
    }

    public PlaneTransform placement() {
        return placement;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public boolean isFull() {
        return full;
    }

    public Bounds2 bounds() {
        return bounds;
    }

    public boolean contains(double column, double row) {
        if (!(column >= 0.0D && row >= 0.0D && column < columns && row < rows)) {
            return false;
        }
        if (full) {
            return true;
        }
        ShapeRaster raster = memoizedRaster();
        if (raster != null) {
            ShapeRaster.Coverage coverage = raster.coverage((int) column, (int) row);
            if (coverage == ShapeRaster.Coverage.FULL) {
                return true;
            }
            if (coverage == ShapeRaster.Coverage.EMPTY) {
                return false;
            }
        }
        return containsAnalytic(column, row);
    }

    public double signedDistance(double column, double row) {
        if (full) {
            return ShapeMath.box(Math.abs(column - columns * 0.5D) - columns * 0.5D, Math.abs(row - rows * 0.5D) - rows * 0.5D);
        }
        return shape.signedDistance(unitU(column, row), unitV(column, row)) * meanScale;
    }

    public void unitCoordinates(double column, double row, double[] out2) {
        out2[0] = unitU(column, row);
        out2[1] = unitV(column, row);
    }

    public List<Outline> outlines(double toleranceCells) {
        ShapeMath.tolerance(toleranceCells);
        if (full) {
            return List.of(Outline.of(0.0D, 0.0D, columns, 0.0D, columns, rows, 0.0D, rows));
        }
        List<Outline> source = shape.outlines(toleranceCells / maxScale);
        List<Outline> mapped = new ArrayList<Outline>(source.size());
        boolean reflects = placement.reflects();
        for (Outline outline : source) {
            Outline moved = outline.transformed(placement);
            mapped.add(reflects ? moved.reversed() : moved);
        }
        return List.copyOf(mapped);
    }

    public ShapeRaster raster(int subsamples) {
        if (subsamples < 1 || subsamples >= rasters.length) {
            throw new IllegalArgumentException("Raster subsamples must be in 1..8, got " + subsamples);
        }
        ShapeRaster raster = rasters[subsamples];
        if (raster == null) {
            raster = ShapeRaster.of(this, subsamples, ShapeRaster.DEFAULT_THRESHOLD);
            rasters[subsamples] = raster;
        }
        return raster;
    }

    public ShapeMesh mesh(int subdivisions, long[] cellMask) {
        return ShapeMesh.of(this, subdivisions, cellMask);
    }

    boolean containsAnalytic(double column, double row) {
        return full || shape.contains(unitU(column, row), unitV(column, row));
    }

    int certify(int column, int row) {
        if (full) {
            return -1;
        }
        double distance = shape.signedDistance(unitU(column + 0.5D, row + 0.5D), unitV(column + 0.5D, row + 0.5D)) * minScale;
        if (distance < -HALF_DIAGONAL * CERTIFY_MARGIN) {
            return -1;
        }
        return distance > HALF_DIAGONAL * CERTIFY_MARGIN ? 1 : 0;
    }

    private double unitU(double column, double row) {
        return Placement.unitU(column, row, columns, rows, scaleU, scaleV, inverseOrientation);
    }

    private double unitV(double column, double row) {
        return Placement.unitV(column, row, columns, rows, scaleU, scaleV, inverseOrientation);
    }

    private ShapeRaster memoizedRaster() {
        ShapeRaster preferred = rasters[ShapeRaster.DEFAULT_SUBSAMPLES];
        if (preferred != null) {
            return preferred;
        }
        for (int index = rasters.length - 1; index > 0; index--) {
            if (rasters[index] != null) {
                return rasters[index];
            }
        }
        return null;
    }

    private static boolean signedPermutation(PlaneTransform orientation) {
        double a = Math.abs(orientation.a());
        double b = Math.abs(orientation.b());
        double c = Math.abs(orientation.c());
        double d = Math.abs(orientation.d());
        boolean straight = a == 1.0D && d == 1.0D && b == 0.0D && c == 0.0D;
        boolean swapped = b == 1.0D && c == 1.0D && a == 0.0D && d == 0.0D;
        return (straight || swapped) && orientation.tu() == 0.0D && orientation.tv() == 0.0D;
    }
}
