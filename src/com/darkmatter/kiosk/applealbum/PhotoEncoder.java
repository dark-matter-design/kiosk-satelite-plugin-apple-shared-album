package com.darkmatter.kiosk.applealbum;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;

/** Bounds native decoding and produces a display JPEG small enough for the SDK inline renderer. */
final class PhotoEncoder {
    static final int SINGLE_IMAGE_BYTES = 360 * 1024;
    static final int TRANSITION_INCOMING_BYTES = 260 * 1024;
    static final int TRANSITION_OUTGOING_BYTES = 96 * 1024;
    static final int MAX_ENCODED_BYTES = SINGLE_IMAGE_BYTES;
    private static final int MAX_EDGE = 1920;
    private static final int MAX_SOURCE_EDGE = 20_000;
    private static final long MAX_SOURCE_PIXELS = 64_000_000L;

    private PhotoEncoder() {}
    static byte[] fit(byte[] source) throws AlbumException { return fit(source, SINGLE_IMAGE_BYTES); }

    static byte[] fit(byte[] source, int maxBytes) throws AlbumException {
        if (source == null || source.length == 0 || maxBytes < 16 * 1024 || maxBytes > SINGLE_IMAGE_BYTES) {
            throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(source, 0, source.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > MAX_SOURCE_EDGE
                || bounds.outHeight > MAX_SOURCE_EDGE || (long) bounds.outWidth * bounds.outHeight > MAX_SOURCE_PIXELS) {
            throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_EDGE * 2) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap decoded;
        try { decoded = BitmapFactory.decodeByteArray(source, 0, source.length, options); }
        catch (OutOfMemoryError error) { throw new AlbumException(AlbumException.Code.TOO_LARGE); }
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
            for (int pass = 0; pass < 8; pass++) {
                for (int quality = 92; quality >= 44; quality -= 8) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream(maxBytes);
                    if (!current.compress(Bitmap.CompressFormat.JPEG, quality, output)) throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
                    if (output.size() <= maxBytes) return output.toByteArray();
                }
                int width = Math.max(240, current.getWidth() * 17 / 20);
                int height = Math.max(180, current.getHeight() * 17 / 20);
                if (width >= current.getWidth() && height >= current.getHeight()) break;
                Bitmap scaled = Bitmap.createScaledBitmap(current, width, height, true);
                if (scaled != current) { current.recycle(); current = scaled; }
            }
            throw new AlbumException(AlbumException.Code.TOO_LARGE);
        } catch (OutOfMemoryError error) {
            throw new AlbumException(AlbumException.Code.TOO_LARGE);
        } finally {
            if (!current.isRecycled()) current.recycle();
        }
    }
}
