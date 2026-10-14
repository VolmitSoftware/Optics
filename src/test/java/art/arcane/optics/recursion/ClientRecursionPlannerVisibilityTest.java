package art.arcane.optics.recursion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import art.arcane.optics.aperture.ApertureDescriptor;
import art.arcane.optics.frame.AxisPermutation;
import art.arcane.optics.frame.OpticTransform;
import art.arcane.optics.math.Face;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.portal.PortalDefinition;
import art.arcane.optics.shape.FitMode;
import art.arcane.optics.shape.ShapeDescriptor;
import art.arcane.optics.shape.Shapes;

final class ClientRecursionPlannerVisibilityTest {
    @Test
    void keepsVisibleChildInteriorsWhenTheirCenterAndCornersMissTheParentCone() {
        PortalDefinition rootPortal = portal(0, 0, 0, 1, 1);
        PortalDefinition childPortal = portal(-10, -10, 5, 15, 21);
        Vec3d eye = new Vec3d(0.5D, 0.5D, -3.0D);
        Vec3d interior = new Vec3d(0.5D, 0.5D, 5.5D);
        for (int index = 0; index < 48; index++) {
            OpticTransform transform = OpticTransform.of(AxisPermutation.ofIndex(index), 10.0D, 20.0D, 30.0D);
            ApertureDescriptor child = descriptor(childPortal.transformed(transform), false, 0);
            ApertureDescriptor root = descriptor(rootPortal.transformed(transform), false, 0).withNested(List.of(child));
            Vec3d mappedEye = transform.point(eye);
            List<ClientRecursionPlanner.NestedCone> cones = new ClientRecursionPlanner(2).plan(root, mappedEye.x(), mappedEye.y(), mappedEye.z());
            assertEquals(1, cones.size(), transform.toString());
            Vec3d mappedInterior = transform.point(interior);
            assertTrue(cones.getFirst().visible(mappedInterior.x(), mappedInterior.y(), mappedInterior.z()));
        }
    }

    @Test
    void clippingAtLargeWorldCoordinatesMatchesTheSameLocalGeometry() {
        ApertureDescriptor child = descriptor(portal(29_999_980, -10, -29_999_990, 15, 21), false, 0);
        ApertureDescriptor root = descriptor(portal(29_999_990, 0, -29_999_995, 1, 1), false, 0).withNested(List.of(child));
        List<ClientRecursionPlanner.NestedCone> cones = new ClientRecursionPlanner(2).plan(root,
            29_999_990.5D, 0.5D, -29_999_998.0D);
        assertEquals(1, cones.size());
        assertTrue(cones.getFirst().visible(29_999_990.5D, 0.5D, -29_999_989.5D));
    }

    @Test
    void rejectsDisjointAperturesAndAperturesEntirelyBehindClosedMaskCells() {
        ApertureDescriptor root = descriptor(portal(0, 0, 0, 1, 1), false, 0);
        ApertureDescriptor disjoint = descriptor(portal(10, 10, 5, 2, 2), false, 0);
        assertTrue(new ClientRecursionPlanner(2).plan(root.withNested(List.of(disjoint)), 0.5D, 0.5D, -3.0D).isEmpty());

        ShapeDescriptor ring = ShapeDescriptor.of(Shapes.rectangle(2.0D, 2.0D)
            .subtract(Shapes.rectangle(0.6D, 0.6D)), FitMode.STRETCH);
        PortalDefinition ringPortal = portal(0, 0, 0, 3, 3).withShape(ring);
        assertFalse(ringPortal.containsCell(1, 1, 0));
        ApertureDescriptor hole = descriptor(ringPortal, false, 0);
        ApertureDescriptor hidden = descriptor(portal(1, 1, 1, 1, 1), false, 0);
        assertTrue(new ClientRecursionPlanner(2).plan(hole.withNested(List.of(hidden)), 1.5D, 1.5D, -3.0D).isEmpty());
    }

    @Test
    void retainedConesContinueToMaskClosedParentCells() {
        ShapeDescriptor ring = ShapeDescriptor.of(Shapes.rectangle(2.0D, 2.0D)
            .subtract(Shapes.rectangle(0.6D, 0.6D)), FitMode.STRETCH);
        ApertureDescriptor child = descriptor(portal(-10, -10, 5, 15, 21), false, 0);
        ApertureDescriptor root = descriptor(portal(0, 0, 0, 3, 3).withShape(ring), false, 0).withNested(List.of(child));
        List<ClientRecursionPlanner.NestedCone> cones = new ClientRecursionPlanner(2).plan(root, 1.5D, 1.5D, -3.0D);
        assertEquals(1, cones.size());
        assertFalse(cones.getFirst().visible(1.5D, 1.5D, 5.5D));
        assertTrue(cones.getFirst().visible(-1.0D, 1.5D, 5.5D));
    }

    @Test
    void reflectedAndQuarterTurnedWindowsRetainVisibleInteriors() {
        ApertureDescriptor child = descriptor(PortalDefinition.builder().facing(Face.S).origin(-10, -10, -5).size(15, 21).build(), false, 0);
        for (int turn = 0; turn < 4; turn++) {
            ApertureDescriptor root = descriptor(portal(0, 0, 0, 1, 1), true, turn).withNested(List.of(child));
            List<ClientRecursionPlanner.NestedCone> cones = new ClientRecursionPlanner(2).plan(root, 0.5D, 0.5D, -3.0D);
            assertEquals(1, cones.size(), "mirror turn " + turn);
            ClientRecursionPlanner.NestedCone cone = cones.getFirst();
            Vec3d interior = cone.transform().point(new Vec3d(0.5D, 0.5D, -4.5D));
            assertTrue(cone.visible(interior.x(), interior.y(), interior.z()));
            assertEquals(List.of(root), cone.reflections());
        }
    }

    @Test
    void deeperWindowsKeepTheEntireAncestorVisibilityChain() {
        ApertureDescriptor grandchild = descriptor(portal(-10, -10, 10, 15, 21), false, 0);
        ApertureDescriptor child = descriptor(portal(-10, -10, 5, 15, 21), false, 0).withNested(List.of(grandchild));
        ApertureDescriptor root = descriptor(portal(0, 0, 0, 1, 1), false, 0).withNested(List.of(child));
        List<ClientRecursionPlanner.NestedCone> cones = new ClientRecursionPlanner(2).plan(root, 0.5D, 0.5D, -3.0D);
        assertEquals(2, cones.size());
        ClientRecursionPlanner.NestedCone deepest = cones.get(1);
        assertEquals(2, deepest.depth());
        assertEquals(List.of(root, child), deepest.ancestors());
        assertTrue(deepest.visible(0.5D, 0.5D, 10.5D));
        assertFalse(deepest.visible(10.0D, 0.5D, 10.5D));
    }

    private static PortalDefinition portal(int x, int y, int z, int width, int height) {
        return PortalDefinition.builder().facing(Face.N).origin(x, y, z).size(width, height).build();
    }

    private static ApertureDescriptor descriptor(PortalDefinition portal, boolean mirror, int mirrorTurns) {
        ApertureDescriptor surface = ApertureDescriptor.fromPortal(portal.source(true, 32)).orElseThrow();
        return new ApertureDescriptor(surface.originX(), surface.originY(), surface.originZ(), surface.facing(), true,
            surface.quarterTurns() | (mirrorTurns << 2), mirror, surface.apertureWidth(), surface.apertureHeight(), surface.apertureMask(),
            surface.shape(), 0.0F, 0.0F, 1.0F, 32, 2, 0, 0, 0, 0, 0, 0, surface.planeOffset(), 0, 0L, List.of());
    }
}
