package art.arcane.optics.demo;

import art.arcane.optics.aperture.ApertureDescriptor;
import art.arcane.optics.claim.BlockClaim;
import art.arcane.optics.demo.scene.SceneFrame;
import art.arcane.optics.demo.scene.ScenePortal;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.math.BlockBox;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.plate.PlateCell;
import art.arcane.optics.plate.PlateGrid;
import art.arcane.optics.plate.ViewPlate;
import art.arcane.optics.plate.ViewPlateKey;
import art.arcane.optics.scan.Sample;
import art.arcane.optics.shape.FitMode;
import art.arcane.optics.shape.ShapeDescriptor;
import art.arcane.optics.stream.BrickLightSource;
import art.arcane.optics.stream.EnvironmentState;
import art.arcane.optics.stream.SessionPalette;
import art.arcane.optics.stream.ViewStreamEndpoints;
import art.arcane.optics.stream.ViewStreamMessage;
import art.arcane.optics.stream.ViewStreamScene;
import art.arcane.optics.transform.Affine;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class DemoStreamScene implements ViewStreamEndpoints<DemoStreamScene, String>, ViewStreamScene<DemoStreamScene> {
    private static final int DISTANCE = 32;
    private static final EnvironmentState.Color WHITE = new EnvironmentState.Color(1.0F, 1.0F, 1.0F);
    private static final EnvironmentState.Color SKY = new EnvironmentState.Color(0.4F, 0.7F, 1.0F);
    private static final BrickLightSource LIGHT = (x, y, z, block, sky) -> {
        Arrays.fill(block, (byte) 0xFF);
        Arrays.fill(sky, (byte) 0xFF);
        return true;
    };

    private final Vec3d origin;
    private final Affine toWorld;
    private final Affine fromWorld;
    private final ApertureDescriptor geometry;
    private final Map<UUID, ScenePortal> portals = new LinkedHashMap<>(2);
    private final Map<BlockBox, ViewPlate<String>> sections = new HashMap<>();
    private final Map<DemoDestination.Voxel, PlateCell<String>> cells = new HashMap<>();
    private final Map<UUID, Long> emitted = new HashMap<>();
    private SceneFrame frame;
    private long revision;

    public DemoStreamScene(Vec3d origin) {
        this.origin = origin;
        toWorld = Affine.translation(origin);
        fromWorld = toWorld.inverse();
        geometry = new ApertureDescriptor((int) origin.x() - 4, (int) origin.y() - 4, (int) origin.z(),
            Face.S.ordinal(), true, 0, false, 8, 8, new long[] {-1L}, ShapeDescriptor.FULL,
            0.01F, 0.0F, 0.0F, DISTANCE, 0, 0, 0, 0, BlockClaim.LightingPolicy.LOCAL.ordinal(), 0, 0,
            0.0D, 0, 0L, List.of());
    }

    public void update(SceneFrame next) {
        frame = next;
        revision++;
        portals.clear();
        for (ScenePortal portal : next.portals()) {
            if (portal.visible()) {
                portals.put(id(portal.id()), portal);
            }
        }
    }

    public long sampleRevision() {
        return revision;
    }

    public int portalCount() {
        return portals.size();
    }

    public Set<BlockBox> requiredSections() {
        Set<BlockBox> required = new HashSet<>();
        for (int x = ((int) origin.x() - 18) >> 4; x <= ((int) origin.x() + 17) >> 4; x++) {
            for (int y = ((int) origin.y() - 1) >> 4; y <= ((int) origin.y() + 11) >> 4; y++) {
                for (int z = ((int) origin.z() - 25) >> 4; z <= (int) origin.z() >> 4; z++) {
                    required.add(new BlockBox(x << 4, y << 4, z << 4, 16, 16, 16));
                }
            }
        }
        return Set.copyOf(required);
    }

    @Override
    public void interested(DemoStreamScene observer, List<UUID> out) {
        out.addAll(observer.portals.keySet());
    }

    @Override
    public long geometryRevision(DemoStreamScene observer, UUID portal) {
        return 1L;
    }

    @Override
    public ApertureDescriptor geometry(DemoStreamScene observer, UUID portal, SessionPalette palette) {
        return observer.portals.containsKey(portal) ? observer.geometry : null;
    }

    @Override
    public int meshDistanceBlocks(DemoStreamScene observer) {
        return DISTANCE;
    }

    @Override
    public Vec3d meshEye(DemoStreamScene observer) {
        return observer.toWorld.point(observer.frame.camera().eye());
    }

    @Override
    public ViewPlate<String> meshSection(DemoStreamScene observer, UUID portal, BlockBox clip, int distance) {
        return observer.sections.computeIfAbsent(clip, observer::section);
    }

    @Override
    public ViewStreamMessage.PortalAffine affine(DemoStreamScene observer, UUID endpoint, int key, long tick, boolean full) {
        ScenePortal portal = observer.portals.get(endpoint);
        if (portal == null || !full && Long.valueOf(observer.revision).equals(observer.emitted.get(endpoint))) {
            return null;
        }
        observer.emitted.put(endpoint, observer.revision);
        return new ViewStreamMessage.PortalAffine(key, observer.revision, observer.toWorld.compose(portal.pose()),
            observer.toWorld.compose(portal.viewTransform()).compose(observer.fromWorld),
            ShapeDescriptor.of(portal.aperture(), FitMode.STRETCH));
    }

    @Override
    public ViewStreamMessage.Environment environment(DemoStreamScene observer, UUID endpoint, int key, long tick, boolean full) {
        if (!full) {
            return null;
        }
        EnvironmentState state = new EnvironmentState(6000L,
            new EnvironmentState.Sky(EnvironmentState.Skybox.OVERWORLD, 0.0F, 0.0F, 0.0F, 0.0F,
                new EnvironmentState.ColorAlpha(0.0F, 0.0F, 0.0F, 0.0F), SKY, 0, 0.0F, 0.0F),
            new EnvironmentState.Fog(SKY, 128.0F, 256.0F, 256.0F, 256.0F, SKY, 128.0F, 256.0F),
            new EnvironmentState.Lighting(WHITE, 1.0F, WHITE, WHITE),
            new EnvironmentState.Clouds(new EnvironmentState.ColorAlpha(1.0F, 1.0F, 1.0F, 0.0F), 192.0F),
            OpticTransform.IDENTITY, new EnvironmentState.Dimension(-64, 384, true, EnvironmentState.CardinalLighting.DEFAULT, 63.0D, false),
            new EnvironmentState.World("optics:virtual_destination", 6000L, "minecraft:plains", 63, 15, 15, 384, false, 1.0F,
                EnvironmentState.EyeMedium.NONE, true), 1.0F);
        return new ViewStreamMessage.Environment(key, state);
    }

    @Override
    public ViewPlate<String> plate(DemoStreamScene observer, UUID portal, boolean firstAttendance) {
        return null;
    }

    @Override
    public boolean refused(DemoStreamScene observer, UUID portal) {
        return false;
    }

    @Override
    public ViewPlate<String> standbyPlate(DemoStreamScene observer, UUID portal) {
        return null;
    }

    @Override
    public BrickLightSource lightBaseline(DemoStreamScene observer, UUID portal, ViewPlate<String> plate) {
        return LIGHT;
    }

    @Override
    public void releaseVanilla(DemoStreamScene observer, UUID portal) {
    }

    @Override
    public void nested(DemoStreamScene observer, UUID parent, ApertureDescriptor parentGeometry, List<UUID> out) {
    }

    @Override
    public long nestedGeometryRevision(DemoStreamScene observer, UUID parent, UUID child) {
        return 0L;
    }

    @Override
    public ApertureDescriptor nestedGeometry(DemoStreamScene observer, UUID parent, UUID child, SessionPalette palette) {
        return null;
    }

    @Override
    public ViewPlate<String> nestedPlate(DemoStreamScene observer, UUID parent, UUID child) {
        return null;
    }

    @Override
    public void effects(DemoStreamScene observer, List<UUID> out) {
    }

    @Override
    public long effectGeometryRevision(DemoStreamScene observer, UUID portal) {
        return 0L;
    }

    @Override
    public ApertureDescriptor effectGeometry(DemoStreamScene observer, UUID portal, SessionPalette palette) {
        return null;
    }

    @Override
    public ViewStreamMessage.Extension effects(DemoStreamScene observer, UUID endpoint, int key, long tick, boolean full) {
        return null;
    }

    @Override
    public ViewStreamMessage.Atmosphere atmosphere(DemoStreamScene observer, UUID endpoint, int key, long tick, boolean full) {
        return null;
    }

    private ViewPlate<String> section(BlockBox box) {
        PlateGrid.Writer<String> writer = new PlateGrid.Writer<>(box);
        for (int x = box.minX(); x < box.minX() + box.sizeX(); x++) {
            for (int y = box.minY(); y < box.minY() + box.sizeY(); y++) {
                for (int z = box.minZ(); z < box.minZ() + box.sizeZ(); z++) {
                    DemoDestination.Voxel voxel = DemoDestination.sample(x - origin.x() + 0.5D, y - origin.y() + 0.5D, z - origin.z() + 0.5D);
                    PlateCell<String> cell = cells.computeIfAbsent(voxel, this::cell);
                    writer.put(x, y, z, cell);
                }
            }
        }
        PlateGrid<String> grid = writer.finish();
        return new ViewPlate<>(new ViewPlateKey(id("destination"), "optics:virtual_destination", true, 0, 0L), grid,
            1L, 1L, null, 0L, box.minX() >> 4, box.minZ() >> 4, (box.minX() + box.sizeX() - 1) >> 4,
            (box.minZ() + box.sizeZ() - 1) >> 4, ViewPlate.estimateBytes(grid), null);
    }

    private PlateCell<String> cell(DemoDestination.Voxel voxel) {
        String state = voxel.state();
        return new PlateCell<>(voxel.isAir() ? Sample.Kind.REMOTE_AIR : Sample.Kind.BLOCK, state, state, null);
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
