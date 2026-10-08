package art.arcane.optics.crossing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import art.arcane.optics.aperture.SizeRatio;

final class ScaleRuleTest {
    private static final SizeRatio TRIPLE = new SizeRatio(3.0D, true);

    @Test
    void offKeepsTheRigidMappingAndTheEntitySize() {
        assertEquals(ScaleRule.Mode.OFF, ScaleRule.OFF.mode());
        assertEquals(ScaleRule.ATTRIBUTE_MIN, ScaleRule.OFF.min(), 0.0D);
        assertEquals(ScaleRule.ATTRIBUTE_MAX, ScaleRule.OFF.max(), 0.0D);
        assertEquals(1.0D, ScaleRule.OFF.travelScale(TRIPLE), 0.0D);
        assertEquals(1.7D, ScaleRule.OFF.entityFactor(1.7D, TRIPLE), 0.0D);
        assertFalse(ScaleRule.OFF.changesEntity());
    }

    @Test
    void motionScalesTravelButNotTheEntity() {
        ScaleRule motion = ScaleRule.motion();
        assertEquals(ScaleRule.Mode.MOTION, motion.mode());
        assertEquals(3.0D, motion.travelScale(TRIPLE), 0.0D);
        assertEquals(1.0D / 3.0D, motion.travelScale(TRIPLE.inverse()), 0.0D);
        assertEquals(1.0D, motion.entityFactor(1.0D, TRIPLE), 0.0D);
        assertFalse(motion.changesEntity());
    }

    @Test
    void ratioCompoundsTheEntityFactorWithinItsClamps() {
        ScaleRule ratio = ScaleRule.ratio(0.25D, 4.0D);
        assertEquals(ScaleRule.Mode.RATIO, ratio.mode());
        assertTrue(ratio.changesEntity());
        assertEquals(3.0D, ratio.travelScale(TRIPLE), 0.0D);
        double grown = ratio.entityFactor(1.0D, TRIPLE);
        assertEquals(3.0D, grown, 0.0D);
        assertEquals(1.0D, ratio.entityFactor(grown, TRIPLE.inverse()), 1.0E-12D);
        assertEquals(4.0D, ratio.entityFactor(grown, TRIPLE), 0.0D);
        assertEquals(0.25D, ratio.entityFactor(0.5D, TRIPLE.inverse()), 0.0D);
        double looped = 1.0D;
        for (int pass = 0; pass < 10; pass++) {
            looped = ratio.entityFactor(looped, TRIPLE);
        }
        assertEquals(4.0D, looped, 0.0D);
    }

    @Test
    void boundsClampIntoTheAttributeRangeAndMustBeOrdered() {
        ScaleRule wide = ScaleRule.ratio(0.001D, 100.0D);
        assertEquals(ScaleRule.ATTRIBUTE_MIN, wide.min(), 0.0D);
        assertEquals(ScaleRule.ATTRIBUTE_MAX, wide.max(), 0.0D);
        assertEquals(new ScaleRule(ScaleRule.Mode.RATIO, ScaleRule.ATTRIBUTE_MIN, ScaleRule.ATTRIBUTE_MAX), wide);
        assertThrows(IllegalArgumentException.class, () -> ScaleRule.ratio(4.0D, 0.25D));
        assertThrows(IllegalArgumentException.class, () -> ScaleRule.ratio(Double.NaN, 2.0D));
        assertThrows(IllegalArgumentException.class, () -> ScaleRule.ratio(0.5D, Double.POSITIVE_INFINITY));
        assertThrows(NullPointerException.class, () -> new ScaleRule(null, 0.5D, 2.0D));
    }
}
