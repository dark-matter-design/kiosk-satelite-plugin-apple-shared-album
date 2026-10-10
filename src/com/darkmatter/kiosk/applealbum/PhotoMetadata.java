package com.darkmatter.kiosk.applealbum;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Bounded presentation metadata carried with one image. */
final class PhotoMetadata {
    static final PhotoMetadata EMPTY = new PhotoMetadata(null, null, null, null);
    private static final long MIN_CAPTURE_MILLIS = -2208988800000L; // 1900-01-01
    private static final long MAX_CAPTURE_MILLIS = 7258118400000L;  // 2200-01-01

    final Long captureMillis;
    final Double latitude;
    final Double longitude;
    final String placeName;

    PhotoMetadata(Long captureMillis, Double latitude, Double longitude) {
        this(captureMillis, latitude, longitude, null);
    }

    PhotoMetadata(Long captureMillis, Double latitude, Double longitude, String placeName) {
        this.captureMillis = validMillis(captureMillis) ? captureMillis : null;
        this.latitude = validCoordinate(latitude, -90, 90) ? latitude : null;
        this.longitude = validCoordinate(longitude, -180, 180) ? longitude : null;
        this.placeName = boundedText(placeName, 96);
    }

    PhotoMetadata withFallback(PhotoMetadata fallback) {
        if (fallback == null) return this;
        return new PhotoMetadata(captureMillis != null ? captureMillis : fallback.captureMillis,
                latitude != null ? latitude : fallback.latitude,
                longitude != null ? longitude : fallback.longitude,
                placeName != null ? placeName : fallback.placeName);
    }

    boolean hasLocation() { return placeName != null || (latitude != null && longitude != null); }

    String formattedLocation() {
        if (placeName != null) return placeName;
        if (!hasLocation()) return null;
        return String.format(Locale.ROOT, "GPS · %.5f° %s, %.5f° %s",
                Math.abs(latitude), latitude < 0 ? "S" : "N",
                Math.abs(longitude), longitude < 0 ? "W" : "E");
    }

    String formattedCaptureDate(Locale locale, TimeZone zone) {
        if (captureMillis == null) return null;
        DateFormat format = DateFormat.getDateInstance(DateFormat.MEDIUM, locale == null ? Locale.getDefault() : locale);
        format.setTimeZone(zone == null ? TimeZone.getDefault() : zone);
        String value = format.format(new Date(captureMillis));
        return boundedText(value, 80);
    }

    static Long parseAppleDate(Object raw) {
        if (raw instanceof Number) {
            double value = ((Number) raw).doubleValue();
            if (!Double.isFinite(value)) return null;
            // CloudKit assetDate is milliseconds; tolerate documented epoch-second shapes too.
            long millis = Math.abs(value) < 100_000_000_000d ? Math.round(value * 1000d) : Math.round(value);
            return validMillis(millis) ? millis : null;
        }
        if (!(raw instanceof String)) return null;
        String value = ((String) raw).trim();
        if (value.length() > 64 || value.isEmpty()) return null;
        try {
            double numeric = Double.parseDouble(value);
            if (Double.isFinite(numeric)) return parseAppleDate(Double.valueOf(numeric));
        } catch (NumberFormatException ignored) { }
        String[] patterns = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss Z", "yyyy:MM:dd HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
                format.setLenient(false);
                if (pattern.endsWith("'Z'")) format.setTimeZone(TimeZone.getTimeZone("UTC"));
                long millis = format.parse(value).getTime();
                if (validMillis(millis)) return millis;
            } catch (ParseException ignored) { }
        }
        return null;
    }

    static String boundedText(String raw, int max) {
        if (raw == null) return null;
        StringBuilder out = new StringBuilder(Math.min(raw.length(), max));
        for (int i = 0; i < raw.length() && out.length() < max; i++) {
            char c = raw.charAt(i);
            int type = Character.getType(c);
            if (Character.isWhitespace(c)) out.append(' ');
            else if (!Character.isISOControl(c) && type != Character.FORMAT
                    && type != Character.PRIVATE_USE && type != Character.SURROGATE
                    && type != Character.UNASSIGNED) out.append(c);
        }
        String value = out.toString().trim().replaceAll("\\s+", " ");
        return value.isEmpty() ? null : value;
    }

    private static boolean validMillis(Long value) {
        return value != null && value >= MIN_CAPTURE_MILLIS && value <= MAX_CAPTURE_MILLIS;
    }

    private static boolean validCoordinate(Double value, double min, double max) {
        return value != null && Double.isFinite(value) && value >= min && value <= max;
    }
}
