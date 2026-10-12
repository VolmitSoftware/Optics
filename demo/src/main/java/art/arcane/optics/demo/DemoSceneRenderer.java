package art.arcane.optics.demo;

import art.arcane.optics.demo.scene.SceneFrame;
import art.arcane.optics.demo.scene.SceneMarker;
import art.arcane.optics.demo.scene.ScenePortal;
import art.arcane.optics.math.Rgba;
import art.arcane.optics.view.AffineProjection;
import io.papermc.paper.math.Position;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

public final class DemoSceneRenderer {
    private static final int MIN_X = -18;
    private static final int MIN_Y = -1;
    private static final int MIN_Z = -25;
    private static final int SIZE_X = 37;
    private static final int SIZE_Y = 14;
    private static final int SIZE_Z = 27;

    private final OpticsDemoPlugin plugin;
    private final Player player;
    private final Location origin;
    private final ProjectionPacketState<BlockData> packets = new ProjectionPacketState<>();
    private final EnumMap<Material, BlockData> blockData = new EnumMap<>(Material.class);
    private final double[] destination = new double[3];
    private TextDisplay caption;
    private String label = "";
    private int visible;

    public DemoSceneRenderer(OpticsDemoPlugin plugin, RenderContext context) {
        this.plugin = plugin;
        player = context.player();
        origin = context.origin().clone();
    }

    public void render(SceneFrame frame) {
        render(frame, false);
    }

    public void render(SceneFrame frame, boolean nativeProjection) {
        requireServerThread();
        if (frame.portals().size() > 2 || frame.markers().size() > 16) {
            throw new IllegalArgumentException("Demo frames support at most two portals and sixteen markers");
        }
        Map<Integer, BlockData> next = new HashMap<>();
        visible = 0;
        if (!nativeProjection) {
            sampleProjection(frame, next);
        }
        for (SceneMarker marker : frame.markers()) {
            int x = (int) Math.floor(marker.position().x());
            int y = (int) Math.floor(marker.position().y());
            int z = (int) Math.floor(marker.position().z());
            if (inBounds(x, y, z)) {
                next.put(index(x, y, z), data(colorMaterial(marker.color())));
            }
        }
        packets.apply(next, this::actualBlock, this::send);
        for (BlockData block : next.values()) {
            if (!block.getMaterial().isAir()) {
                visible++;
            }
        }
        renderCaption(frame.label(), nativeProjection);
    }

    public void close() {
        requireServerThread();
        try {
            packets.apply(Map.of(), this::actualBlock, this::send);
            visible = 0;
        } finally {
            if (caption != null) {
                try {
                    caption.remove();
                    caption = null;
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Could not remove Optics demo caption " + caption.getUniqueId(), exception);
                }
            }
        }
    }

    public int displayCount() {
        return packets.size() + (caption == null ? 0 : 1);
    }

    public int visibleCount() {
        return visible;
    }

    public long packetBatches() {
        return packets.batches();
    }

    public long packetChanges() {
        return packets.changes();
    }

    private void sampleProjection(SceneFrame frame, Map<Integer, BlockData> next) {
        List<AffineProjection> projections = new ArrayList<>(frame.portals().size());
        for (ScenePortal portal : frame.portals()) {
            if (portal.visible()) {
                projections.add(new AffineProjection(new AffineProjection.Options(portal.aperture(), portal.pose(),
                    portal.viewTransform(), frame.camera().eye())));
            }
        }
        if (projections.isEmpty()) {
            return;
        }
        for (int z = MIN_Z; z < MIN_Z + SIZE_Z; z++) {
            for (int y = MIN_Y; y < MIN_Y + SIZE_Y; y++) {
                for (int x = MIN_X; x < MIN_X + SIZE_X; x++) {
                    for (AffineProjection projection : projections) {
                        if (!projection.contains(x + 0.5D, y + 0.5D, z + 0.5D)) {
                            continue;
                        }
                        projection.destinationInto(x + 0.5D, y + 0.5D, z + 0.5D, destination);
                        DemoDestination.Voxel voxel = DemoDestination.sample(destination[0], destination[1], destination[2]);
                        int key = index(x, y, z);
                        if (!voxel.isAir()) {
                            next.put(key, data(Material.matchMaterial(voxel.state())));
                        } else if (!next.containsKey(key) && !actualBlock(key).getMaterial().isAir()) {
                            next.put(key, data(Material.AIR));
                        }
                    }
                }
            }
        }
    }

    private void renderCaption(String text, boolean nativeProjection) {
        if (caption == null) {
            caption = origin.getWorld().spawn(origin.clone().add(0.0D, 4.7D, 0.0D), TextDisplay.class, this::configureCaption);
            player.showEntity(plugin, caption);
        }
        String next = text + (nativeProjection ? "\nOptics Crisp projection" : "\nOptics block-packet projection");
        if (!label.equals(next)) {
            label = next;
            caption.setText(label);
        }
    }

    private BlockData actualBlock(int key) {
        int x = key % SIZE_X + MIN_X;
        int y = key / SIZE_X % SIZE_Y + MIN_Y;
        int z = key / (SIZE_X * SIZE_Y) + MIN_Z;
        return origin.getWorld().getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z).getBlockData();
    }

    private void send(Map<Integer, BlockData> delta) {
        if (!player.isOnline() || player.getWorld() != origin.getWorld()) {
            return;
        }
        Map<Position, BlockData> updates = new HashMap<>(delta.size());
        for (Map.Entry<Integer, BlockData> entry : delta.entrySet()) {
            int key = entry.getKey();
            int x = key % SIZE_X + MIN_X;
            int y = key / SIZE_X % SIZE_Y + MIN_Y;
            int z = key / (SIZE_X * SIZE_Y) + MIN_Z;
            updates.put(Position.block(origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z), entry.getValue());
        }
        player.sendMultiBlockChange(updates);
    }

    private int index(int x, int y, int z) {
        return ((z - MIN_Z) * SIZE_Y + y - MIN_Y) * SIZE_X + x - MIN_X;
    }

    private boolean inBounds(int x, int y, int z) {
        return x >= MIN_X && x < MIN_X + SIZE_X && y >= MIN_Y && y < MIN_Y + SIZE_Y && z >= MIN_Z && z < MIN_Z + SIZE_Z;
    }

    private BlockData data(Material material) {
        return blockData.computeIfAbsent(material, Material::createBlockData);
    }

    private Material colorMaterial(Rgba color) {
        if (color.red() > color.green() * 1.25F && color.red() > color.blue() * 1.25F) {
            return Material.ORANGE_CONCRETE;
        }
        if (color.green() > color.red() * 1.15F && color.green() > color.blue() * 1.15F) {
            return Material.LIME_CONCRETE;
        }
        return color.red() + color.green() + color.blue() > 2.2F ? Material.SEA_LANTERN : Material.CYAN_CONCRETE;
    }

    private void configureCaption(TextDisplay display) {
        display.setPersistent(false);
        display.setVisibleByDefault(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setBillboard(Display.Billboard.CENTER);
        display.setDisplayWidth(16.0F);
        display.setDisplayHeight(4.0F);
        display.setViewRange(1.0F);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setShadowed(true);
        display.setLineWidth(420);
    }

    private void requireServerThread() {
        if (!plugin.getServer().isPrimaryThread()) {
            throw new IllegalStateException("Demo rendering requires the server thread");
        }
    }

    public record RenderContext(Player player, Location origin) {}
}
