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
        final List<URI> renditions;
        final String id;
        final PhotoMetadata metadata;
        ImageRef(URI uri, String id) throws AlbumException { this(uri, id, PhotoMetadata.EMPTY); }
        ImageRef(URI uri, String id, PhotoMetadata metadata) throws AlbumException {
            this(Collections.singletonList(uri), id, metadata);
        }
        ImageRef(List<URI> renditions, String id, PhotoMetadata metadata) throws AlbumException {
            if (renditions == null || renditions.isEmpty() || renditions.size() > 4) {
                throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
            }
            List<URI> checked = new ArrayList<>();
            for (URI rendition : renditions) {
                SafeHttp.validateAppleUri(rendition);
                if (!checked.contains(rendition)) checked.add(rendition);
            }
            if (checked.isEmpty()) throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
            this.renditions = Collections.unmodifiableList(checked);
            this.uri = checked.get(0);
            this.id = id == null ? "" : id;
            this.metadata = metadata == null ? PhotoMetadata.EMPTY : metadata;
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
        AlbumException oversized = null;
        for (int index = 0; index < image.renditions.size(); index++) {
            cancel.check();
            try {
                return image(image, index, cancel);
            } catch (AlbumException error) {
                if (error.code != AlbumException.Code.IMAGE_TOO_LARGE) throw error;
                oversized = error;
            }
        }
        throw oversized == null ? new AlbumException(AlbumException.Code.IMAGE_TOO_LARGE) : oversized;
    }

    byte[] image(ImageRef image, int renditionIndex, Cancellation cancel) throws AlbumException {
        if (renditionIndex < 0 || renditionIndex >= image.renditions.size()) {
            throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
        }
        Response response = transport.getImage(image.renditions.get(renditionIndex), cancel);
        if (response.status == 401 || response.status == 403) throw new AlbumException(AlbumException.Code.NOT_PUBLIC);
        if (response.status < 200 || response.status >= 300 || response.body.length == 0) {
            throw new AlbumException(AlbumException.Code.NETWORK);
        }
        String contentType = response.headers.get("content-type");
        if (contentType != null && !contentType.toLowerCase().startsWith("image/")) {
            throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
        }
        return response.body;
    }

    static boolean retryWithSmallerRendition(AlbumException.Code code) {
        return code == AlbumException.Code.IMAGE_TOO_LARGE
                || code == AlbumException.Code.DECODE_TOO_LARGE
                || code == AlbumException.Code.OUTPUT_TOO_LARGE;
    }

    void cancel() { transport.cancelAll(); }
}
