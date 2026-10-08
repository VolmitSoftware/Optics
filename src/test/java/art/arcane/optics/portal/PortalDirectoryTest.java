package art.arcane.optics.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import art.arcane.optics.aperture.EndpointDirectory;
import art.arcane.optics.crossing.ScaleRule;
import art.arcane.optics.frame.QuarterTurn;
import art.arcane.optics.math.Face;
import art.arcane.optics.recursion.RecursiveEndpoints;

final class PortalDirectoryTest {
    private static final String WORLD = "world";

    @Test
    void directoryExposesTheRegistryAsEndpoints() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition a = PortalFixtures.wall(Face.N, 0, 64, 0, 2, 2);
        PortalDefinition b = PortalFixtures.wall(Face.S, 0, 64, 4, 2, 2);
        registry.add(WORLD, a);
        registry.add("nether", b);
        registry.link(a.id(), b.id());
        EndpointDirectory<String, PortalDefinition> directory = registry.directory();
        PortalDefinition linkedA = registry.get(a.id());
        PortalDefinition linkedB = registry.get(b.id());

        assertSame(directory, registry.directory());
        assertEquals(registry.all(), directory.endpoints());
        assertEquals(WORLD, directory.world(linkedA));
        assertEquals("nether", directory.world(linkedB));
        assertSame(linkedA.aperture(), directory.aperture(linkedA));
        assertEquals(linkedA.area().min(), directory.view(linkedA).min());
        assertEquals(linkedA.area().max(), directory.view(linkedA).max());
        assertTrue(directory.eligible(linkedA));
        assertFalse(directory.mirror(linkedA));
        assertEquals(QuarterTurn.DEGREES_0, directory.mirrorTurns(linkedA));
        assertSame(linkedB, directory.destination(linkedA));
        assertEquals(1.0D, directory.travelScale(linkedA), 0.0D);
    }

    @Test
    void eligibilityNeedsALiveTargetOrAMirror() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition a = PortalFixtures.wall(Face.N, 0, 64, 0, 2, 2);
        PortalDefinition b = PortalFixtures.wall(Face.S, 0, 64, 4, 2, 2);
        PortalDefinition loose = PortalFixtures.wall(Face.E, 9, 64, 9, 2, 2);
        PortalDefinition mirror = PortalFixtures.wall(Face.W, 20, 64, 20, 3, 3).linked(PortalLink.mirror(QuarterTurn.DEGREES_90));
        PortalDefinition dangling = PortalFixtures.wall(Face.W, 30, 64, 30, 3, 3).linked(PortalLink.to(UUID.randomUUID()));
        registry.add(WORLD, a);
        registry.add(WORLD, b);
        registry.add(WORLD, loose);
        registry.add(WORLD, mirror);
        registry.add(WORLD, dangling);
        registry.linkOneWay(a.id(), b.id());
        PortalDirectory<String> directory = registry.directory();

        assertTrue(directory.eligible(registry.get(a.id())));
        assertFalse(directory.eligible(loose));
        assertFalse(directory.eligible(dangling));
        assertNull(directory.destination(dangling));
        assertTrue(directory.eligible(mirror));
        assertTrue(directory.mirror(mirror));
        assertEquals(QuarterTurn.DEGREES_90, directory.mirrorTurns(mirror));
        assertSame(mirror, directory.destination(mirror));
        assertEquals(1.0D, directory.travelScale(mirror), 0.0D);

        registry.remove(b.id());
        assertFalse(directory.eligible(registry.get(a.id())));
        assertEquals(1.0D, directory.travelScale(registry.get(a.id())), 0.0D);
    }

    @Test
    void travelScaleFollowsTheLinkRule() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition small = PortalFixtures.wall(Face.N, 0, 64, 0, 3, 3);
        PortalDefinition large = PortalFixtures.wall(Face.S, 50, 64, 50, 9, 9);
        registry.add(WORLD, small);
        registry.add(WORLD, large);
        registry.link(small.id(), large.id());
        registry.update(small.id(), portal -> portal.linked(portal.link().withScale(ScaleRule.ratio(0.25D, 4.0D))));
        registry.update(large.id(), portal -> portal.linked(portal.link().withScale(ScaleRule.motion())));
        PortalDirectory<String> directory = registry.directory();

        assertEquals(3.0D, directory.travelScale(registry.get(small.id())), 0.0D);
        assertEquals(1.0D / 3.0D, directory.travelScale(registry.get(large.id())), 1.0E-15D);
    }

    @Test
    void recursiveEndpointsTraverseALinkedPair() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition a = PortalFixtures.wall(Face.N, 0, 64, 0, 2, 2);
        PortalDefinition b = PortalFixtures.wall(Face.S, 0, 64, 4, 2, 2);
        registry.add(WORLD, a);
        registry.add(WORLD, b);
        registry.link(a.id(), b.id());
        PortalDefinition linkedA = registry.get(a.id());
        PortalDefinition linkedB = registry.get(b.id());
        RecursiveEndpoints<String, PortalDefinition> recursive = new RecursiveEndpoints<String, PortalDefinition>(registry.directory(),
            () -> new RecursiveEndpoints.Options(0.75D, 64.0D));
        recursive.revalidate();

        RecursiveEndpoints<String, PortalDefinition>.Index index = recursive.indexFor(WORLD, 1.0D, 65.0D, -5.0D, null);

        assertEquals(2, index.paths().size());
        RecursiveEndpoints<String, PortalDefinition>.Candidate candidate = candidate(index, linkedA.id());
        assertNotNull(candidate);
        assertTrue(candidate.traversable);
        assertSame(linkedB, candidate.nestedDestination);
        assertEquals(WORLD, candidate.nestedWorld);
        assertEquals(linkedA.toward(linkedB, true).rigid().inverse(), candidate.transform());
        assertTrue(recursive.reaches(WORLD, linkedA, -2.0D, 63.0D, 3.0D, 4.0D, 67.0D, 5.0D));

        registry.unlink(a.id());
        recursive.revalidate();
        assertTrue(recursive.indexFor(WORLD, 1.0D, 65.0D, -5.0D, null).isEmpty());
    }

    @Test
    void recursiveEndpointsReflectThroughMirrors() {
        PortalRegistry<String> registry = new PortalRegistry<String>();
        PortalDefinition mirror = PortalFixtures.wall(Face.U, 0, 64, 0, 2, 2).linked(PortalLink.mirror(QuarterTurn.DEGREES_90));
        registry.add(WORLD, mirror);
        RecursiveEndpoints<String, PortalDefinition> recursive = new RecursiveEndpoints<String, PortalDefinition>(registry.directory(),
            () -> new RecursiveEndpoints.Options(0.75D, 64.0D));

        RecursiveEndpoints<String, PortalDefinition>.Candidate candidate = candidate(recursive.indexFor(WORLD, 1.0D, 70.0D, 1.0D, null),
            mirror.id());

        assertNotNull(candidate);
        assertTrue(candidate.traversable);
        assertSame(mirror, candidate.nestedDestination);
        assertEquals(mirror.toward(mirror, true).rigid().inverse(), candidate.transform());
    }

    private static RecursiveEndpoints<String, PortalDefinition>.Candidate candidate(RecursiveEndpoints<String, PortalDefinition>.Index index,
                                                                                   UUID id) {
        for (RecursiveEndpoints<String, PortalDefinition>.Candidate candidate : index.paths()) {
            if (id.equals(candidate.portalId)) {
                return candidate;
            }
        }
        return null;
    }
}
