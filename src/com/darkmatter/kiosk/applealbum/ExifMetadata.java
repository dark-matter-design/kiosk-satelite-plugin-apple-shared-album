package com.darkmatter.kiosk.applealbum;

import java.nio.charset.StandardCharsets;

/** Minimal bounded JPEG EXIF reader for capture date and GPS coordinates. */
final class ExifMetadata {
    private static final int MAX_EXIF_BYTES = 64 * 1024;
    private ExifMetadata() {}

    static PhotoMetadata read(byte[] image) {
        if (image == null || image.length < 4 || (image[0] & 255) != 0xff || (image[1] & 255) != 0xd8) {
            return PhotoMetadata.EMPTY;
        }
        int at = 2;
        while (at + 4 <= image.length) {
            if ((image[at] & 255) != 0xff) break;
            int marker = image[at + 1] & 255;
            at += 2;
            if (marker == 0xda || marker == 0xd9) break;
            if (marker == 0x01 || (marker >= 0xd0 && marker <= 0xd7)) continue;
            int length = u16be(image, at);
            if (length < 2 || at + length > image.length) break;
            if (marker == 0xe1 && length >= 8 && length <= MAX_EXIF_BYTES
                    && asciiEquals(image, at + 2, "Exif\0\0")) {
                try { return readTiff(image, at + 8, length - 8); }
                catch (RuntimeException ignored) { return PhotoMetadata.EMPTY; }
            }
            at += length;
        }
        return PhotoMetadata.EMPTY;
    }

    private static PhotoMetadata readTiff(byte[] data, int base, int length) {
        if (length < 8 || base < 0 || base + length > data.length) return PhotoMetadata.EMPTY;
        boolean little;
        if (data[base] == 'I' && data[base + 1] == 'I') little = true;
        else if (data[base] == 'M' && data[base + 1] == 'M') little = false;
        else return PhotoMetadata.EMPTY;
        if (u16(data, base + 2, little) != 42) return PhotoMetadata.EMPTY;
        int ifd0 = offset(data, base, length, u32(data, base + 4, little));
        Ifd root = readIfd(data, base, length, ifd0, little);
        Long capture = null;
        if (root.exifOffset >= 0) {
            Ifd exif = readIfd(data, base, length, offset(data, base, length, root.exifOffset), little);
            capture = PhotoMetadata.parseAppleDate(exif.dateTimeOriginal);
        }
        if (capture == null) capture = PhotoMetadata.parseAppleDate(root.dateTimeOriginal);
        Double latitude = null, longitude = null;
        if (root.gpsOffset >= 0) {
            Ifd gps = readIfd(data, base, length, offset(data, base, length, root.gpsOffset), little);
            latitude = coordinate(gps.latitude, gps.latitudeRef, true);
            longitude = coordinate(gps.longitude, gps.longitudeRef, false);
        }
        return new PhotoMetadata(capture, latitude, longitude);
    }

    private static Ifd readIfd(byte[] data, int base, int length, int at, boolean little) {
        Ifd result = new Ifd();
        if (at < base || at + 2 > base + length) return result;
        int count = Math.min(u16(data, at, little), 128);
        at += 2;
        for (int i = 0; i < count && at + 12 <= base + length; i++, at += 12) {
            int tag = u16(data, at, little);
            int type = u16(data, at + 2, little);
            long rawCount = u32(data, at + 4, little);
            if (rawCount < 0 || rawCount > 64) continue;
            int unit = type == 1 || type == 2 || type == 7 ? 1 : type == 3 ? 2 : type == 4 || type == 9 ? 4 : type == 5 || type == 10 ? 8 : 0;
            if (unit == 0) continue;
            long byteCount = rawCount * unit;
            int valueAt;
            if (byteCount <= 4) valueAt = at + 8;
            else valueAt = offset(data, base, length, u32(data, at + 8, little));
            if (valueAt < base || byteCount > Integer.MAX_VALUE || valueAt + byteCount > base + length) continue;
            if (tag == 0x8769 && type == 4 && rawCount == 1) result.exifOffset = u32(data, valueAt, little);
            else if (tag == 0x8825 && type == 4 && rawCount == 1) result.gpsOffset = u32(data, valueAt, little);
            else if ((tag == 0x9003 || tag == 0x0132) && type == 2) result.dateTimeOriginal = ascii(data, valueAt, (int) rawCount);
            else if (tag == 1 && type == 2) result.latitudeRef = ascii(data, valueAt, (int) rawCount);
            else if (tag == 2 && type == 5 && rawCount == 3) result.latitude = rationals(data, valueAt, little);
            else if (tag == 3 && type == 2) result.longitudeRef = ascii(data, valueAt, (int) rawCount);
            else if (tag == 4 && type == 5 && rawCount == 3) result.longitude = rationals(data, valueAt, little);
        }
        return result;
    }

    private static Double coordinate(double[] parts, String ref, boolean latitude) {
        if (parts == null || parts.length != 3 || ref == null) return null;
        double value = parts[0] + parts[1] / 60d + parts[2] / 3600d;
        String direction = ref.trim().toUpperCase(java.util.Locale.ROOT);
        if ((latitude && "S".equals(direction)) || (!latitude && "W".equals(direction))) value = -value;
        else if (!(latitude ? "N".equals(direction) : "E".equals(direction))) return null;
        double limit = latitude ? 90 : 180;
        return Double.isFinite(value) && value >= -limit && value <= limit ? value : null;
    }

    private static double[] rationals(byte[] data, int at, boolean little) {
        double[] values = new double[3];
        for (int i = 0; i < 3; i++) {
            long numerator = u32(data, at + i * 8, little);
            long denominator = u32(data, at + i * 8 + 4, little);
            if (denominator == 0) return null;
            values[i] = numerator / (double) denominator;
        }
        return values;
    }

    private static int offset(byte[] data, int base, int length, long relative) {
        if (relative < 0 || relative > length || base + relative > Integer.MAX_VALUE) return -1;
        return base + (int) relative;
    }

    private static int u16be(byte[] data, int at) { return ((data[at] & 255) << 8) | (data[at + 1] & 255); }
    private static int u16(byte[] data, int at, boolean little) {
        return little ? (data[at] & 255) | ((data[at + 1] & 255) << 8) : u16be(data, at);
    }
    private static long u32(byte[] data, int at, boolean little) {
        long a = u16(data, little ? at : at + 2, little) & 0xffffL;
        long b = u16(data, little ? at + 2 : at, little) & 0xffffL;
        return little ? a | (b << 16) : (b << 16) | a;
    }
    private static String ascii(byte[] data, int at, int count) {
        int length = 0;
        while (length < count && data[at + length] != 0) length++;
        return PhotoMetadata.boundedText(new String(data, at, length, StandardCharsets.US_ASCII), 64);
    }
    private static boolean asciiEquals(byte[] data, int at, String value) {
        if (at < 0 || at + value.length() > data.length) return false;
        for (int i = 0; i < value.length(); i++) if ((byte) value.charAt(i) != data[at + i]) return false;
        return true;
    }
    private static final class Ifd {
        long exifOffset = -1, gpsOffset = -1;
        String dateTimeOriginal, latitudeRef, longitudeRef;
        double[] latitude, longitude;
    }
}
