package com.darkmatter.kiosk.applealbum;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private static final int MAX_RENDITIONS = 4;
    private static final int MAX_ASSET_ITEMS_PER_BATCH = 250;
    private static final double TARGET_EDGE = 1920;
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
        if (photos.size() > MAX_ITEMS) throw new AlbumException(AlbumException.Code.METADATA_TOO_LARGE);

        List<Plan> plans = new ArrayList<>();
        for (Object rawPhoto : photos) {
            cancel.check();
            Map<String, Object> photo = SafeJson.object(rawPhoto);
            String guid = SafeJson.string(photo, "photoGuid");
            String mediaType = SafeJson.string(photo, "mediaAssetType");
            Map<String, Object> derivatives = photo == null ? null : SafeJson.object(photo.get("derivatives"));
            if (guid == null || guid.length() > 256 || derivatives == null || isVideo(mediaType, derivatives)) continue;
            List<String> checksums = bestImageChecksums(derivatives);
            Long captureMillis = PhotoMetadata.parseAppleDate(photo.get("dateCreated"));
            if (!checksums.isEmpty()) plans.add(new Plan(guid, checksums, new PhotoMetadata(captureMillis, null, null)));
        }

        Map<String, URI> urls = new LinkedHashMap<>();
        for (int offset = 0; offset < plans.size(); offset += BATCH) {
            cancel.check();
            List<Object> guids = new ArrayList<>();
            Set<String> requestedChecksums = new HashSet<>();
            for (int i = offset; i < Math.min(plans.size(), offset + BATCH); i++) {
                Plan plan = plans.get(i);
                guids.add(plan.guid);
                requestedChecksums.addAll(plan.checksums);
            }
            Map<String, Object> request = new LinkedHashMap<>(); request.put("photoGuids", guids);
            AppleAlbumClient.Response response = transport.post(base.resolve("webasseturls"), SafeJson.encode(request), cancel);
            requirePublicSuccess(response);
            Map<String, Object> assetRoot = SafeJson.object(SafeJson.parse(response.body));
            Map<String, Object> items = assetRoot == null ? null : SafeJson.object(assetRoot.get("items"));
            if (items == null) throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
            if (items.size() > MAX_ASSET_ITEMS_PER_BATCH) {
                throw new AlbumException(AlbumException.Code.METADATA_TOO_LARGE);
            }
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
            List<URI> renditions = new ArrayList<>();
            for (String checksum : plan.checksums) {
                URI uri = urls.get(checksum);
                if (uri != null) renditions.add(uri);
            }
            if (!renditions.isEmpty()) result.add(new AppleAlbumClient.ImageRef(renditions, plan.guid, plan.metadata));
        }
        return result;
    }

    private static void requirePublicSuccess(AppleAlbumClient.Response response) throws AlbumException {
        if (response.status == 401 || response.status == 403 || response.status == 404 || response.status == 410) {
            throw new AlbumException(AlbumException.Code.NOT_PUBLIC);
        }
        if (response.status < 200 || response.status >= 300) throw new AlbumException(AlbumException.Code.NETWORK);
    }

    private static List<String> bestImageChecksums(Map<String, Object> derivatives) {
        List<Derivative> candidates = new ArrayList<>();
        Set<String> checksums = new HashSet<>();
        for (Map.Entry<String, Object> entry : derivatives.entrySet()) {
            String key = entry.getKey();
            if (!key.matches("[0-9]+")) continue;
            Map<String, Object> derivative = SafeJson.object(entry.getValue());
            String checksum = SafeJson.string(derivative, "checksum");
            if (checksum == null || checksum.length() > 256 || !checksums.add(checksum)) continue;
            double size = number(derivative.get("fileSize"));
            if (size > SafeHttp.MAX_IMAGE_BYTES) continue;
            double edge = Math.max(number(key), Math.max(number(derivative.get("width")), number(derivative.get("height"))));
            candidates.add(new Derivative(checksum, edge));
        }
        Collections.sort(candidates, new Comparator<Derivative>() {
            @Override public int compare(Derivative left, Derivative right) {
                if (left.edge < 0) return right.edge < 0 ? 0 : 1;
                if (right.edge < 0) return -1;
                return Double.compare(left.edge, right.edge);
            }
        });
        List<String> result = new ArrayList<>();
        int primary = primary(candidates);
        if (primary >= 0) {
            result.add(candidates.get(primary).checksum);
            for (int i = primary - 1; i >= 0 && result.size() < MAX_RENDITIONS; i--) {
                if (candidates.get(i).edge >= 0) result.add(candidates.get(i).checksum);
            }
            for (int i = primary + 1; i < candidates.size() && result.size() < MAX_RENDITIONS; i++) {
                result.add(candidates.get(i).checksum);
            }
        } else {
            for (Derivative candidate : candidates) {
                result.add(candidate.checksum);
                if (result.size() == MAX_RENDITIONS) break;
            }
        }
        return result;
    }

    /** Prefer the smallest screen-sized derivative, then monotonically smaller fallbacks. */
    private static int primary(List<Derivative> sorted) {
        int largestBelow = -1, smallestAbove = -1;
        for (int i = 0; i < sorted.size(); i++) {
            double edge = sorted.get(i).edge;
            if (edge < 0) break;
            if (edge >= TARGET_EDGE && edge <= 2560) return i;
            if (edge < TARGET_EDGE) largestBelow = i;
            else if (smallestAbove < 0) smallestAbove = i;
        }
        return largestBelow >= 0 ? largestBelow : smallestAbove;
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
        final String guid; final List<String> checksums; final PhotoMetadata metadata;
        Plan(String guid, List<String> checksums, PhotoMetadata metadata) {
            this.guid = guid; this.checksums = checksums; this.metadata = metadata;
        }
    }

    private static final class Derivative {
        final String checksum; final double edge;
        Derivative(String checksum, double edge) { this.checksum = checksum; this.edge = edge; }
    }
}
