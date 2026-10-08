package art.arcane.optics.shape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import art.arcane.optics.internal.shape.PointBuffer;

public final class ShapeMesh {
    private static final int MAX_SUBDIVISIONS = 16;

    private final float[] positions;
    private final float[] distances;
    private final int[] indices;
    private final List<Outline> outlines;
    private final Bounds2 bounds;

    private ShapeMesh(float[] positions, float[] distances, int[] indices, List<Outline> outlines, Bounds2 bounds) {
        this.positions = positions;
        this.distances = distances;
        this.indices = indices;
        this.outlines = outlines;
        this.bounds = bounds;
    }

    public static ShapeMesh of(PlaneShape shape, int subdivisions, long[] cellMask) {
        Objects.requireNonNull(shape, "shape");
        if (subdivisions < 1 || subdivisions > MAX_SUBDIVISIONS) {
            throw new IllegalArgumentException("Mesh subdivisions must be in 1.." + MAX_SUBDIVISIONS + ", got " + subdivisions);
        }
        return new Builder(shape, subdivisions, cellMask == null ? null : cellMask.clone()).build();
    }

    public int vertexCount() {
        return positions.length >> 1;
    }

    public int triangleCount() {
        return indices.length / 3;
    }

    public float[] positions() {
        return positions;
    }

    public float[] distances() {
        return distances;
    }

    public int[] indices() {
        return indices;
    }

    public List<Outline> outlines() {
        return outlines;
    }

    public Bounds2 bounds() {
        return bounds;
    }

    public boolean isEmpty() {
        return indices.length == 0;
    }

    private static final class Builder {
        private static final int ROOT_ITERATIONS = 30;
        private static final byte OPEN = 0;
        private static final byte MIXED = 1;
        private static final byte CLOSED = 2;

        private final PlaneShape shape;
        private final int subdivisions;
        private final long[] mask;
        private final int columns;
        private final int rows;
        private final int gridU;
        private final int gridV;
        private final double[] values;
        private final byte[] kinds;
        private final boolean[] inside;
        private final int[] gridIndex;
        private final int[] horizontalIndex;
        private final int[] verticalIndex;
        private double[] vertexPositions = new double[256];
        private float[] vertexDistances = new float[128];
        private int vertexCount;
        private int[] triangles = new int[384];
        private int indexCount;
        private int[] segmentFrom = new int[64];
        private int[] segmentTo = new int[64];
        private int segmentCount;

        private Builder(PlaneShape shape, int subdivisions, long[] mask) {
            this.shape = shape;
            this.subdivisions = subdivisions;
            this.mask = mask;
            columns = shape.columns();
            rows = shape.rows();
            gridU = columns * subdivisions + 1;
            gridV = rows * subdivisions + 1;
            values = new double[gridU * gridV];
            kinds = new byte[values.length];
            inside = new boolean[values.length];
            gridIndex = new int[values.length];
            horizontalIndex = new int[(gridU - 1) * gridV];
            verticalIndex = new int[gridU * (gridV - 1)];
            Arrays.fill(gridIndex, -1);
            Arrays.fill(horizontalIndex, -1);
            Arrays.fill(verticalIndex, -1);
        }

        private ShapeMesh build() {
            sample();
            for (int j = 0; j < gridV - 1; j++) {
                for (int i = 0; i < gridU - 1; i++) {
                    if (open(i / subdivisions, j / subdivisions)) {
                        cell(i, j);
                    }
                }
            }
            float[] positions = new float[vertexCount << 1];
            for (int index = 0; index < positions.length; index++) {
                positions[index] = (float) vertexPositions[index];
            }
            Bounds2 bounds = vertexCount == 0 ? Bounds2.EMPTY : Bounds2.of(Arrays.copyOf(vertexPositions, vertexCount << 1));
            return new ShapeMesh(positions, Arrays.copyOf(vertexDistances, vertexCount), Arrays.copyOf(triangles, indexCount),
                trace(), bounds);
        }

        private void sample() {
            for (int j = 0; j < gridV; j++) {
                for (int i = 0; i < gridU; i++) {
                    int vertex = j * gridU + i;
                    byte kind = touching(i, j);
                    kinds[vertex] = kind;
                    if (kind == CLOSED) {
                        values[vertex] = 1.0D;
                        continue;
                    }
                    double value = shape.signedDistance(i / (double) subdivisions, j / (double) subdivisions);
                    values[vertex] = value;
                    inside[vertex] = value <= 0.0D;
                }
            }
        }

        private byte touching(int i, int j) {
            int columnLow = i % subdivisions == 0 ? i / subdivisions - 1 : i / subdivisions;
            int columnHigh = i / subdivisions;
            int rowLow = j % subdivisions == 0 ? j / subdivisions - 1 : j / subdivisions;
            int rowHigh = j / subdivisions;
            int open = 0;
            int total = 0;
            for (int row = rowLow; row <= rowHigh; row++) {
                for (int column = columnLow; column <= columnHigh; column++) {
                    total++;
                    if (open(column, row)) {
                        open++;
                    }
                }
            }
            return open == total ? OPEN : (open == 0 ? CLOSED : MIXED);
        }

        private boolean open(int column, int row) {
            if (column < 0 || row < 0 || column >= columns || row >= rows) {
                return false;
            }
            if (mask == null) {
                return true;
            }
            int bit = row * columns + column;
            int word = bit >>> 6;
            return word < mask.length && (mask[word] & (1L << (bit & 63))) != 0L;
        }

        private void cell(int i, int j) {
            int c0 = j * gridU + i;
            int c1 = c0 + 1;
            int c2 = c1 + gridU;
            int c3 = c0 + gridU;
            boolean b0 = inside[c0];
            boolean b1 = inside[c1];
            boolean b2 = inside[c2];
            boolean b3 = inside[c3];
            if (!b0 && !b1 && !b2 && !b3) {
                return;
            }
            int[] corners = {c0, c1, c2, c3};
            boolean[] flags = {b0, b1, b2, b3};
            int[] edges = {horizontal(i, j), vertical(i + 1, j), horizontal(i, j + 1), vertical(i, j)};
            boolean saddle = b0 == b2 && b1 == b3 && b0 != b1;
            boolean connected = saddle && (values[c0] + values[c1] + values[c2] + values[c3]) * 0.25D <= 0.0D;
            int[] edgeVertices = new int[4];
            for (int k = 0; k < 4; k++) {
                edgeVertices[k] = flags[k] != flags[(k + 1) & 3]
                    ? edgeVertex(edges[k], (k & 1) == 0, corners[k], corners[(k + 1) & 3], flags[k])
                    : -1;
            }
            if (saddle && !connected) {
                int first = b0 ? 0 : 1;
                for (int corner = first; corner < 4; corner += 2) {
                    int previous = (corner + 3) & 3;
                    triangle(edgeVertices[previous], gridVertex(corners[corner]), edgeVertices[corner]);
                    segment(edgeVertices[corner], edgeVertices[previous]);
                }
            } else {
                int[] polygon = new int[8];
                int size = 0;
                for (int k = 0; k < 4; k++) {
                    if (flags[k]) {
                        polygon[size++] = gridVertex(corners[k]);
                    }
                    if (edgeVertices[k] >= 0) {
                        polygon[size++] = edgeVertices[k];
                    }
                }
                for (int k = 1; k + 1 < size; k++) {
                    triangle(polygon[0], polygon[k], polygon[k + 1]);
                }
                for (int k = 0; k < 4; k++) {
                    if (flags[k] && !flags[(k + 1) & 3]) {
                        int entry = saddle ? (k + 1) & 3 : entryAfter(flags, k);
                        segment(edgeVertices[k], edgeVertices[entry]);
                    }
                }
            }
            border(i, j, corners, flags, edgeVertices);
        }

        private void border(int i, int j, int[] corners, boolean[] flags, int[] edgeVertices) {
            int column = i / subdivisions;
            int row = j / subdivisions;
            for (int k = 0; k < 4; k++) {
                boolean closed = switch (k) {
                    case 0 -> j % subdivisions == 0 && !open(column, row - 1);
                    case 1 -> (i + 1) % subdivisions == 0 && !open(column + 1, row);
                    case 2 -> (j + 1) % subdivisions == 0 && !open(column, row + 1);
                    default -> i % subdivisions == 0 && !open(column - 1, row);
                };
                if (!closed) {
                    continue;
                }
                boolean from = flags[k];
                boolean to = flags[(k + 1) & 3];
                if (from && to) {
                    segment(gridVertex(corners[k]), gridVertex(corners[(k + 1) & 3]));
                } else if (from) {
                    segment(gridVertex(corners[k]), edgeVertices[k]);
                } else if (to) {
                    segment(edgeVertices[k], gridVertex(corners[(k + 1) & 3]));
                }
            }
        }

        private static int entryAfter(boolean[] flags, int exit) {
            for (int step = 1; step < 4; step++) {
                int k = (exit + step) & 3;
                if (!flags[k] && flags[(k + 1) & 3]) {
                    return k;
                }
            }
            return exit;
        }

        private int horizontal(int i, int j) {
            return j * (gridU - 1) + i;
        }

        private int vertical(int i, int j) {
            return j * gridU + i;
        }

        private int gridVertex(int vertex) {
            int existing = gridIndex[vertex];
            if (existing >= 0) {
                return existing;
            }
            int i = vertex % gridU;
            int j = vertex / gridU;
            float distance = kinds[vertex] == MIXED ? 0.0F : (float) Math.min(0.0D, values[vertex]);
            int created = addVertex(i / (double) subdivisions, j / (double) subdivisions, distance);
            gridIndex[vertex] = created;
            return created;
        }

        private int edgeVertex(int edge, boolean horizontal, int from, int to, boolean fromInside) {
            int[] table = horizontal ? horizontalIndex : verticalIndex;
            int existing = table[edge];
            if (existing >= 0) {
                return existing;
            }
            int insideVertex = fromInside ? from : to;
            int outsideVertex = fromInside ? to : from;
            double insideU = (insideVertex % gridU) / (double) subdivisions;
            double insideV = (insideVertex / gridU) / (double) subdivisions;
            double outsideU = (outsideVertex % gridU) / (double) subdivisions;
            double outsideV = (outsideVertex / gridU) / (double) subdivisions;
            for (int iteration = 0; iteration < ROOT_ITERATIONS; iteration++) {
                double midU = (insideU + outsideU) * 0.5D;
                double midV = (insideV + outsideV) * 0.5D;
                if (shape.signedDistance(midU, midV) <= 0.0D) {
                    insideU = midU;
                    insideV = midV;
                } else {
                    outsideU = midU;
                    outsideV = midV;
                }
            }
            int created = addVertex(insideU, insideV, 0.0F);
            table[edge] = created;
            return created;
        }

        private int addVertex(double u, double v, float distance) {
            if ((vertexCount << 1) + 2 > vertexPositions.length) {
                vertexPositions = Arrays.copyOf(vertexPositions, vertexPositions.length << 1);
            }
            if (vertexCount + 1 > vertexDistances.length) {
                vertexDistances = Arrays.copyOf(vertexDistances, vertexDistances.length << 1);
            }
            vertexPositions[vertexCount << 1] = (float) u;
            vertexPositions[(vertexCount << 1) + 1] = (float) v;
            vertexDistances[vertexCount] = distance;
            return vertexCount++;
        }

        private void triangle(int a, int b, int c) {
            double ax = vertexPositions[a << 1];
            double ay = vertexPositions[(a << 1) + 1];
            double area = (vertexPositions[b << 1] - ax) * (vertexPositions[(c << 1) + 1] - ay)
                - (vertexPositions[c << 1] - ax) * (vertexPositions[(b << 1) + 1] - ay);
            if (!(area > 0.0D)) {
                return;
            }
            if (indexCount + 3 > triangles.length) {
                triangles = Arrays.copyOf(triangles, triangles.length << 1);
            }
            triangles[indexCount++] = a;
            triangles[indexCount++] = b;
            triangles[indexCount++] = c;
        }

        private void segment(int from, int to) {
            if (segmentCount == segmentFrom.length) {
                segmentFrom = Arrays.copyOf(segmentFrom, segmentCount << 1);
                segmentTo = Arrays.copyOf(segmentTo, segmentCount << 1);
            }
            segmentFrom[segmentCount] = from;
            segmentTo[segmentCount] = to;
            segmentCount++;
        }

        private List<Outline> trace() {
            int[] first = new int[vertexCount];
            int[] next = new int[segmentCount];
            Arrays.fill(first, -1);
            for (int index = segmentCount - 1; index >= 0; index--) {
                next[index] = first[segmentFrom[index]];
                first[segmentFrom[index]] = index;
            }
            boolean[] used = new boolean[segmentCount];
            List<Outline> loops = new ArrayList<Outline>();
            PointBuffer buffer = new PointBuffer(64);
            for (int start = 0; start < segmentCount; start++) {
                if (used[start]) {
                    continue;
                }
                buffer.clear();
                int current = start;
                int origin = segmentFrom[start];
                while (current >= 0) {
                    used[current] = true;
                    int from = segmentFrom[current];
                    int to = segmentTo[current];
                    buffer.addDistinct(vertexPositions[from << 1], vertexPositions[(from << 1) + 1]);
                    if (to == origin) {
                        break;
                    }
                    current = leftmost(first, next, used, from, to);
                }
                double[] loop = buffer.closedLoop();
                if (loop.length >= 6) {
                    loops.add(Outline.of(loop));
                }
            }
            return List.copyOf(loops);
        }

        private int leftmost(int[] first, int[] next, boolean[] used, int from, int to) {
            double inU = vertexPositions[to << 1] - vertexPositions[from << 1];
            double inV = vertexPositions[(to << 1) + 1] - vertexPositions[(from << 1) + 1];
            int best = -1;
            double bestTurn = Double.NEGATIVE_INFINITY;
            for (int candidate = first[to]; candidate >= 0; candidate = next[candidate]) {
                if (used[candidate]) {
                    continue;
                }
                int end = segmentTo[candidate];
                double outU = vertexPositions[end << 1] - vertexPositions[to << 1];
                double outV = vertexPositions[(end << 1) + 1] - vertexPositions[(to << 1) + 1];
                double turn = StrictMath.atan2(inU * outV - inV * outU, inU * outU + inV * outV);
                if (best < 0 || turn > bestTurn) {
                    best = candidate;
                    bestTurn = turn;
                }
            }
            return best;
        }
    }
}
