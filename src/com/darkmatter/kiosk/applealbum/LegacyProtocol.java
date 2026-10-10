package com.darkmatter.kiosk.applealbum;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Original bounded implementation of Apple's legacy sharedstreams public protocol. */
final class LegacyProtocol implements AppleAlbumClient.Protocol {
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int MAX_ITEMS = 1000;
    private static final int BATCH = 25;
    private static final int MAX_PARTITION_REDIRECTS = 3;
    private final AppleAlbumClient.Transport transport;

    LegacyProtocol(AppleAlbumClient.Transport transport) { this.transport = transport; }

    @Override public List<AppleAlbumClient.ImageRef> list(String token, AppleAlbumClient.Cancellation cancel) throws AlbumException {
        URI base = baseUri(token);
        AppleAlbumClient.Response stream = transport.post(base.resolve("webstream"), "{\"streamCtag\":null}", cancel);
        Set<String> redirectHosts = new HashSet<>();
        for (int redirects = 0; stream.status == 330; redirects++) {
            if (redirects >= MAX_PARTITION_REDIRECTS) throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
            String host = stream.headers.get("x-apple-mme-host");
            if (host == null) {
                Map<String, Object> redirect = SafeJson.object(SafeJson.parse(stream.body));
                host = SafeJson.string(redirect, "X-Apple-MMe-Host");
            }
            if (host == null || !redirectHosts.add(host.toLowerCase(java.util.Locale.ROOT))) {
                throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
            }
            base = redirectedBase(host, token);
            stream = transport.post(base.resolve("webstream"), "{\"streamCtag\":null}", cancel);
        }
        requirePublicSuccess(stream);
        Map<String, Object> root = SafeJson.object(SafeJson.parse(stream.body));
        List<Object> photos = root == null ? null : SafeJson.array(root.get("photos"));
        if (photos == null) throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
        if (photos.size() > MAX_ITEMS) throw new AlbumException(AlbumException.Code.TOO_LARGE);

        List<Plan> plans = new ArrayList<>();
        for (Object rawPhoto : photos) {
            cancel.check();
            Map<String, Object> photo = SafeJson.object(rawPhoto);
            String guid = SafeJson.string(photo, "photoGuid");
            String mediaType = SafeJson.string(photo, "mediaAssetType");
            Map<String, Object> derivatives = photo == null ? null : SafeJson.object(photo.get("derivatives"));
            if (guid == null || guid.length() > 256 || derivatives == null || isVideo(mediaType, derivatives)) continue;
            String checksum = bestImageChecksum(derivatives);
            Long captureMillis = PhotoMetadata.parseAppleDate(photo.get("dateCreated"));
            if (checksum != null) plans.add(new Plan(guid, checksum, new PhotoMetadata(captureMillis, null, null)));
        }

        Map<String, URI> urls = new LinkedHashMap<>();
        for (int offset = 0; offset < plans.size(); offset += BATCH) {
            cancel.check();
            List<Object> guids = new ArrayList<>();
            Set<String> requestedChecksums = new HashSet<>();
            for (int i = offset; i < Math.min(plans.size(), offset + BATCH); i++) {
                Plan plan = plans.get(i);
                guids.add(plan.guid);
                requestedChecksums.add(plan.checksum);
            }
            Map<String, Object> request = new LinkedHashMap<>(); request.put("photoGuids", guids);
            AppleAlbumClient.Response response = transport.post(base.resolve("webasseturls"), SafeJson.encode(request), cancel);
            requirePublicSuccess(response);
            Map<String, Object> assetRoot = SafeJson.object(SafeJson.parse(response.body));
            Map<String, Object> items = assetRoot == null ? null : SafeJson.object(assetRoot.get("items"));
            if (items == null) throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
            for (Map.Entry<String, Object> entry : items.entrySet()) {
                if (!requestedChecksums.contains(entry.getKey())) continue;
                Map<String, Object> item = SafeJson.object(entry.getValue());
                String host = SafeJson.string(item, "url_location");
                String path = SafeJson.string(item, "url_path");
                if (host == null || path == null || host.length() > 253 || path.length() > 4096) continue;
                try {
                    URI uri = URI.create("https://" + host + (path.startsWith("/") ? path : "/" + path));
                    SafeHttp.validateAppleUri(uri);
                    urls.put(entry.getKey(), uri);
                } catch (RuntimeException ignored) { /* malformed untrusted item */ }
            }
        }

        List<AppleAlbumClient.ImageRef> result = new ArrayList<>();
        for (Plan plan : plans) {
            URI uri = urls.get(plan.checksum);
            if (uri != null) result.add(new AppleAlbumClient.ImageRef(uri, plan.guid, plan.metadata));
        }
        return result;
    }

    private static void requirePublicSuccess(AppleAlbumClient.Response response) throws AlbumException {
        if (response.status == 401 || response.status == 403 || response.status == 404 || response.status == 410) {
            throw new AlbumException(AlbumException.Code.NOT_PUBLIC);
        }
        if (response.status < 200 || response.status >= 300) throw new AlbumException(AlbumException.Code.NETWORK);
    }

    private static String bestImageChecksum(Map<String, Object> derivatives) {
        String best = null; double bestScore = -1;
        for (Map.Entry<String, Object> entry : derivatives.entrySet()) {
            String key = entry.getKey();
            if (!key.matches("[0-9]+")) continue;
            Map<String, Object> derivative = SafeJson.object(entry.getValue());
            String checksum = SafeJson.string(derivative, "checksum");
            if (checksum == null || checksum.length() > 256) continue;
            double score = number(key);
            if (score < 0) score = number(derivative.get("fileSize"));
            if (score > bestScore) { bestScore = score; best = checksum; }
        }
        return best;
    }

    private static boolean isVideo(String mediaType, Map<String, Object> derivatives) {
        if (mediaType != null) {
            String lower = mediaType.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("video") || lower.contains("movie") || lower.contains("quicktime")
                    || lower.contains("mpeg-4")) return true;
        }
        for (String key : derivatives.keySet()) {
            if ("PosterFrame".equalsIgnoreCase(key) || key.matches("(?i)[0-9]+p")) return true;
        }
        return false;
    }

    /** Apple encodes derivative dimensions and byte counts as either JSON numbers or decimal strings. */
    private static double number(Object raw) {
        try {
            double value = raw instanceof Number ? ((Number) raw).doubleValue()
                    : raw instanceof String ? Double.parseDouble((String) raw) : -1;
            return Double.isFinite(value) && value >= 0 ? value : -1;
        } catch (NumberFormatException ignored) { return -1; }
    }

    private static URI baseUri(String token) throws AlbumException {
        int partition;
        try {
            String encoded = token.charAt(0) == 'A' ? token.substring(1, 2) : token.substring(1, 3);
            partition = 0;
            for (int i = 0; i < encoded.length(); i++) {
                int digit = BASE62.indexOf(encoded.charAt(i));
                if (digit < 0) throw new IllegalArgumentException();
                partition = partition * 62 + digit;
            }
        } catch (RuntimeException error) { throw new AlbumException(AlbumException.Code.INVALID_URL); }
        String host = "p" + (partition < 10 ? "0" : "") + partition + "-sharedstreams.icloud.com";
        return URI.create("https://" + host + "/" + token + "/sharedstreams/");
    }

    private static URI redirectedBase(String host, String token) throws AlbumException {
        if (host == null || !host.matches("(?i)[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.icloud\\.com")) {
            throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
        }
        URI uri = URI.create("https://" + host + "/" + token + "/sharedstreams/");
        SafeHttp.validateAppleUri(uri);
        return uri;
    }

    private static final class Plan {
        final String guid; final String checksum; final PhotoMetadata metadata;
        Plan(String guid, String checksum, PhotoMetadata metadata) {
            this.guid = guid; this.checksum = checksum; this.metadata = metadata;
        }
    }
}
