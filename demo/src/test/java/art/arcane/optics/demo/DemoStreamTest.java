package art.arcane.optics.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import art.arcane.optics.demo.scene.DemoScene;
import art.arcane.optics.demo.scene.DemoScenes;
import art.arcane.optics.demo.scene.SceneFrame;
import art.arcane.optics.demo.scene.ScenePortal;
import art.arcane.optics.math.BlockBox;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.plate.ViewPlate;
import art.arcane.optics.shape.ShapeDescriptor;
import art.arcane.optics.shape.FitMode;
import art.arcane.optics.stream.ViewStreamCapability;
import art.arcane.optics.stream.ViewStreamCodec;
import art.arcane.optics.stream.ViewStreamInbound;
import art.arcane.optics.stream.ViewStreamLimits;
import art.arcane.optics.stream.ViewStreamMessage;
import art.arcane.optics.stream.ViewStreamOptions;
import art.arcane.optics.stream.ViewStreamPhase;
import art.arcane.optics.stream.ViewStreamPlatform;
import art.arcane.optics.stream.ViewStreamProtocolException;
import art.arcane.optics.stream.ViewStreamSession;
import art.arcane.optics.stream.ViewStreamSessionRegistry;
import art.arcane.optics.stream.ViewStreamTransport;
import art.arcane.optics.transform.Affine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class DemoStreamTest {
    private static final Vec3d ORIGIN = new Vec3d(96.0D, 64.0D, 0.0D);
    private static final long CAPS = ViewStreamCapability.of(ViewStreamCapability.PLATES, ViewStreamCapability.MESH_RENDER,
        ViewStreamCapability.AFFINE_PORTALS, ViewStreamCapability.DEST_LIGHT, ViewStreamCapability.ATMOSPHERE);

    @Test
    void everySceneStreamsActualAffineSamplesWithoutRestartingDestinationMesh() throws Exception {
        for (DemoScene definition : DemoScenes.all()) {
            try (Harness harness = new Harness(CAPS)) {
                SceneFrame first = definition.sample(0.0D);
                harness.render(first);
                assertSamples(harness, first);
                long begins = harness.count(ViewStreamMessage.MeshBegin.class);
                assertTrue(begins > 0L, definition.id());
                harness.messages.clear();
                SceneFrame next = definition.sample(1.0D);
                harness.render(next);
                assertSamples(harness, next);
                assertEquals(0L, harness.count(ViewStreamMessage.MeshBegin.class), definition.id());
                assertEquals(0L, harness.count(ViewStreamMessage.Portal.class), definition.id());
                assertTrue(harness.warnings.isEmpty(), harness.warnings.toString());
            }
        }
    }

    @Test
    void nativeWithoutAffineCapabilityNeverReceivesTheNewOpcode() throws Exception {
        try (Harness harness = new Harness(CAPS & ~ViewStreamCapability.AFFINE_PORTALS.mask())) {
            harness.render(DemoScenes.find("affine-shear").orElseThrow().sample(1.0D));
            assertTrue(harness.count(ViewStreamMessage.MeshSection.class) > 0L);
            assertEquals(0L, harness.count(ViewStreamMessage.PortalAffine.class));
        }
        ViewStreamCodec codec = new ViewStreamCodec(List.of());
        assertFalse(ViewStreamCapability.AFFINE_PORTALS.in(codec.settle(ViewStreamCapability.AFFINE_PORTALS.mask())));
    }

    @Test
    void vanishedApertureDropsNativePane() throws Exception {
        try (Harness harness = new Harness(CAPS)) {
            DemoScene definition = DemoScenes.find("grow-vanish").orElseThrow();
            harness.render(definition.sample(0.0D));
            harness.messages.clear();
            harness.render(definition.sample(10.0D));
            assertEquals(1L, harness.count(ViewStreamMessage.PortalDrop.class));
            assertEquals(0L, harness.count(ViewStreamMessage.PortalAffine.class));
            assertEquals(0, harness.scene.portalCount());
        }
    }

    @Test
    void endingNativeDemoResetsTheClientBeforeClosingTheSession() throws Exception {
        try (Harness harness = new Harness(CAPS)) {
            harness.render(DemoScenes.find("continuous-spin").orElseThrow().sample(0.0D));
            assertTrue(harness.count(ViewStreamMessage.MeshSection.class) > 0L);
            harness.messages.clear();
            harness.registry.runtimeEnabled(false);
            harness.registry.shutdown();
            assertEquals(List.of(new ViewStreamMessage.SessionReset(ViewStreamMessage.ResetReason.DISABLED)),
                harness.messages);
            assertFalse(harness.session.send(new ViewStreamMessage.PortalDrop(1)));
            assertTrue(harness.warnings.isEmpty(), harness.warnings.toString());
        }
    }

    @Test
    void fixedSectionsContainDestinationTerrainAndAreReusedAcrossAnimation() {
        DemoStreamScene scene = new DemoStreamScene(ORIGIN);
        scene.update(DemoScenes.find("affine-shear").orElseThrow().sample(0.0D));
        BlockBox box = new BlockBox(80, 64, -16, 16, 16, 16);
        ViewPlate<String> first = scene.meshSection(scene, UUID.randomUUID(), box, 32);
        int landmark = first.cellRef(94, 65, -3);
        assertEquals("minecraft:ochre_froglight", first.paletteCell(landmark).sourceData());
        scene.update(DemoScenes.find("affine-shear").orElseThrow().sample(2.0D));
        assertSame(first, scene.meshSection(scene, UUID.randomUUID(), box, 32));
    }

    @Test
    void affineCodecRejectsInvalidMatricesAndTruncatedSamples() throws Exception {
        ViewStreamCodec codec = new ViewStreamCodec(List.of());
        ViewStreamMessage.PortalAffine sample = new ViewStreamMessage.PortalAffine(1, 2L, Affine.IDENTITY, Affine.IDENTITY,
            ShapeDescriptor.FULL);
        byte[] encoded = codec.encodeS2C(sample, 1, 0);
        assertEquals(sample, codec.decodeS2C(encoded, CAPS).message());
        assertThrows(ViewStreamProtocolException.class,
            () -> codec.decodeS2C(Arrays.copyOf(encoded, encoded.length - 1), CAPS));
        assertThrows(ViewStreamProtocolException.class, () -> codec.decodeS2C(encoded, CAPS & ~ViewStreamCapability.AFFINE_PORTALS.mask()));
        assertThrows(IllegalArgumentException.class,
            () -> new ViewStreamMessage.PortalAffine(1, 1L, Affine.scale(0.0D), Affine.IDENTITY, ShapeDescriptor.FULL));
        assertThrows(IllegalArgumentException.class,
            () -> new ViewStreamMessage.PortalAffine(1, 1L, Affine.IDENTITY, Affine.translation(Double.NaN, 0.0D, 0.0D), ShapeDescriptor.FULL));
    }

    private void assertSamples(Harness harness, SceneFrame frame) {
        List<ViewStreamMessage.PortalAffine> samples = new ArrayList<>();
        for (ViewStreamMessage message : harness.messages) {
            if (message instanceof ViewStreamMessage.PortalAffine sample) {
                samples.add(sample);
            }
        }
        List<ScenePortal> visible = new ArrayList<>();
        for (ScenePortal portal : frame.portals()) {
            if (portal.visible()) {
                visible.add(portal);
            }
        }
        assertEquals(visible.size(), samples.size());
        samples.sort(Comparator.comparingInt(ViewStreamMessage.PortalAffine::portalKey));
        Affine world = Affine.translation(ORIGIN);
        for (int index = 0; index < visible.size(); index++) {
            ScenePortal portal = visible.get(index);
            ViewStreamMessage.PortalAffine sample = samples.get(index);
            assertEquals(world.compose(portal.pose()), sample.apertureToSource());
            assertEquals(world.compose(portal.viewTransform()).compose(world.inverse()), sample.sourceToDestination());
            assertEquals(ShapeDescriptor.of(portal.aperture(), FitMode.STRETCH), sample.shape());
            assertEquals(harness.scene.sampleRevision(), sample.sampleRevision());
        }
    }

    private static final class Harness implements AutoCloseable {
        private final DemoStreamScene scene = new DemoStreamScene(ORIGIN);
        private final ViewStreamCodec codec = new ViewStreamCodec(List.of());
        private final List<ViewStreamMessage> messages = new ArrayList<>();
        private final List<Throwable> warnings = new ArrayList<>();
        private final AtomicLong clock = new AtomicLong(1_000_000_000_000L);
        private final ViewStreamSessionRegistry<DemoStreamScene, String> registry;
        private final ViewStreamSession<DemoStreamScene, String> session;
        private long tick;

        private Harness(long clientCaps) throws Exception {
            ViewStreamTransport<DemoStreamScene> transport = new ViewStreamTransport<>() {
                @Override
                public void send(DemoStreamScene observer, byte[] payload) {
                    try {
                        messages.add(codec.decodeS2C(payload, CAPS).message());
                    } catch (ViewStreamProtocolException failure) {
                        throw new IllegalStateException(failure);
                    }
                }

                @Override
                public void flush(DemoStreamScene observer) {
                }
            };
            ViewStreamPlatform<DemoStreamScene, String> platform = new ViewStreamPlatform<>(transport, scene, null, scene,
                null, Runnable::run, state -> state, 6000, CAPS, clock::get,
                (message, error) -> warnings.add(new AssertionError(message, error)), List.of(), null);
            ViewStreamOptions options = new ViewStreamOptions(true, false, 100, ViewStreamLimits.MIN_MAX_FRAME_BYTES,
                0, false, true, false, false, false, false, false, false, 0, 0L);
            registry = new ViewStreamSessionRegistry<>(platform, options);
            session = registry.open(UUID.randomUUID(), scene, 0L);
            session.brand("fabric");
            session.offer(ViewStreamPhase.PLAY);
            byte[] hello = codec.encodeC2S(new ViewStreamMessage.Hello(ViewStreamLimits.WIRE_VERSION, 6000, clientCaps,
                ViewStreamLimits.MIN_MAX_FRAME_BYTES, 128, 0L, "fabric"));
            assertEquals(ViewStreamInbound.HELLO_ACCEPTED, session.receive(hello, 0, hello.length));
            messages.clear();
        }

        private void render(SceneFrame frame) {
            scene.update(frame);
            clock.addAndGet(50_000_000L);
            session.tick(++tick);
        }

        private long count(Class<? extends ViewStreamMessage> type) {
            return messages.stream().filter(type::isInstance).count();
        }

        @Override
        public void close() {
            registry.shutdown();
        }
    }
}
