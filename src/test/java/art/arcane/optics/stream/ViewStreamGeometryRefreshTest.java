package art.arcane.optics.stream;

import art.arcane.optics.aperture.ApertureDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ViewStreamGeometryRefreshTest {
    @Test
    void metadataCapturesOnOwnerAndEmitsMatchingRevisionOnSeparateLane() throws Exception {
        ConcurrentLinkedQueue<Runnable> pending = new ConcurrentLinkedQueue<>();
        SessionHarness harness = new SessionHarness(SessionHarness.options(false, 0), pending::add, 0L);
        Thread owner = Thread.currentThread();
        AtomicReference<String> world = new AtomicReference<>("test:source");
        SessionPortal root = harness.access.add(new SessionPortal("metadata mirror", 0));
        root.mirror = true;
        harness.access.meshDistance = 32;
        harness.access.metadata = (portal, context) -> {
            assertSame(owner, Thread.currentThread());
            String captured = world.get();
            return revision -> {
                assertNotEquals(owner, Thread.currentThread());
                return List.of(TestEffects.scene(context.portalKey(), List.of(captured, Integer.toString(revision))));
            };
        };
        harness.handshake(SessionHarness.NATIVE_CAPS | TestEffects.CAPABILITY);
        harness.clock.addAndGet(SessionHarness.TICK_NANOS);
        harness.session.tick(++harness.serverTick);
        world.set("test:arrival");
        Thread lane = new Thread(() -> {
            Runnable job;
            while ((job = pending.poll()) != null) {
                job.run();
            }
        }, "metadata-test-lane");
        lane.start();
        lane.join(3000L);
        assertFalse(lane.isAlive());
        harness.pump();
        ViewStreamMessage.Portal descriptor = (ViewStreamMessage.Portal) harness.last(ViewStreamMessageType.PORTAL);
        TestEffects.Effect metadata = TestEffects.payload(harness.last(TestEffects.ID));
        assertEquals(descriptor.portalKey(), metadata.key());
        assertEquals(List.of("test:source", Integer.toString(descriptor.geometryRevision())), metadata.names());
        int index = harness.client.received.indexOf(descriptor);
        assertEquals(TestEffects.ID, harness.client.received.get(index + 1).id());
        assertTrue(harness.warnings.isEmpty(), harness.warnings.toString());
    }

    @Test
    void changingBackendStampsRetainEqualDescriptorsWhileRefreshingNestedAttendance() throws ViewStreamProtocolException {
        SessionHarness harness = new SessionHarness(SessionHarness.options(false, 0));
        harness.access.meshDistance = 32;
        SessionPortal root = harness.access.add(new SessionPortal("root mirror", 0));
        SessionPortal child = new SessionPortal("child mirror", 8);
        root.mirror = true;
        root.recursionDepth = 1;
        child.mirror = true;
        harness.access.nested.put(root.id, List.of(child));
        harness.handshake(SessionHarness.NATIVE_CAPS);
        harness.tick();
        assertEquals(2, harness.client.portals.size());
        Map<Integer, ApertureDescriptor> initial = Map.copyOf(harness.client.portals);
        int descriptors = harness.sent(ViewStreamMessageType.PORTAL);
        int begins = harness.sent(ViewStreamMessageType.MESH_BEGIN);
        int nestedCalls = harness.access.nestedCalls;

        for (int tick = 0; tick < 40; tick++) {
            root.geometryRevision++;
            child.geometryRevision++;
            harness.tick();
        }

        assertEquals(initial, harness.client.portals);
        assertEquals(descriptors, harness.sent(ViewStreamMessageType.PORTAL));
        assertEquals(begins, harness.sent(ViewStreamMessageType.MESH_BEGIN));
        assertTrue(harness.access.nestedCalls >= nestedCalls + 40);
        assertTrue(harness.access.contexts.containsValue(child.id));

        root.frontSide = true;
        root.geometryRevision++;
        harness.tick();

        assertTrue(harness.sent(ViewStreamMessageType.PORTAL) > descriptors);
        assertTrue(harness.sent(ViewStreamMessageType.MESH_BEGIN) > begins);
        assertTrue(harness.client.portals.values().stream().anyMatch(ApertureDescriptor::frontSide));
        assertTrue(harness.access.contexts.containsValue(child.id));
        assertTrue(harness.warnings.isEmpty(), harness.warnings.toString());
    }
}
