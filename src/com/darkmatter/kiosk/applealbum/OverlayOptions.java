package com.darkmatter.kiosk.applealbum;

import java.util.Map;

/** Sanitized SDK settings for the inline photo overlay. */
final class OverlayOptions {
    static final String TOP_LEFT = "Top left";
    static final String TOP_RIGHT = "Top right";
    static final String BOTTOM_LEFT = "Bottom left";
    static final String BOTTOM_RIGHT = "Bottom right";

    final boolean clock;
    final boolean use24Hour;
    final boolean seconds;
    final boolean currentDate;
    final boolean photoDate;
    final boolean location;
    final boolean pixelShift;
    final String position;
    final String color;
    final String font;
    final int weight;
    final int scale;

    private OverlayOptions(boolean clock, boolean use24Hour, boolean seconds, boolean currentDate,
            boolean photoDate, boolean location, boolean pixelShift, String position, String color,
            String font, int weight, int scale) {
        this.clock = clock; this.use24Hour = use24Hour; this.seconds = seconds;
        this.currentDate = currentDate; this.photoDate = photoDate; this.location = location;
        this.pixelShift = pixelShift;
        this.position = position; this.color = color; this.font = font; this.weight = weight; this.scale = scale;
    }

    static OverlayOptions from(Map<String, Object> settings) {
        return new OverlayOptions(bool(settings, "showClock"), bool(settings, "clock24Hour"),
                bool(settings, "clockSeconds"), bool(settings, "showCurrentDate"),
                bool(settings, "showPhotoDate"), bool(settings, "showLocation"),
                bool(settings, "overlayPixelShift"),
                oneOf(string(settings, "overlayPosition", TOP_RIGHT), TOP_RIGHT,
                        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT),
                color(string(settings, "overlayColor", "#FAFAFA")),
                font(string(settings, "overlayFont", "Sans serif")),
                weight(string(settings, "overlayWeight", "Light")),
                integer(settings == null ? null : settings.get("overlayScale"), 100, 50, 150));
    }

    boolean visible(PhotoMetadata metadata) {
        return clock || currentDate || (photoDate && metadata != null && metadata.captureMillis != null)
                || (location && metadata != null && metadata.hasLocation());
    }

    String cssPosition() {
        if (TOP_LEFT.equals(position)) return "top:0;left:0;align-items:flex-start;text-align:left";
        if (BOTTOM_LEFT.equals(position)) return "bottom:0;left:0;align-items:flex-start;text-align:left";
        if (BOTTOM_RIGHT.equals(position)) return "bottom:0;right:0;align-items:flex-end;text-align:right";
        return "top:0;right:0;align-items:flex-end;text-align:right";
    }

    private static boolean bool(Map<String, Object> settings, String key) {
        return settings != null && Boolean.TRUE.equals(settings.get(key));
    }
    private static String string(Map<String, Object> settings, String key, String fallback) {
        Object value = settings == null ? null : settings.get(key);
        return value instanceof String ? (String) value : fallback;
    }
    private static String oneOf(String value, String fallback, String... values) {
        for (String allowed : values) if (allowed.equals(value)) return value;
        return fallback;
    }
    private static String color(String value) {
        return value != null && value.matches("#[0-9A-Fa-f]{6}") ? value.toUpperCase(java.util.Locale.ROOT) : "#FAFAFA";
    }
    private static String font(String value) {
        if ("Serif".equals(value)) return "serif";
        if ("Monospace".equals(value)) return "monospace";
        if ("Cursive".equals(value)) return "cursive";
        return "sans-serif";
    }
    private static int weight(String value) {
        if ("Regular".equals(value)) return 400;
        if ("Medium".equals(value)) return 500;
        if ("Bold".equals(value)) return 700;
        return 300;
    }
    private static int integer(Object value, int fallback, int min, int max) {
        if (!(value instanceof Number)) return fallback;
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number)) return fallback;
        return Math.max(min, Math.min(max, (int) Math.round(number / 5d) * 5));
    }
}
