package com.darkmatter.kiosk.applealbum;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;

/** Bounds native decoding and produces a display JPEG small enough for the SDK inline renderer. */
final class PhotoEncoder {
    static final int MAX_ENCODED_BYTES = 360 * 1024;
    private static final int MAX_EDGE = 1920;
    private static final int MAX_SOURCE_EDGE = 20_000;
    private static final long MAX_SOURCE_PIXELS = 64_000_000L;

    private PhotoEncoder() {}

    static byte[] fit(byte[] source) throws AlbumException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(source, 0, source.length, bounds);
        validateSourceBounds(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_EDGE * 2) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap decoded;
        try { decoded = BitmapFactory.decodeByteArray(source, 0, source.length, options); }
        catch (OutOfMemoryError error) { throw new AlbumException(AlbumException.Code.DECODE_TOO_LARGE); }
        catch (RuntimeException error) { throw new AlbumException(AlbumException.Code.INVALID_RESPONSE); }
        if (decoded == null) throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);

        Bitmap current = decoded;
        try {
            int edge = Math.max(current.getWidth(), current.getHeight());
            if (edge > MAX_EDGE) {
                double ratio = MAX_EDGE / (double) edge;
                Bitmap scaled = Bitmap.createScaledBitmap(current, Math.max(1, (int) (current.getWidth() * ratio)),
                        Math.max(1, (int) (current.getHeight() * ratio)), true);
                if (scaled != current) { current.recycle(); current = scaled; }
            }
            for (int pass = 0; pass < 6; pass++) {
                for (int quality = 88; quality >= 48; quality -= 8) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream(MAX_ENCODED_BYTES);
                    if (!current.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                        throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
                    }
                    if (output.size() <= MAX_ENCODED_BYTES) return output.toByteArray();
                }
                int width = Math.max(320, current.getWidth() * 4 / 5);
                int height = Math.max(240, current.getHeight() * 4 / 5);
                if (width >= current.getWidth() && height >= current.getHeight()) break;
                Bitmap scaled = Bitmap.createScaledBitmap(current, width, height, true);
                if (scaled != current) { current.recycle(); current = scaled; }
            }
            throw new AlbumException(AlbumException.Code.OUTPUT_TOO_LARGE);
        } catch (OutOfMemoryError error) {
            throw new AlbumException(AlbumException.Code.DECODE_TOO_LARGE);
        } finally {
            if (!current.isRecycled()) current.recycle();
        }
    }

    static void validateSourceBounds(int width, int height) throws AlbumException {
        if (width <= 0 || height <= 0 || width > MAX_SOURCE_EDGE || height > MAX_SOURCE_EDGE
                || (long) width * height > MAX_SOURCE_PIXELS) {
            throw new AlbumException(AlbumException.Code.DECODE_TOO_LARGE);
        }
    }
}
