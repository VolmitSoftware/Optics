package art.arcane.optics.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import art.arcane.optics.crossing.ScaleRule;
import art.arcane.optics.frame.QuarterTurn;
import art.arcane.optics.math.Face;

final class PortalRegistryTest {
    private static final String OVERWORLD = "overworld";
    private static final String NETHER = "nether";

    @Test
    void addGetRemoveAndReplace() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition a = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);
        PortalDefinition b = PortalFixtures.wall(Face.S, 10, 64, 10, 3, 3);

        assertNull(registry.add(OVERWORLD, a));
        assertNull(registry.add(NETHER, b));

        assertEquals(2, registry.size());
        assertSame(a, registry.get(a.id()));
        assertEquals(OVERWORLD, registry.world(a.id()));
        assertTrue(registry.contains(b.id()));
        assertEquals(List.of(a, b), registry.all());
        assertEquals(List.of(a), registry.in(OVERWORLD));
        assertEquals(List.of(b), registry.in(NETHER));
        assertEquals(List.of(), registry.in("end"));

        PortalDefinition moved = a.moved(1, 0, 0);
        assertSame(a, registry.add(OVERWORLD, moved));
        assertSame(moved, registry.get(a.id()));
        assertEquals(2, registry.size());

        assertSame(moved, registry.remove(a.id()));
        assertNull(registry.get(a.id()));
        assertNull(registry.world(a.id()));
        assertFalse(registry.contains(a.id()));
        assertNull(registry.remove(a.id()));
        assertEquals(List.of(b), registry.all());
        assertEquals(List.of(), registry.in(OVERWORLD));
        assertThrows(UnsupportedOperationException.class, () -> registry.all().clear());
    }

    @Test
    void replacingIntoAnotherWorldMovesTheEntry() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition a = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);
        registry.add(OVERWORLD, a);

        registry.add(NETHER, a);

        assertEquals(NETHER, registry.world(a.id()));
        assertEquals(List.of(), registry.in(OVERWORLD));
        assertEquals(List.of(a), registry.in(NETHER));
    }

    @Test
    void linkConnectsBothDirectionsAndKeepsRules() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition a = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);
        PortalDefinition b = PortalFixtures.wall(Face.S, 10, 64, 10, 3, 3).linked(PortalLink.mirror(QuarterTurn.DEGREES_90)
            .withScale(ScaleRule.motion()));
        registry.add(OVERWORLD, a);
        registry.add(NETHER, b);

        registry.link(a.id(), b.id());

        assertEquals(PortalLink.to(b.id()), registry.get(a.id()).link());
        assertEquals(PortalLink.to(a.id()).withScale(ScaleRule.motion()), registry.get(b.id()).link());
        assertSame(registry.get(b.id()), registry.destination(registry.get(a.id())));
        assertSame(registry.get(a.id()), registry.destination(registry.get(b.id())));
        assertThrows(IllegalArgumentException.class, () -> registry.link(a.id(), a.id()));
        assertThrows(IllegalArgumentException.class, () -> registry.link(a.id(), UUID.randomUUID()));
    }

    @Test
    void linkOneWayLeavesTheTargetAlone() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition a = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);
        PortalDefinition b = PortalFixtures.wall(Face.S, 10, 64, 10, 3, 3);
        registry.add(OVERWORLD, a);
        registry.add(OVERWORLD, b);

        registry.linkOneWay(a.id(), b.id());

        assertEquals(b.id(), registry.get(a.id()).link().target());
        assertNull(registry.get(b.id()).link());
        assertNull(registry.destination(registry.get(b.id())));
    }

    @Test
    void unlinkClearsThePortalAndEveryPortalTargetingIt() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition a = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);
        PortalDefinition b = PortalFixtures.wall(Face.S, 10, 64, 10, 3, 3);
        PortalDefinition c = PortalFixtures.wall(Face.E, 20, 64, 20, 3, 3);
        PortalDefinition d = PortalFixtures.wall(Face.W, 30, 64, 30, 3, 3);
        registry.add(OVERWORLD, a);
        registry.add(OVERWORLD, b);
        registry.add(OVERWORLD, c);
        registry.add(OVERWORLD, d);
        registry.link(a.id(), b.id());
        registry.linkOneWay(c.id(), a.id());
        registry.linkOneWay(d.id(), b.id());

        registry.unlink(a.id());

        assertNull(registry.get(a.id()).link());
        assertNull(registry.get(b.id()).link());
        assertNull(registry.get(c.id()).link());
        assertEquals(b.id(), registry.get(d.id()).link().target());
    }

    @Test
    void destinationIsNullWhenUnlinkedOrTheTargetIsGoneAndSelfForMirrors() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition a = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);
        PortalDefinition b = PortalFixtures.wall(Face.S, 10, 64, 10, 3, 3);
        registry.add(OVERWORLD, a);
        registry.add(OVERWORLD, b);
        assertNull(registry.destination(a));

        registry.link(a.id(), b.id());
        registry.remove(b.id());
        assertNull(registry.destination(registry.get(a.id())));

        registry.mirror(a.id(), QuarterTurn.DEGREES_270);
        PortalDefinition mirror = registry.get(a.id());
        assertTrue(mirror.link().isMirror());
        assertEquals(QuarterTurn.DEGREES_270, mirror.link().mirrorTurns());
        assertSame(mirror, registry.destination(mirror));
    }

    @Test
    void updateReplacesTheDefinition() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition a = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);
        registry.add(OVERWORLD, a);

        PortalDefinition updated = registry.update(a.id(), portal -> portal.withShape("circle"));

        assertSame(updated, registry.get(a.id()));
        assertEquals(a.withShape("circle"), updated);
        assertNull(registry.update(UUID.randomUUID(), portal -> portal));
        assertThrows(IllegalArgumentException.class, () -> registry.update(a.id(), portal -> PortalDefinition.builder(portal).id(UUID.randomUUID()).build()));
        assertThrows(NullPointerException.class, () -> registry.update(a.id(), portal -> null));
    }

    @Test
    void revisionsBumpOnEveryMutationAndOnlyForTouchedPortals() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition a = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);
        PortalDefinition b = PortalFixtures.wall(Face.S, 10, 64, 10, 3, 3);
        PortalDefinition c = PortalFixtures.wall(Face.E, 20, 64, 20, 3, 3);
        assertEquals(0L, registry.revision());
        assertEquals(0L, registry.revision(a.id()));

        registry.add(OVERWORLD, a);
        registry.add(OVERWORLD, b);
        registry.add(OVERWORLD, c);
        long afterAdds = registry.revision();
        long revisionA = registry.revision(a.id());
        long revisionC = registry.revision(c.id());
        assertTrue(revisionA > 0L);
        assertTrue(afterAdds >= registry.revision(c.id()));

        registry.link(a.id(), b.id());
        assertTrue(registry.revision() > afterAdds);
        assertTrue(registry.revision(a.id()) > revisionA);
        assertEquals(revisionC, registry.revision(c.id()));

        long beforeNoop = registry.revision();
        registry.update(c.id(), portal -> portal);
        registry.update(c.id(), portal -> PortalDefinition.builder(portal).build());
        assertEquals(beforeNoop, registry.revision());

        registry.update(c.id(), portal -> portal.moved(0, 1, 0));
        assertTrue(registry.revision(c.id()) > revisionC);

        long beforeMirror = registry.revision();
        registry.mirror(c.id(), QuarterTurn.DEGREES_0);
        assertTrue(registry.revision() > beforeMirror);

        long beforeUnlink = registry.revision();
        registry.unlink(b.id());
        assertTrue(registry.revision() > beforeUnlink);

        long beforeRemove = registry.revision();
        registry.remove(a.id());
        assertTrue(registry.revision() > beforeRemove);
        assertEquals(0L, registry.revision(a.id()));
    }

    @Test
    void listenersHearEventsInMutationAndRegistrationOrder() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        List<String> events = new ArrayList<String>();
        Recorder first = new Recorder("first", events);
        Recorder second = new Recorder("second", events);
        registry.addListener(first);
        registry.addListener(second);
        PortalDefinition a = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3).withMetadata("name", "a");
        PortalDefinition b = PortalFixtures.wall(Face.S, 10, 64, 10, 3, 3).withMetadata("name", "b");

        registry.add(OVERWORLD, a);
        registry.add(OVERWORLD, b);
        registry.link(a.id(), b.id());
        registry.add(NETHER, registry.get(b.id()));
        registry.remove(a.id());
        registry.removeListener(first);
        registry.update(b.id(), portal -> portal.moved(1, 0, 0));
        registry.update(b.id(), portal -> portal);

        assertEquals(List.of(
            "first added overworld a", "second added overworld a",
            "first added overworld b", "second added overworld b",
            "first changed overworld a", "second changed overworld a",
            "first changed overworld b", "second changed overworld b",
            "first removed overworld b", "second removed overworld b",
            "first added nether b", "second added nether b",
            "first removed overworld a", "second removed overworld a",
            "second changed nether b"), events);
    }

    @Test
    void listenersSeePreviousAndCurrentDefinitions() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition a = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);
        registry.add(OVERWORLD, a);
        List<PortalDefinition> seen = new ArrayList<PortalDefinition>();
        registry.addListener(new PortalRegistry.Listener<String>() {
            @Override
            public void added(String world, PortalDefinition portal) {
            }

            @Override
            public void removed(String world, PortalDefinition portal) {
            }

            @Override
            public void changed(String world, PortalDefinition previous, PortalDefinition current) {
                seen.add(previous);
                seen.add(current);
            }
        });

        PortalDefinition current = registry.update(a.id(), portal -> portal.scaled(2.0D));

        assertEquals(List.of(a, current), seen);
        assertEquals(registry.get(a.id()), current);
    }

    private record Recorder(String name, List<String> events) implements PortalRegistry.Listener<String> {
        @Override
        public void added(String world, PortalDefinition portal) {
            events.add(name + " added " + world + " " + portal.metadata("name"));
        }

        @Override
        public void removed(String world, PortalDefinition portal) {
            events.add(name + " removed " + world + " " + portal.metadata("name"));
        }

        @Override
        public void changed(String world, PortalDefinition previous, PortalDefinition current) {
            events.add(name + " changed " + world + " " + current.metadata("name"));
        }
    }
}
