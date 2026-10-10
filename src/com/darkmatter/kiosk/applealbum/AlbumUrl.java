package com.darkmatter.kiosk.applealbum;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AlbumUrl {
    enum Kind { LEGACY, CLOUDKIT }

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{6,128}");
    private static final Pattern CURRENT_PATH = Pattern.compile("^/shared/album/([A-Za-z0-9_-]{6,128})/?$");
    final Kind kind;
    final String token;

    private AlbumUrl(Kind kind, String token) {
        this.kind = kind;
        this.token = token;
    }

    static AlbumUrl parse(String raw) throws AlbumException {
        if (raw == null || raw.length() > 512) throw new AlbumException(AlbumException.Code.INVALID_URL);
        final URI uri;
        try { uri = URI.create(raw.trim()); }
        catch (RuntimeException error) { throw new AlbumException(AlbumException.Code.INVALID_URL); }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new AlbumException(AlbumException.Code.INVALID_URL);
        }
        host = host.toLowerCase(Locale.ROOT);
        if ("photos.icloud.com".equals(host)) {
            Matcher match = CURRENT_PATH.matcher(uri.getPath());
            if (match.matches()) return new AlbumUrl(Kind.CLOUDKIT, match.group(1));
        }
        if (("icloud.com".equals(host) || host.endsWith(".icloud.com"))
                && "/sharedalbum/".equals(uri.getPath())) {
            String fragment = uri.getRawFragment();
            if (fragment != null) {
                int suffix = fragment.indexOf(';');
                String token = suffix < 0 ? fragment : fragment.substring(0, suffix);
                if (TOKEN.matcher(token).matches()) return new AlbumUrl(Kind.LEGACY, token);
            }
        }
        throw new AlbumException(AlbumException.Code.INVALID_URL);
    }
}
