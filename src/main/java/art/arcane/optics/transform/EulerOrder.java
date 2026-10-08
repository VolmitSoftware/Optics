package art.arcane.optics.transform;

public enum EulerOrder {
    XYZ(0, 1, 2),
    XZY(0, 2, 1),
    YXZ(1, 0, 2),
    YZX(1, 2, 0),
    ZXY(2, 0, 1),
    ZYX(2, 1, 0);

    private final int first;
    private final int second;
    private final int third;

    EulerOrder(int first, int second, int third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    int first() {
        return first;
    }

    int second() {
        return second;
    }

    int third() {
        return third;
    }

    double parity() {
        return (second - first + 3) % 3 == 1 ? 1.0D : -1.0D;
    }
}
