package art.arcane.optics.math;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

final class RgbaTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void constantsHaveTheExpectedChannels() {
        assertEquals(new Rgba(1.0F, 1.0F, 1.0F, 1.0F), Rgba.WHITE);
        assertEquals(new Rgba(0.0F, 0.0F, 0.0F, 1.0F), Rgba.BLACK);
        assertEquals(new Rgba(0.0F, 0.0F, 0.0F, 0.0F), Rgba.TRANSPARENT);
        assertEquals(0xFFFFFFFF, Rgba.WHITE.argb());
        assertEquals(0xFF000000, Rgba.BLACK.argb());
        assertEquals(0x00000000, Rgba.TRANSPARENT.argb());
    }

    @Test
    void argbRoundTripsEveryPackedValue() {
        SplittableRandom random = new SplittableRandom(9L);
        for (int sample = 0; sample < 10_000; sample++) {
            int argb = random.nextInt();
            assertEquals(argb, Rgba.argb(argb).argb());
            assertEquals(argb & 0xFFFFFF, Rgba.argb(argb).rgb());
        }
        Rgba orange = Rgba.argb(0x80FF8000);
        assertEquals(1.0F, orange.red(), 0.0F);
        assertEquals(128.0F / 255.0F, orange.green(), 0.0F);
        assertEquals(0.0F, orange.blue(), 0.0F);
        assertEquals(128.0F / 255.0F, orange.alpha(), 0.0F);
    }

    @Test
    void rgbIsOpaque() {
        Rgba color = Rgba.rgb(0x12345678);
        assertEquals(1.0F, color.alpha(), 0.0F);
        assertEquals(0xFF345678, color.argb());
        assertEquals(0x345678, color.rgb());
    }

    @Test
    void packingClampsOutOfRangeChannels() {
        assertEquals(0xFFFF0000, new Rgba(2.0F, -1.0F, 0.0F, 7.0F).argb());
        assertEquals(new Rgba(1.0F, 0.0F, 0.5F, 1.0F), new Rgba(2.0F, -1.0F, 0.5F, 7.0F).clamped());
    }

    @Test
    void hsvMapsThePrimaryHues() {
        assertColor(new Rgba(1.0F, 0.0F, 0.0F, 1.0F), Rgba.hsv(0.0F, 1.0F, 1.0F, 1.0F));
        assertColor(new Rgba(1.0F, 1.0F, 0.0F, 1.0F), Rgba.hsv(60.0F, 1.0F, 1.0F, 1.0F));
        assertColor(new Rgba(0.0F, 1.0F, 0.0F, 1.0F), Rgba.hsv(120.0F, 1.0F, 1.0F, 1.0F));
        assertColor(new Rgba(0.0F, 0.0F, 1.0F, 0.5F), Rgba.hsv(240.0F, 1.0F, 1.0F, 0.5F));
        assertColor(new Rgba(1.0F, 0.0F, 0.0F, 1.0F), Rgba.hsv(360.0F, 1.0F, 1.0F, 1.0F));
        assertColor(new Rgba(1.0F, 0.0F, 1.0F, 1.0F), Rgba.hsv(-60.0F, 1.0F, 1.0F, 1.0F));
        assertColor(new Rgba(0.5F, 0.5F, 0.5F, 1.0F), Rgba.hsv(200.0F, 0.0F, 0.5F, 1.0F));
        Rgba magenta = new Rgba(1.0F, 0.0F, 1.0F, 1.0F);
        assertEquals(300.0F, magenta.hue(), EPSILON);
        assertEquals(1.0F, magenta.saturation(), EPSILON);
        assertEquals(1.0F, magenta.value(), EPSILON);
        assertEquals(0.0F, new Rgba(0.3F, 0.3F, 0.3F, 1.0F).hue(), 0.0F);
        assertEquals(0.0F, new Rgba(0.3F, 0.3F, 0.3F, 1.0F).saturation(), 0.0F);
        assertEquals(0.0F, Rgba.BLACK.saturation(), 0.0F);
    }

    @Test
    void hsvRoundTripsRandomColors() {
        SplittableRandom random = new SplittableRandom(13L);
        for (int sample = 0; sample < 10_000; sample++) {
            Rgba color = new Rgba((float) random.nextDouble(), (float) random.nextDouble(), (float) random.nextDouble(), (float) random.nextDouble());
            float hue = color.hue();
            assertEquals(true, hue >= 0.0F && hue < 360.0F, "hue " + hue);
            assertColor(color, Rgba.hsv(hue, color.saturation(), color.value(), color.alpha()));
        }
    }

    @Test
    void lerpHitsEndpointsInBothSpaces() {
        Rgba from = new Rgba(1.0F, 0.0F, 0.0F, 1.0F);
        Rgba to = new Rgba(0.0F, 0.0F, 1.0F, 0.0F);
        assertEquals(from, from.lerp(to, 0.0D));
        assertEquals(to, from.lerp(to, 1.0D));
        assertColor(new Rgba(0.5F, 0.0F, 0.5F, 0.5F), from.lerp(to, 0.5D));
        assertColor(from, from.lerpHsv(to, 0.0D));
        assertColor(to, from.lerpHsv(to, 1.0D));
        Rgba middle = from.lerpHsv(to, 0.5D);
        assertEquals(300.0F, middle.hue(), 1.0E-3F);
        assertEquals(1.0F, middle.saturation(), EPSILON);
        assertEquals(1.0F, middle.value(), EPSILON);
        assertEquals(0.5F, middle.alpha(), EPSILON);
    }

    @Test
    void lerpHsvKeepsTheHueOfTheSaturatedEndWhenTheOtherIsGray() {
        Rgba green = Rgba.hsv(120.0F, 1.0F, 1.0F, 1.0F);
        Rgba middle = Rgba.WHITE.lerpHsv(green, 0.5D);
        assertEquals(120.0F, middle.hue(), 1.0E-3F);
        assertEquals(0.5F, middle.saturation(), EPSILON);
    }

    @Test
    void alphaHelpersWorkOnTheAlphaChannelOnly() {
        Rgba color = new Rgba(0.8F, 0.4F, 0.2F, 1.0F);
        assertEquals(new Rgba(0.8F, 0.4F, 0.2F, 0.25F), color.withAlpha(0.25F));
        assertColor(new Rgba(0.4F, 0.2F, 0.1F, 0.5F), color.withAlpha(0.5F).premultiplied());
    }

    private static void assertColor(Rgba expected, Rgba actual) {
        assertEquals(expected.red(), actual.red(), EPSILON, "red of " + actual);
        assertEquals(expected.green(), actual.green(), EPSILON, "green of " + actual);
        assertEquals(expected.blue(), actual.blue(), EPSILON, "blue of " + actual);
        assertEquals(expected.alpha(), actual.alpha(), EPSILON, "alpha of " + actual);
    }
}
