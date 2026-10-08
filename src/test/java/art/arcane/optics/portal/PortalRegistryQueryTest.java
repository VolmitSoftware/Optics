package art.arcane.optics.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sun.management.ThreadMXBean;

import art.arcane.optics.math.Box;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;

final class PortalRegistryQueryTest {
    private static final String WORLD = "world";
    private static final String OTHER = "other";
    private static final Vec3d SOUTH = new Vec3d(0.0D, 0.0D, 1.0D);

    @Test
    void atFindsThePortalWhoseOpeningHoldsThePoint() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition gate = PortalFixtures.wall(Face.N, 0, 64, 0, 7, 7).withShape("circle");
        PortalDefinition near = PortalFixtures.wall(Face.N, 0, 64, 1, 7, 7);
        registry.add(WORLD, gate);
        registry.add(WORLD, near);
        registry.add(OTHER, PortalFixtures.wall(Face.N, 0, 64, 0, 7, 7));

        assertSame(gate, registry.at(WORLD, new Vec3d(3.5D, 67.5D, 0.6D), 0.25D));
        assertSame(near, registry.at(WORLD, new Vec3d(3.5D, 67.5D, 1.4D), 0.25D));
        assertNull(registry.at(WORLD, new Vec3d(3.5D, 67.5D, 1.0D), 0.25D));
        assertNull(registry.at(WORLD, new Vec3d(0.2D, 64.2D, 0.5D), 0.25D));
        assertNull(registry.at("missing", new Vec3d(3.5D, 67.5D, 0.5D), 0.25D));

        List<PortalDefinition> out = new ArrayList<PortalDefinition>();
        assertEquals(2, registry.at(WORLD, new Vec3d(3.5D, 67.5D, 1.0D), 0.5D, out));
        assertEquals(List.of(gate, near), out);
        assertSame(gate, registry.at(WORLD, new Vec3d(3.5D, 67.5D, 0.9D), 0.5D));
    }

    @Test
    void crossingsAreOrderedByFractionAndAppended() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition far = PortalFixtures.wall(Face.N, 0, 64, 10, 3, 3);
        PortalDefinition near = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);
        PortalDefinition middle = PortalFixtures.wall(Face.S, 0, 64, 5, 3, 3);
        PortalDefinition elsewhere = PortalFixtures.wall(Face.N, 40, 64, 5, 3, 3);
        registry.add(WORLD, far);
        registry.add(WORLD, near);
        registry.add(WORLD, middle);
        registry.add(WORLD, elsewhere);
        Vec3d start = new Vec3d(1.5D, 65.5D, -2.0D);
        Vec3d end = new Vec3d(1.5D, 65.5D, 12.0D);
        List<PortalCrossing> out = new ArrayList<PortalCrossing>();
        out.add(null);

        int count = registry.crossings(WORLD, start, end, SOUTH, SOUTH, out);

        assertEquals(3, count);
        assertEquals(4, out.size());
        assertNull(out.get(0));
        assertSame(near, out.get(1).portal());
        assertSame(middle, out.get(2).portal());
        assertSame(far, out.get(3).portal());
        assertTrue(out.get(1).fraction() < out.get(2).fraction() && out.get(2).fraction() < out.get(3).fraction());
        assertSame(near, registry.firstCrossing(WORLD, start, end, SOUTH, SOUTH).portal());

        List<PortalCrossing> back = new ArrayList<PortalCrossing>();
        registry.crossings(WORLD, end, start, SOUTH.negate(), SOUTH.negate(), back);
        assertSame(far, back.get(0).portal());
        assertSame(near, back.get(2).portal());
        assertNull(registry.firstCrossing(WORLD, new Vec3d(1.5D, 65.5D, 1.0D), new Vec3d(1.5D, 65.5D, 4.0D), SOUTH, SOUTH));
        assertNull(registry.firstCrossing(OTHER, start, end, SOUTH, SOUTH));
    }

    @Test
    void intersectingReturnsPortalsWhoseAreaOverlapsTheBox() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition a = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);
        PortalDefinition b = PortalFixtures.wall(Face.U, 30, 64, 30, 5, 5);
        registry.add(WORLD, a);
        registry.add(WORLD, b);
        List<PortalDefinition> out = new ArrayList<PortalDefinition>();

        assertEquals(1, registry.intersecting(WORLD, new Box(2.5D, 10.0D, 60.0D, 65.0D, -1.0D, 0.2D), out));
        assertEquals(List.of(a), out);
        out.clear();
        assertEquals(2, registry.intersecting(WORLD, new Box(-100.0D, 100.0D, 0.0D, 128.0D, -100.0D, 100.0D), out));
        out.clear();
        assertEquals(0, registry.intersecting(WORLD, new Box(3.5D, 10.0D, 60.0D, 65.0D, -1.0D, 0.2D), out));
        assertEquals(0, registry.intersecting(OTHER, new Box(-100.0D, 100.0D, 0.0D, 128.0D, -100.0D, 100.0D), out));
    }

    @Test
    void visibleChecksFieldOfViewAndRangeFromEitherSide() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition ahead = PortalFixtures.wall(Face.N, 0, 64, 10, 3, 3);
        PortalDefinition behind = PortalFixtures.wall(Face.S, 0, 64, -10, 3, 3);
        PortalDefinition distant = PortalFixtures.wall(Face.N, 0, 64, 200, 3, 3);
        PortalDefinition aside = PortalFixtures.wall(Face.E, 30, 64, 1, 3, 3);
        registry.add(WORLD, ahead);
        registry.add(WORLD, behind);
        registry.add(WORLD, distant);
        registry.add(WORLD, aside);
        Vec3d eye = new Vec3d(1.5D, 65.5D, 0.0D);
        List<PortalDefinition> out = new ArrayList<PortalDefinition>();

        assertEquals(1, registry.visible(WORLD, eye, SOUTH, 90.0D, 64.0D, out));
        assertEquals(List.of(ahead), out);
        out.clear();
        assertEquals(1, registry.visible(WORLD, eye, SOUTH.negate(), 90.0D, 64.0D, out));
        assertEquals(List.of(behind), out);
        out.clear();
        assertEquals(2, registry.visible(WORLD, eye, SOUTH, 90.0D, 512.0D, out));
        out.clear();
        assertEquals(4, registry.visible(WORLD, eye, new Vec3d(0.0D, 0.0D, 2.0D), 360.0D, 512.0D, out));
        out.clear();
        assertEquals(1, registry.visible(WORLD, new Vec3d(1.5D, 65.5D, 20.0D), SOUTH.negate(), 60.0D, 15.0D, out));
        assertEquals(List.of(ahead), out);
    }

    @Test
    void chunkIndexHandlesBordersAndNegativeCoordinates() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition straddle = PortalFixtures.wall(Face.N, -17, 64, -33, 4, 3);
        registry.add(WORLD, straddle);

        assertSame(straddle, crossAt(registry, -16.5D, -32.5D));
        assertSame(straddle, crossAt(registry, -14.5D, -32.5D));
        assertNull(crossAt(registry, -17.5D, -32.5D));
        assertNull(crossAt(registry, -12.5D, -32.5D));
        assertSame(straddle, registry.at(WORLD, new Vec3d(-16.0D, 65.0D, -32.5D), 0.01D));
        assertSame(straddle, registry.at(WORLD, new Vec3d(-13.01D, 65.0D, -32.5D), 0.01D));
    }

    @Test
    void offsetPlanesAreIndexedWhereThePlaneLies() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition offset = PortalFixtures.wall(Face.E, 15, 64, 3, 3, 3).withPlaneOffset(1.0D);
        registry.add(WORLD, offset);

        PortalCrossing crossing = registry.firstCrossing(WORLD, new Vec3d(16.2D, 65.5D, 4.5D), new Vec3d(16.8D, 65.5D, 4.5D), Vec3d.UNIT_X,
            Vec3d.UNIT_X);

        assertSame(offset, crossing.portal());
        assertEquals(16.5D, crossing.point().x(), 1.0E-12D);
        assertSame(offset, registry.at(WORLD, new Vec3d(16.5D, 65.5D, 4.5D), 0.01D));
    }

    @Test
    void updatesAndRemovalsReindex() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition portal = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);
        registry.add(WORLD, portal);

        registry.update(portal.id(), definition -> definition.moved(100, 0, -100));

        assertNull(crossAt(registry, 1.5D, 0.5D));
        assertSame(registry.get(portal.id()), crossAt(registry, 101.5D, -99.5D));

        registry.add(OTHER, registry.get(portal.id()));
        assertNull(crossAt(registry, 101.5D, -99.5D));

        registry.add(WORLD, portal);
        registry.remove(portal.id());
        assertNull(crossAt(registry, 1.5D, 0.5D));
        assertEquals(0, registry.in(WORLD).size());
    }

    @Test
    void longSegmentsStillFindPortals() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition portal = PortalFixtures.wall(Face.E, 500, 64, 500, 3, 3);
        registry.add(WORLD, portal);

        PortalCrossing crossing = registry.firstCrossing(WORLD, new Vec3d(-1000.0D, 65.5D, -998.5D), new Vec3d(2000.0D, 65.5D, 2001.5D),
            Vec3d.UNIT_X, Vec3d.UNIT_X);

        assertSame(portal, crossing.portal());
    }

    @Test
    void queriesThatMissAllocateNothing() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        for (int index = 0; index < 32; index++) {
            registry.add(WORLD, PortalFixtures.wall(Face.N, index * 7 - 100, 64, index * 5 - 80, 3, 3));
        }
        Vec3d start = new Vec3d(-30.5D, 65.5D, -28.0D);
        Vec3d end = new Vec3d(-28.5D, 66.5D, -26.0D);
        Vec3d point = new Vec3d(-29.5D, 65.5D, -27.0D);
        Box box = new Box(-31.0D, -28.0D, 60.0D, 70.0D, -28.5D, -26.0D);
        List<PortalCrossing> crossings = new ArrayList<PortalCrossing>();
        List<PortalDefinition> portals = new ArrayList<PortalDefinition>();
        long sink = 0L;
        for (int warm = 0; warm < 20_000; warm++) {
            sink += registry.crossings(WORLD, start, end, SOUTH, SOUTH, crossings);
            sink += registry.at(WORLD, point, 0.25D, portals);
            sink += registry.intersecting(WORLD, box, portals);
            sink += registry.firstCrossing(WORLD, start, end, SOUTH, SOUTH) == null ? 0 : 1;
        }
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int call = 0; call < 100_000; call++) {
            sink += registry.crossings(WORLD, start, end, SOUTH, SOUTH, crossings);
            sink += registry.at(WORLD, point, 0.25D, portals);
            sink += registry.intersecting(WORLD, box, portals);
            sink += registry.firstCrossing(WORLD, start, end, SOUTH, SOUTH) == null ? 0 : 1;
            sink += registry.at(WORLD, point, 0.25D) == null ? 0 : 1;
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        assertTrue(allocated < 1_024L, allocated + " bytes allocated by registry queries that miss");
        assertEquals(0L, sink);
    }

    private static PortalDefinition crossAt(PortalRegistry<String> registry, double x, double z) {
        PortalCrossing crossing = registry.firstCrossing(WORLD, new Vec3d(x, 65.5D, z + 1.0D), new Vec3d(x, 65.5D, z - 1.0D), SOUTH.negate(),
            SOUTH.negate());
        return crossing == null ? null : crossing.portal();
    }
}
