package art.arcane.optics.internal.shape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MarchingSquares {
    private static final int ROOT_ITERATIONS = 40;
    private static final double BORDER_OUTSIDE = 1.0E-9D;

    private MarchingSquares() {
    }

    public static List<double[]> trace(Field field, double minU, double minV, double stepU, double stepV, int cellsU, int cellsV,
                                       boolean refine) {
        int verticesU = cellsU + 1;
        int verticesV = cellsV + 1;
        double[] values = new double[verticesU * verticesV];
        for (int j = 0; j < verticesV; j++) {
            double v = minV + j * stepV;
            for (int i = 0; i < verticesU; i++) {
                double value = field.value(minU + i * stepU, v);
                boolean border = i == 0 || j == 0 || i == cellsU || j == cellsV;
                values[j * verticesU + i] = border && !(value > BORDER_OUTSIDE) ? BORDER_OUTSIDE : value;
            }
        }
        int horizontal = cellsU * verticesV;
        int[] next = new int[horizontal + verticesU * cellsV];
        Arrays.fill(next, -1);
        for (int j = 0; j < cellsV; j++) {
            for (int i = 0; i < cellsU; i++) {
                link(values, next, verticesU, horizontal, i, j);
            }
        }
        List<double[]> loops = new ArrayList<double[]>();
        PointBuffer buffer = new PointBuffer(64);
        for (int start = 0; start < next.length; start++) {
            if (next[start] < 0) {
                continue;
            }
            buffer.clear();
            int edge = start;
            while (edge >= 0 && next[edge] >= 0) {
                int following = next[edge];
                next[edge] = -1;
                appendPoint(buffer, field, values, edge, verticesU, horizontal, minU, minV, stepU, stepV, refine);
                edge = following;
            }
            double[] loop = buffer.closedLoop();
            if (loop.length >= 6) {
                loops.add(loop);
            }
        }
        return loops;
    }

    private static void link(double[] values, int[] next, int verticesU, int horizontal, int i, int j) {
        double v0 = values[j * verticesU + i];
        double v1 = values[j * verticesU + i + 1];
        double v2 = values[(j + 1) * verticesU + i + 1];
        double v3 = values[(j + 1) * verticesU + i];
        boolean b0 = v0 < 0.0D;
        boolean b1 = v1 < 0.0D;
        boolean b2 = v2 < 0.0D;
        boolean b3 = v3 < 0.0D;
        int e0 = j * (verticesU - 1) + i;
        int e1 = horizontal + j * verticesU + i + 1;
        int e2 = (j + 1) * (verticesU - 1) + i;
        int e3 = horizontal + j * verticesU + i;
        int[] edges = {e0, e1, e2, e3};
        boolean[] inside = {b0, b1, b2, b3};
        boolean saddle = b0 == b2 && b1 == b3 && b0 != b1;
        boolean connected = saddle && (v0 + v1 + v2 + v3) * 0.25D < 0.0D;
        for (int k = 0; k < 4; k++) {
            if (!inside[k] || inside[(k + 1) & 3]) {
                continue;
            }
            int entry = saddle ? (connected ? (k + 1) & 3 : (k + 3) & 3) : entryAfter(inside, k);
            next[edges[k]] = edges[entry];
        }
    }

    private static int entryAfter(boolean[] inside, int exit) {
        for (int step = 1; step < 4; step++) {
            int k = (exit + step) & 3;
            if (!inside[k] && inside[(k + 1) & 3]) {
                return k;
            }
        }
        return exit;
    }

    private static void appendPoint(PointBuffer buffer, Field field, double[] values, int edge, int verticesU, int horizontal,
                                    double minU, double minV, double stepU, double stepV, boolean refine) {
        int i;
        int j;
        int ia;
        int ja;
        int ib;
        int jb;
        if (edge < horizontal) {
            j = edge / (verticesU - 1);
            i = edge - j * (verticesU - 1);
            ia = i;
            ja = j;
            ib = i + 1;
            jb = j;
        } else {
            int local = edge - horizontal;
            j = local / verticesU;
            i = local - j * verticesU;
            ia = i;
            ja = j;
            ib = i;
            jb = j + 1;
        }
        double va = values[ja * verticesU + ia];
        double vb = values[jb * verticesU + ib];
        double ua = minU + ia * stepU;
        double wa = minV + ja * stepV;
        double ub = minU + ib * stepU;
        double wb = minV + jb * stepV;
        if (!refine) {
            double t = va / (va - vb);
            buffer.addDistinct(ua + (ub - ua) * t, wa + (wb - wa) * t);
            return;
        }
        boolean aInside = va < 0.0D;
        double insideU = aInside ? ua : ub;
        double insideV = aInside ? wa : wb;
        double outsideU = aInside ? ub : ua;
        double outsideV = aInside ? wb : wa;
        for (int iteration = 0; iteration < ROOT_ITERATIONS; iteration++) {
            double midU = (insideU + outsideU) * 0.5D;
            double midV = (insideV + outsideV) * 0.5D;
            if (field.value(midU, midV) < 0.0D) {
                insideU = midU;
                insideV = midV;
            } else {
                outsideU = midU;
                outsideV = midV;
            }
        }
        buffer.addDistinct((insideU + outsideU) * 0.5D, (insideV + outsideV) * 0.5D);
    }

    public interface Field {
        double value(double u, double v);
    }
}
