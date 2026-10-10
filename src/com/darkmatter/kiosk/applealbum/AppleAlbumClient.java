package com.darkmatter.kiosk.applealbum;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class AppleAlbumClient {
    interface Cancellation { void check() throws AlbumException; }
    interface Transport {
        Response post(URI uri, String json, Cancellation cancel) throws AlbumException;
        Response getImage(URI uri, Cancellation cancel) throws AlbumException;
        void cancelAll();
    }
    interface Protocol { List<ImageRef> list(String token, Cancellation cancel) throws AlbumException; }

    static final class Response {
        final int status;
        final Map<String, String> headers;
        final byte[] body;
        final URI finalUri;
        Response(int status, Map<String, String> headers, byte[] body, URI finalUri) {
            this.status = status; this.headers = headers; this.body = body; this.finalUri = finalUri;
        }
    }

    static final class ImageRef {
        final URI uri;
        final String id;
        ImageRef(URI uri, String id) throws AlbumException {
            SafeHttp.validateAppleUri(uri);
            this.uri = uri;
            this.id = id == null ? "" : id;
        }
    }

    private final Transport transport;

    AppleAlbumClient(Transport transport) { this.transport = transport; }

    List<ImageRef> list(String rawUrl, Cancellation cancel) throws AlbumException {
        AlbumUrl parsed = AlbumUrl.parse(rawUrl);
        Protocol protocol = parsed.kind == AlbumUrl.Kind.LEGACY
                ? new LegacyProtocol(transport) : new CloudKitProtocol(transport);
        List<ImageRef> result = protocol.list(parsed.token, cancel);
        if (result.isEmpty()) throw new AlbumException(AlbumException.Code.EMPTY);
        return Collections.unmodifiableList(new ArrayList<>(result));
    }

    byte[] image(ImageRef image, Cancellation cancel) throws AlbumException {
        Response response = transport.getImage(image.uri, cancel);
        if (response.status == 401 || response.status == 403) throw new AlbumException(AlbumException.Code.NOT_PUBLIC);
        if (response.status < 200 || response.status >= 300 || response.body.length == 0) throw new AlbumException(AlbumException.Code.NETWORK);
        String contentType = response.headers.get("content-type");
        if (contentType != null && !contentType.toLowerCase().startsWith("image/")) {
            throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
        }
        return response.body;
    }

    void cancel() { transport.cancelAll(); }
}
