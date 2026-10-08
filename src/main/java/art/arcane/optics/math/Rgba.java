package art.arcane.optics.math;

public record Rgba(float red, float green, float blue, float alpha) {
    public static final Rgba WHITE = new Rgba(1.0F, 1.0F, 1.0F, 1.0F);
    public static final Rgba BLACK = new Rgba(0.0F, 0.0F, 0.0F, 1.0F);
    public static final Rgba TRANSPARENT = new Rgba(0.0F, 0.0F, 0.0F, 0.0F);
    private static final float CHANNEL_MAX = 255.0F;
    private static final double FULL_TURN = 360.0D;
    private static final double SECTOR = 60.0D;

    public static Rgba argb(int argb) {
        return new Rgba(channel(argb >>> 16), channel(argb >>> 8), channel(argb), channel(argb >>> 24));
    }

    public static Rgba rgb(int rgb) {
        return new Rgba(channel(rgb >>> 16), channel(rgb >>> 8), channel(rgb), 1.0F);
    }

    public static Rgba hsv(float hue, float saturation, float value, float alpha) {
        double wrapped = hue % FULL_TURN;
        double sectorPosition = (wrapped < 0.0D ? wrapped + FULL_TURN : wrapped) / SECTOR;
        int sector = (int) Math.floor(sectorPosition);
        double fraction = sectorPosition - sector;
        float low = (float) (value * (1.0D - saturation));
        float falling = (float) (value * (1.0D - saturation * fraction));
        float rising = (float) (value * (1.0D - saturation * (1.0D - fraction)));
        return switch (sector % 6) {
            case 1 -> new Rgba(falling, value, low, alpha);
            case 2 -> new Rgba(low, value, rising, alpha);
            case 3 -> new Rgba(low, falling, value, alpha);
            case 4 -> new Rgba(rising, low, value, alpha);
            case 5 -> new Rgba(value, low, falling, alpha);
            default -> new Rgba(value, rising, low, alpha);
        };
    }

    public int argb() {
        return pack(alpha) << 24 | pack(red) << 16 | pack(green) << 8 | pack(blue);
    }

    public int rgb() {
        return pack(red) << 16 | pack(green) << 8 | pack(blue);
    }

    public float hue() {
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        double delta = max - min;
        if (delta <= 0.0D) {
            return 0.0F;
        }
        double sector;
        if (max == red) {
            sector = (green - blue) / delta;
        } else if (max == green) {
            sector = (blue - red) / delta + 2.0D;
        } else {
            sector = (red - green) / delta + 4.0D;
        }
        double degrees = sector * SECTOR;
        float hue = (float) (degrees < 0.0D ? degrees + FULL_TURN : degrees);
        return hue >= FULL_TURN ? 0.0F : hue;
    }

    public float saturation() {
        float max = Math.max(red, Math.max(green, blue));
        if (max <= 0.0F) {
            return 0.0F;
        }
        float min = Math.min(red, Math.min(green, blue));
        return (max - min) / max;
    }

    public float value() {
        return Math.max(red, Math.max(green, blue));
    }

    public Rgba withAlpha(float alpha) {
        return new Rgba(red, green, blue, alpha);
    }

    public Rgba clamped() {
        return new Rgba(clamp(red), clamp(green), clamp(blue), clamp(alpha));
    }

    public Rgba premultiplied() {
        return new Rgba(red * alpha, green * alpha, blue * alpha, alpha);
    }

    public Rgba lerp(Rgba to, double t) {
        return new Rgba(lerp(red, to.red, t), lerp(green, to.green, t), lerp(blue, to.blue, t), lerp(alpha, to.alpha, t));
    }

    public Rgba lerpHsv(Rgba to, double t) {
        float fromSaturation = saturation();
        float toSaturation = to.saturation();
        double fromHue = fromSaturation > 0.0F ? hue() : to.hue();
        double toHue = toSaturation > 0.0F ? to.hue() : fromHue;
        double delta = toHue - fromHue;
        if (delta > FULL_TURN / 2.0D) {
            delta -= FULL_TURN;
        } else if (delta < -FULL_TURN / 2.0D) {
            delta += FULL_TURN;
        }
        return hsv((float) (fromHue + delta * t), lerp(fromSaturation, toSaturation, t), lerp(value(), to.value(), t), lerp(alpha, to.alpha, t));
    }

    private static float channel(int bits) {
        return (bits & 0xFF) / CHANNEL_MAX;
    }

    private static int pack(float channel) {
        return Math.round(clamp(channel) * CHANNEL_MAX);
    }

    private static float clamp(float channel) {
        return channel > 1.0F ? 1.0F : channel > 0.0F ? channel : 0.0F;
    }

    private static float lerp(float from, float to, double t) {
        return (float) (from + (to - from) * t);
    }
}
