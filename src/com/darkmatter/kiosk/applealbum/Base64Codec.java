package com.darkmatter.kiosk.applealbum;

final class Base64Codec {
    private static final char[] TABLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private Base64Codec() {}

    static String encode(byte[] data) {
        StringBuilder out = new StringBuilder(((data.length + 2) / 3) * 4);
        for (int i = 0; i < data.length; i += 3) {
            int a = data[i] & 255;
            int b = i + 1 < data.length ? data[i + 1] & 255 : 0;
            int c = i + 2 < data.length ? data[i + 2] & 255 : 0;
            out.append(TABLE[a >>> 2]);
            out.append(TABLE[((a & 3) << 4) | (b >>> 4)]);
            out.append(i + 1 < data.length ? TABLE[((b & 15) << 2) | (c >>> 6)] : '=');
            out.append(i + 2 < data.length ? TABLE[c & 63] : '=');
        }
        return out.toString();
    }
}
