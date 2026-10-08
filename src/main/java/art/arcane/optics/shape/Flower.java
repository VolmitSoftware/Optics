package art.arcane.optics.shape;

import java.util.List;

import art.arcane.optics.internal.shape.PolylineDistance;

public record Flower(int petals, double radius, double petalDepth, double rotationDegrees) implements Shape {
    private static final int MIN_SAMPLES = 64;
    private static final int SAMPLES_PER_PETAL = 16;

    public Flower {
        petals = ShapeMath.count(petals, 2, 32, "Flower petals");
        radius = ShapeMath.positive(radius, ShapeMath.MAX_RADIUS, "Flower radius");
        petalDepth = ShapeMath.closed(petalDepth, 0.0D, 1.0D, "Flower petal depth");
        rotationDegrees = ShapeMath.finite(rotationDegrees, "Flower rotation");
    }

    @Override
    public Bounds2 bounds() {
        return new Bounds2(-radius, -radius, radius, radius);
    }

    @Override
    public boolean contains(double u, double v) {
        double cos = ShapeMath.cos(rotationDegrees);
        double sin = ShapeMath.sin(rotationDegrees);
        return containsLocal(cos * u + sin * v, -sin * u + cos * v);
    }

    @Override
    public double signedDistance(double u, double v) {
        double cos = ShapeMath.cos(rotationDegrees);
        double sin = ShapeMath.sin(rotationDegrees);
        double localU = cos * u + sin * v;
        double localV = -sin * u + cos * v;
        int samples = samples();
        double best = Double.POSITIVE_INFINITY;
        if (samples % (petals << 1) == 0) {
            double sector = 2.0D * Math.PI / petals;
            double angle = StrictMath.atan2(localV, localU) - Math.PI * 0.5D;
            angle = Math.abs(angle - sector * Math.rint(angle / sector));
            double rho = Math.sqrt(localU * localU + localV * localV);
            double foldedU = -rho * StrictMath.sin(angle);
            double foldedV = rho * StrictMath.cos(angle);
            int half = samples / (petals << 1);
            for (int index = 0; index < half; index++) {
                best = Math.min(best, segment(foldedU, foldedV, index, samples));
            }
        } else {
            for (int index = 0; index < samples; index++) {
                best = Math.min(best, segment(localU, localV, index, samples));
            }
        }
        double distance = Math.sqrt(best);
        return containsLocal(localU, localV) ? -distance : distance;
    }

    @Override
    public List<Outline> outlines(double tolerance) {
        ShapeMath.tolerance(tolerance);
        int samples = samples();
        double cos = ShapeMath.cos(rotationDegrees);
        double sin = ShapeMath.sin(rotationDegrees);
        double[] points = new double[samples << 1];
        for (int index = 0; index < samples; index++) {
            double localU = vertexU(index, samples);
            double localV = vertexV(index, samples);
            points[index << 1] = cos * localU - sin * localV;
            points[(index << 1) + 1] = sin * localU + cos * localV;
        }
        return List.of(Outline.of(points));
    }

    private int samples() {
        return Math.max(MIN_SAMPLES, SAMPLES_PER_PETAL * petals);
    }

    private boolean containsLocal(double localU, double localV) {
        double rho = Math.sqrt(localU * localU + localV * localV);
        return rho <= radiusAt(StrictMath.atan2(localV, localU) - Math.PI * 0.5D);
    }

    private double radiusAt(double theta) {
        return radius * (1.0D - petalDepth * (1.0D - StrictMath.cos(petals * theta)) * 0.5D);
    }

    private double segment(double u, double v, int index, int samples) {
        return PolylineDistance.segmentDistanceSquared(u, v, vertexU(index, samples), vertexV(index, samples),
            vertexU(index + 1, samples), vertexV(index + 1, samples));
    }

    private double vertexU(int index, int samples) {
        double theta = 2.0D * Math.PI * (index % samples) / samples;
        return -radiusAt(theta) * StrictMath.sin(theta);
    }

    private double vertexV(int index, int samples) {
        double theta = 2.0D * Math.PI * (index % samples) / samples;
        return radiusAt(theta) * StrictMath.cos(theta);
    }
}
