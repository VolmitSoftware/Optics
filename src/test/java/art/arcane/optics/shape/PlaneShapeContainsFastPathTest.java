package art.arcane.optics.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

final class PlaneShapeContainsFastPathTest {
    @Test
    void rasterFastPathAgreesWithTheAnalyticTest() {
        Random random = new Random(0xFA57L);
        List<PlaneShape> planes = List.of(
            PlaneShape.fit(Ellipse.circle(1.0D), FitMode.CONTAIN, 9, 9),
            PlaneShape.fit(new Flower(5, 1.0D, 0.6D, 0.0D), FitMode.CONTAIN, 13, 7),
            PlaneShape.fit(new Star(6, 1.0D, 0.5D, 10.0D), FitMode.COVER, 7, 7),
            PlaneShape.fit(new Heart(1.0D, 0.0D), FitMode.STRETCH, 11, 5, PlaneTransform.flipU()),
            PlaneShape.fit(new Ring(1.0D, 0.5D), FitMode.CONTAIN, 9, 9, PlaneTransform.rotation(90.0D)),
            PlaneShape.fit(new Ellipse(1.0D, 0.3D).union(new Rectangle(0.2D, 1.8D)), FitMode.CONTAIN, 12, 12));
        double[] unit = new double[2];
        for (PlaneShape plane : planes) {
            plane.raster(ShapeRaster.DEFAULT_SUBSAMPLES);
            for (int sample = 0; sample < 10_000; sample++) {
                double column = random.nextDouble() * plane.columns();
                double row = random.nextDouble() * plane.rows();
                plane.unitCoordinates(column, row, unit);
                assertEquals(plane.shape().contains(unit[0], unit[1]), plane.contains(column, row),
                    plane.shape() + " at " + column + "," + row);
            }
        }
    }
}
