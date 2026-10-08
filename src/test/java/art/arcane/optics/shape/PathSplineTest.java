package art.arcane.optics.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

final class PathSplineTest {
    @Test
    void builderClosesOpenSubpaths() {
        Path path = Path.builder().moveTo(-1.0D, -1.0D).lineTo(1.0D, -1.0D).lineTo(0.0D, 1.0D).build();
        assertInstanceOf(Path.Close.class, path.segments().getLast());
        assertTrue(path.contains(0.0D, 0.0D));
        assertFalse(path.contains(0.9D, 0.9D));
        Path two = Path.builder().moveTo(-1.0D, -1.0D).lineTo(0.0D, -1.0D).lineTo(0.0D, 0.0D)
            .moveTo(0.5D, 0.5D).lineTo(1.0D, 0.5D).lineTo(1.0D, 1.0D).build();
        assertEquals(8, two.segments().size());
        assertInstanceOf(Path.Close.class, two.segments().get(3));
        assertThrows(IllegalStateException.class, () -> Path.builder().lineTo(1.0D, 1.0D));
    }

    @Test
    void unclosedOrOversizedPathsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Path(List.of(new Path.Move(0.0D, 0.0D), new Path.Line(1.0D, 0.0D))));
        assertThrows(IllegalArgumentException.class, () -> new Path(List.of(new Path.Line(1.0D, 0.0D), new Path.Close())));
        assertThrows(IllegalArgumentException.class, () -> new Path(List.of(new Path.Move(0.0D, 0.0D), new Path.Close())));
        List<Path.Segment> segments = new ArrayList<Path.Segment>();
        segments.add(new Path.Move(0.0D, 0.0D));
        for (int index = 0; index < 511; index++) {
            segments.add(new Path.Line(index * 0.001D, 1.0D));
        }
        segments.add(new Path.Close());
        assertThrows(IllegalArgumentException.class, () -> new Path(segments));
    }

    @Test
    void nestedSubpathsFillEvenOdd() {
        Path frame = Path.builder().moveTo(-1.0D, -1.0D).lineTo(1.0D, -1.0D).lineTo(1.0D, 1.0D).lineTo(-1.0D, 1.0D).close()
            .moveTo(-0.5D, -0.5D).lineTo(0.5D, -0.5D).lineTo(0.5D, 0.5D).lineTo(-0.5D, 0.5D).close().build();
        assertTrue(frame.contains(0.75D, 0.0D));
        assertFalse(frame.contains(0.0D, 0.0D));
        assertTrue(frame.signedDistance(0.0D, 0.0D) > 0.0D);
        assertEquals(0.5D, frame.signedDistance(0.0D, 0.0D), 1.0E-12D);
        List<Outline> outlines = frame.outlines(0.01D);
        assertEquals(2, outlines.size());
        assertFalse(outlines.get(0).clockwise());
        assertTrue(outlines.get(1).clockwise());
    }

    @Test
    void curvesFlattenWithinTheTolerance() {
        Path bulge = Path.builder().moveTo(-1.0D, 0.0D).quadTo(0.0D, 2.0D, 1.0D, 0.0D).close().build();
        assertTrue(bulge.contains(0.0D, 0.9D));
        assertFalse(bulge.contains(0.0D, 1.1D));
        double tolerance = 0.002D;
        Outline outline = bulge.outlines(tolerance).getFirst();
        for (int index = 0; index < outline.size(); index++) {
            int next = (index + 1) % outline.size();
            double midU = (outline.u(index) + outline.u(next)) * 0.5D;
            double midV = (outline.v(index) + outline.v(next)) * 0.5D;
            assertTrue(curveDistance(midU, midV) <= tolerance * 1.01D || Math.abs(midV) < 1.0E-12D, "segment " + index);
        }
        Path wave = Path.builder().moveTo(-1.0D, -0.2D).cubicTo(-0.3D, 1.2D, 0.3D, -1.2D, 1.0D, 0.2D).lineTo(1.0D, -1.0D)
            .lineTo(-1.0D, -1.0D).close().build();
        assertTrue(wave.contains(-0.5D, -0.5D));
        assertFalse(wave.contains(0.5D, 0.6D));
        assertEquals(0.0D, wave.signedDistance(-1.0D, -0.6D), 1.0E-9D);
    }

    @Test
    void splinesPassThroughTheirControlPoints() {
        double[] control = {0.0D, 0.9D, -0.8D, 0.1D, -0.4D, -0.8D, 0.5D, -0.7D, 0.8D, 0.2D};
        Spline spline = new Spline(control, 6);
        Outline outline = spline.outlines(0.01D).getFirst();
        assertEquals(30, outline.size());
        for (int index = 0; index < control.length; index += 2) {
            assertEquals(0.0D, outline.distance(control[index], control[index + 1]), 1.0E-12D);
        }
        assertTrue(spline.contains(0.0D, 0.0D));
        assertFalse(spline.contains(0.0D, 1.2D));
        assertEquals(spline, new Spline(control.clone(), 6));
        assertFalse(spline.equals(new Spline(control, 7)));
    }

    private static double curveDistance(double u, double v) {
        double best = Double.POSITIVE_INFINITY;
        for (int step = 0; step <= 20000; step++) {
            double t = step / 20000.0D;
            double cu = (1.0D - t) * (1.0D - t) * -1.0D + t * t;
            double cv = 2.0D * (1.0D - t) * t * 2.0D;
            best = Math.min(best, Math.hypot(cu - u, cv - v));
        }
        return best;
    }
}
