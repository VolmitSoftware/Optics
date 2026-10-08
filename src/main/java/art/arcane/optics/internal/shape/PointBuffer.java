package art.arcane.optics.internal.shape;

import java.util.Arrays;

public final class PointBuffer {
    private double[] values;
    private int size;

    public PointBuffer(int capacityPoints) {
        values = new double[Math.max(8, capacityPoints << 1)];
    }

    public void add(double u, double v) {
        if (size + 2 > values.length) {
            values = Arrays.copyOf(values, values.length << 1);
        }
        values[size++] = u;
        values[size++] = v;
    }

    public void addDistinct(double u, double v) {
        if (size >= 2 && values[size - 2] == u && values[size - 1] == v) {
            return;
        }
        add(u, v);
    }

    public void clear() {
        size = 0;
    }

    public double[] closedLoop() {
        int end = size;
        while (end >= 4 && values[end - 2] == values[0] && values[end - 1] == values[1]) {
            end -= 2;
        }
        return Arrays.copyOf(values, end);
    }
}
