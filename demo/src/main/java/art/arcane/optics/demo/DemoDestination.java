package art.arcane.optics.demo;

import java.util.List;

public final class DemoDestination {
    private static final List<Cuboid> LANDMARKS = List.of(
        new Cuboid(-2.2D, 0.0D, -3.8D, -0.7D, 2.4D, -2.3D, Voxel.OCHRE_FROGLIGHT),
        new Cuboid(0.6D, 0.0D, -5.8D, 2.0D, 3.3D, -4.3D, Voxel.WARPED_PLANKS),
        new Cuboid(-3.8D, 0.0D, -8.0D, -3.0D, 4.6D, -7.2D, Voxel.QUARTZ_BLOCK),
        new Cuboid(3.0D, 0.0D, -8.0D, 3.8D, 4.6D, -7.2D, Voxel.QUARTZ_BLOCK),
        new Cuboid(-3.8D, 4.0D, -8.0D, 3.8D, 4.6D, -7.2D, Voxel.QUARTZ_BLOCK));

    private DemoDestination() {}

    public static Voxel sample(double x, double y, double z) {
        if (Math.abs(x) >= 18.0D || z < -25.0D || z >= 1.0D) {
            return Voxel.AIR;
        }
        if (y >= -1.0D && y < 0.0D && z >= -24.0D) {
            return (((int) Math.floor(x) + (int) Math.floor(z)) & 1) == 0
                ? Voxel.SMOOTH_QUARTZ : Voxel.GRAY_CONCRETE;
        }
        if (z < -24.0D && y >= 0.0D && y < 12.0D) {
            return Voxel.LIGHT_BLUE_CONCRETE;
        }
        for (Cuboid landmark : LANDMARKS) {
            if (landmark.contains(x, y, z)) {
                return landmark.material();
            }
        }
        return Voxel.AIR;
    }

    public enum Voxel {
        AIR("minecraft:air"),
        OCHRE_FROGLIGHT("minecraft:ochre_froglight"),
        WARPED_PLANKS("minecraft:warped_planks"),
        QUARTZ_BLOCK("minecraft:quartz_block"),
        SMOOTH_QUARTZ("minecraft:smooth_quartz"),
        GRAY_CONCRETE("minecraft:gray_concrete"),
        LIGHT_BLUE_CONCRETE("minecraft:light_blue_concrete");

        private final String state;

        Voxel(String state) {
            this.state = state;
        }

        public String state() {
            return state;
        }

        public boolean isAir() {
            return this == AIR;
        }
    }

    private record Cuboid(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Voxel material) {
        private boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
        }
    }
}
