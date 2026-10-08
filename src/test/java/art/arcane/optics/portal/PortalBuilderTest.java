package art.arcane.optics.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import art.arcane.optics.aperture.ApertureDescriptor;
import art.arcane.optics.crossing.MomentumRule;
import art.arcane.optics.crossing.OrientationRule;
import art.arcane.optics.crossing.ScaleRule;
import art.arcane.optics.frame.Frame;
import art.arcane.optics.frame.QuarterTurn;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.shape.ShapeDescriptor;

final class PortalBuilderTest {
    @Test
    void defaultsAreASingleUnlinkedFullCell() {
        PortalDefinition portal = PortalDefinition.builder().facing(Face.N).origin(4, 64, -2).build();

        assertNotNull(portal.id());
        assertEquals(Frame.canonical(Face.N), portal.frame());
        assertEquals(1, portal.columns());
        assertEquals(1, portal.rows());
        assertEquals(0.0D, portal.planeOffset(), 0.0D);
        assertEquals(ShapeDescriptor.FULL, portal.shape());
        assertNull(portal.link());
        assertEquals(Map.of(), portal.metadata());
        assertEquals(new Vec3d(4.5D, 64.5D, -1.5D), portal.origin());
        PortalFixtures.assertConsistent(portal);
    }

    @Test
    void everyBuildDrawsAFreshIdUnlessOneIsGiven() {
        UUID id = UUID.randomUUID();
        PortalBuilder builder = PortalDefinition.builder().facing(Face.E).origin(0, 0, 0);

        assertNotEquals(builder.build().id(), builder.build().id());
        assertEquals(id, builder.id(id).build().id());
    }

    @Test
    void frameShortcutsMatchTheFrameFactories() {
        assertEquals(Frame.fromNormalUp(Face.U, Face.E), PortalDefinition.builder().frame(Face.U, Face.E).origin(0, 0, 0).build().frame());
        assertEquals(Frame.canonical(Face.D), PortalDefinition.builder().facing(Face.D).origin(0, 0, 0).build().frame());
        Frame turned = Frame.canonical(Face.S).rotateClockwise();
        assertEquals(turned, PortalDefinition.builder().frame(turned).origin(0, 0, 0).build().frame());
    }

    @Test
    void centerResolvesAgainstTheSizeAtBuild() {
        PortalDefinition odd = PortalDefinition.builder().center(new Vec3d(0.5D, 65.5D, 0.5D)).facing(Face.N).size(3, 3).build();
        assertEquals(-1, odd.originX());
        assertEquals(64, odd.originY());
        assertEquals(0, odd.originZ());
        assertEquals(new Vec3d(0.5D, 65.5D, 0.5D), odd.origin());

        PortalDefinition even = PortalDefinition.builder().facing(Face.N).size(2, 2).center(new Vec3d(1.0D, 65.0D, 0.5D)).build();
        assertEquals(0, even.originX());
        assertEquals(64, even.originY());
        assertEquals(new Vec3d(1.0D, 65.0D, 0.5D), even.origin());

        PortalDefinition floor = PortalDefinition.builder().facing(Face.U).size(3, 5).center(new Vec3d(0.5D, 64.5D, 0.5D)).build();
        assertEquals(-1, floor.originX());
        assertEquals(64, floor.originY());
        assertEquals(-2, floor.originZ());
        assertEquals(new Vec3d(0.5D, 64.5D, 0.5D), floor.origin());
    }

    @Test
    void centerHonoursThePlaneOffsetAlongTheNormal() {
        PortalDefinition south = PortalDefinition.builder().facing(Face.S).planeOffset(0.25D).center(new Vec3d(0.5D, 65.5D, 10.75D)).size(3, 3).build();
        assertEquals(10, south.originZ());
        assertEquals(10.75D, south.origin().z(), 1.0E-12D);

        PortalDefinition north = PortalDefinition.builder().facing(Face.N).planeOffset(0.25D).center(new Vec3d(0.5D, 65.5D, 10.25D)).size(3, 3).build();
        assertEquals(10, north.originZ());
        assertEquals(10.25D, north.origin().z(), 1.0E-12D);
    }

    @Test
    void theLastPlacementCallWins() {
        PortalDefinition centered = PortalDefinition.builder().facing(Face.N).origin(100, 100, 100).center(new Vec3d(0.5D, 0.5D, 0.5D)).build();
        assertEquals(0, centered.originX());
        PortalDefinition placed = PortalDefinition.builder().facing(Face.N).center(new Vec3d(0.5D, 0.5D, 0.5D)).origin(7, 8, 9).build();
        assertEquals(7, placed.originX());
        assertEquals(8, placed.originY());
        assertEquals(9, placed.originZ());
    }

    @Test
    void missingFrameOrPlacementIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PortalDefinition.builder().origin(0, 0, 0).build());
        assertThrows(IllegalArgumentException.class, () -> PortalDefinition.builder().facing(Face.N).build());
    }

    @Test
    void sizesOutsideTheDescriptorLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PortalDefinition.builder().facing(Face.N).origin(0, 0, 0).size(0, 3).build());
        assertThrows(IllegalArgumentException.class, () -> PortalDefinition.builder().facing(Face.N).origin(0, 0, 0).size(3, -1).build());
        assertThrows(IllegalArgumentException.class,
            () -> PortalDefinition.builder().facing(Face.N).origin(0, 0, 0).size(ApertureDescriptor.MAX_APERTURE_EDGE + 1, 1).build());
        assertThrows(IllegalArgumentException.class, () -> PortalDefinition.builder().facing(Face.N).origin(0, 0, 0).size(2_000, 2_000).build());
        assertEquals(ApertureDescriptor.MAX_APERTURE_EDGE,
            PortalDefinition.builder().facing(Face.N).origin(0, 0, 0).size(ApertureDescriptor.MAX_APERTURE_EDGE, 1).build().columns());
    }

    @Test
    void invalidOffsetsSelfLinksAndEmptyShapesAreRejected() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> PortalDefinition.builder().facing(Face.N).origin(0, 0, 0).planeOffset(Double.NaN).build());
        assertThrows(IllegalArgumentException.class, () -> PortalDefinition.builder().id(id).facing(Face.N).origin(0, 0, 0).linkTo(id).build());
        assertThrows(IllegalArgumentException.class,
            () -> PortalDefinition.builder().facing(Face.N).origin(0, 0, 0).size(3, 3).shape("circle(radius=0.05)").build());
        assertThrows(IllegalArgumentException.class, () -> PortalDefinition.builder().facing(Face.N).origin(0, 0, 0).shape("circle(").build());
    }

    @Test
    void shapeTextParsesThroughTheDescriptorGrammar() {
        PortalDefinition portal = PortalDefinition.builder().facing(Face.N).origin(0, 64, 0).size(7, 7).shape("flower(petals=6)").build();

        assertEquals(ShapeDescriptor.parse("flower(petals=6)"), portal.shape());
        assertEquals(ShapeDescriptor.parse("circle"),
            PortalDefinition.builder().facing(Face.N).origin(0, 64, 0).size(3, 3).shape(ShapeDescriptor.parse("circle")).build().shape());
    }

    @Test
    void linkShortcutsBuildTheExpectedLinks() {
        UUID target = UUID.randomUUID();
        PortalDefinition linked = PortalDefinition.builder().facing(Face.N).origin(0, 0, 0).linkTo(target).build();
        assertEquals(PortalLink.to(target), linked.link());

        PortalDefinition mirror = PortalDefinition.builder().facing(Face.N).origin(0, 0, 0).mirror(QuarterTurn.DEGREES_90).build();
        assertTrue(mirror.link().isMirror());
        assertEquals(QuarterTurn.DEGREES_90, mirror.link().mirrorTurns());

        PortalLink custom = PortalLink.to(target).withScale(ScaleRule.motion());
        assertEquals(custom, PortalDefinition.builder().facing(Face.N).origin(0, 0, 0).link(custom).build().link());
    }

    @Test
    void metadataIsKeptInInsertionOrderAndImmutable() {
        PortalDefinition portal = PortalDefinition.builder().facing(Face.N).origin(0, 0, 0).metadata("name", "Spawn").metadata("color", "blue").build();

        assertEquals("Spawn", portal.metadata("name"));
        assertNull(portal.metadata("missing"));
        assertEquals(List.of("name", "color"), List.copyOf(portal.metadata().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> portal.metadata().put("x", "y"));
    }

    @Test
    void builderFromADefinitionReproducesIt() {
        PortalDefinition portal = PortalDefinition.builder().frame(Face.U, Face.W).origin(-20, 70, 33).size(5, 3).planeOffset(-0.25D)
            .shape("star").linkTo(UUID.randomUUID()).metadata("k", "v").build();

        assertEquals(portal, PortalDefinition.builder(portal).build());
        assertEquals(portal.hashCode(), PortalDefinition.builder(portal).build().hashCode());
        assertFalse(portal.equals(PortalDefinition.builder(portal).id(UUID.randomUUID()).build()));
    }

    @Test
    void linkDefaultsFollowTheLockedRules() {
        UUID target = UUID.randomUUID();
        PortalLink link = PortalLink.to(target);
        assertEquals(target, link.target());
        assertFalse(link.isMirror());
        assertEquals(QuarterTurn.DEGREES_0, link.mirrorTurns());
        assertEquals(OrientationRule.FRAME, link.orientation());
        assertEquals(MomentumRule.Mode.PRESERVE, link.momentum().mode());
        assertEquals(ScaleRule.OFF, link.scale());
        assertEquals(ScaleRule.ratio(0.5D, 2.0D), link.withScale(ScaleRule.ratio(0.5D, 2.0D)).scale());

        PortalLink mirror = PortalLink.mirror(QuarterTurn.DEGREES_180);
        assertNull(mirror.target());
        assertTrue(mirror.isMirror());
        assertEquals(QuarterTurn.DEGREES_180, mirror.mirrorTurns());

        assertThrows(NullPointerException.class, () -> PortalLink.to(null));
        assertThrows(IllegalArgumentException.class, () -> new PortalLink(target, true, QuarterTurn.DEGREES_0, null, null, null));
    }
}
