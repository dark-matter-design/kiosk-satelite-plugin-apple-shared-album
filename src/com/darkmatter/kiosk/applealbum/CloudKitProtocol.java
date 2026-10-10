package com.darkmatter.kiosk.applealbum;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Original bounded implementation of the anonymous public CloudKit album flow. */
final class CloudKitProtocol implements AppleAlbumClient.Protocol {
    private static final URI RESOLVE = URI.create("https://ckdatabasews.icloud.com/database/1/com.apple.photos.cloud/production/public/records/resolve?remapEnums=true");
    private static final String RECORD_TYPE = "CPLAssetAndMasterByAssetDateWithoutHiddenOrDeleted";
    private static final int MAX_PAGES = 20;
    private static final int MAX_RECORDS = 2500;
    private static final int MAX_ITEMS = 1000;
    private static final int MAX_ZONE_NAME_LENGTH = 256;
    private static final int MAX_OWNER_NAME_LENGTH = 256;
    private static final String REGULAR_ZONE = "REGULAR_CUSTOM_ZONE";
    private final AppleAlbumClient.Transport transport;

    CloudKitProtocol(AppleAlbumClient.Transport transport) { this.transport = transport; }

    @Override public List<AppleAlbumClient.ImageRef> list(String token, AppleAlbumClient.Cancellation cancel) throws AlbumException {
        Map<String, Object> shortGuid = new LinkedHashMap<>();
        shortGuid.put("value", token); shortGuid.put("shouldFetchRootRecord", Boolean.TRUE);
        List<Object> shortGuids = new ArrayList<>(); shortGuids.add(shortGuid);
        Map<String, Object> resolveRequest = new LinkedHashMap<>(); resolveRequest.put("shortGUIDs", shortGuids);
        AppleAlbumClient.Response resolve = transport.post(RESOLVE, SafeJson.encode(resolveRequest), cancel);
        if (resolve.status == 401 || resolve.status == 403 || resolve.status == 404 || resolve.status == 410) {
            throw new AlbumException(AlbumException.Code.NOT_PUBLIC);
        }
        if (resolve.status < 200 || resolve.status >= 300) throw new AlbumException(AlbumException.Code.NETWORK);
        Map<String, Object> root = SafeJson.object(SafeJson.parse(resolve.body));
        List<Object> results = root == null ? null : SafeJson.array(root.get("results"));
        Map<String, Object> result = results == null || results.isEmpty() ? null : SafeJson.object(results.get(0));
        Map<String, Object> access = result == null ? null : SafeJson.object(result.get("anonymousPublicAccess"));
        Map<String, Object> rawZone = result == null ? null : SafeJson.object(result.get("zoneID"));
        if (result == null || access == null || rawZone == null || SafeJson.bool(result, "requiresAuthentication")) {
            throw new AlbumException(AlbumException.Code.NOT_PUBLIC);
        }
        Map<String, Object> zone = queryZone(rawZone);
        String partition = SafeJson.string(access, "databasePartition");
        String accessToken = SafeJson.string(access, "token");
        if (partition == null || accessToken == null || accessToken.length() > 4096) {
            throw new AlbumException(AlbumException.Code.NOT_PUBLIC);
        }
        URI base;
        try { base = URI.create(partition.endsWith("/") ? partition : partition + "/"); }
        catch (RuntimeException error) { throw new AlbumException(AlbumException.Code.INVALID_RESPONSE); }
        SafeHttp.validateAppleUri(base);
        String encodedToken;
        try { encodedToken = URLEncoder.encode(accessToken, StandardCharsets.UTF_8.name()); }
        catch (Exception impossible) { throw new AlbumException(AlbumException.Code.INVALID_RESPONSE); }
        URI queryUri = base.resolve("database/1/com.apple.photos.cloud/production/shared/records/query?remapEnums=true&getCurrentSyncToken=true&publicAccessAuthToken=" + encodedToken);

        List<AppleAlbumClient.ImageRef> items = new ArrayList<>();
        Set<String> markers = new HashSet<>();
        String marker = null;
        int recordCount = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            cancel.check();
            Map<String, Object> fieldValue = new LinkedHashMap<>();
            fieldValue.put("value", "DESCENDING"); fieldValue.put("type", "STRING");
            Map<String, Object> direction = new LinkedHashMap<>();
            direction.put("fieldName", "direction"); direction.put("comparator", "EQUALS"); direction.put("fieldValue", fieldValue);
            List<Object> filters = new ArrayList<>(); filters.add(direction);
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("recordType", RECORD_TYPE); query.put("filterBy", filters);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", query); body.put("zoneID", zone); body.put("resultsLimit", 200);
            if (marker != null) body.put("continuationMarker", marker);
            AppleAlbumClient.Response response = transport.post(queryUri, SafeJson.encode(body), cancel);
            if (response.status == 401 || response.status == 403 || response.status == 404 || response.status == 410) {
                throw new AlbumException(AlbumException.Code.NOT_PUBLIC);
            }
            if (response.status < 200 || response.status >= 300) throw new AlbumException(AlbumException.Code.NETWORK);
            Map<String, Object> pageRoot = SafeJson.object(SafeJson.parse(response.body));
            List<Object> records = pageRoot == null ? null : SafeJson.array(pageRoot.get("records"));
            if (records == null) throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
            recordCount += records.size();
            if (recordCount > MAX_RECORDS) throw new AlbumException(AlbumException.Code.TOO_LARGE);
            for (Object rawRecord : records) {
                Map<String, Object> record = SafeJson.object(rawRecord);
                if (!"CPLMaster".equals(SafeJson.string(record, "recordType"))) continue;
                Map<String, Object> fields = record == null ? null : SafeJson.object(record.get("fields"));
                if (fields == null || isVideo(fields)) continue;
                URI image = pickImage(fields);
                if (image != null) {
                    String id = SafeJson.string(record, "recordName");
                    items.add(new AppleAlbumClient.ImageRef(image, id));
                    if (items.size() > MAX_ITEMS) throw new AlbumException(AlbumException.Code.TOO_LARGE);
                }
            }
            marker = SafeJson.string(pageRoot, "continuationMarker");
            if (marker == null || marker.isEmpty()) return items;
            if (marker.length() > 4096 || !markers.add(marker)) throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
        }
        if (marker != null) throw new AlbumException(AlbumException.Code.TOO_LARGE);
        return items;
    }

    private static boolean isVideo(Map<String, Object> fields) {
        String itemType = fieldString(fields, "itemType");
        if (itemType != null && !itemType.isEmpty()) {
            itemType = itemType.toLowerCase();
            return itemType.contains("movie") || itemType.contains("video") || itemType.contains("mpeg-4");
        }
        // Older records can omit itemType. In that case only an actual video rendition is evidence of video.
        for (String key : fields.keySet()) if (key.startsWith("resVid") && key.endsWith("Res")) return true;
        return false;
    }

    private static URI pickImage(Map<String, Object> fields) {
        String[] preferred = {"resJPEGFullRes", "resJPEGMedRes", "resJPEGSmallRes", "resJPEGThumbRes"};
        for (String key : preferred) {
            URI uri = renditionUri(fields, key);
            if (uri != null) return uri;
        }
        // Apple has added other resJPEG*Res names over time. Accept only JPEG renditions here;
        // video tracks and arbitrary CloudKit asset fields remain excluded.
        URI fallback = null; double fallbackWidth = -1;
        for (String key : fields.keySet()) {
            if (!key.startsWith("resJPEG") || !key.endsWith("Res")) continue;
            URI uri = renditionUri(fields, key);
            if (uri == null) continue;
            double width = fieldNumber(fields, key.substring(0, key.length() - 3) + "Width");
            if (fallback == null || width > fallbackWidth) { fallback = uri; fallbackWidth = width; }
        }
        if (fallback != null) return fallback;
        String originalType = fieldString(fields, "resOriginalFileType");
        if ("public.jpeg".equals(originalType) || "public.png".equals(originalType)
                || "org.webmproject.webp".equals(originalType) || "com.compuserve.gif".equals(originalType)) {
            return renditionUri(fields, "resOriginalRes");
        }
        return null;
    }

    private static URI renditionUri(Map<String, Object> fields, String key) {
        Map<String, Object> field = SafeJson.object(fields.get(key));
        Map<String, Object> value = field == null ? null : SafeJson.object(field.get("value"));
        String raw = SafeJson.string(value, "downloadURL");
        if (raw == null || raw.length() > 8192) return null;
        try {
            URI uri = URI.create(raw); SafeHttp.validateAppleUri(uri); return uri;
        } catch (Exception ignored) { return null; }
    }

    private static String fieldString(Map<String, Object> fields, String key) {
        Map<String, Object> field = SafeJson.object(fields.get(key));
        return field == null ? null : SafeJson.string(field, "value");
    }

    /** Rebuild the query zone from the documented scalar fields; never echo the resolve object. */
    private static Map<String, Object> queryZone(Map<String, Object> raw) throws AlbumException {
        String zoneName = boundedZoneString(raw, "zoneName", MAX_ZONE_NAME_LENGTH, true);
        String ownerName = boundedZoneString(raw, "ownerRecordName", MAX_OWNER_NAME_LENGTH, false);
        String zoneType = boundedZoneString(raw, "zoneType", REGULAR_ZONE.length(), false);
        if (zoneType != null && !REGULAR_ZONE.equals(zoneType)) {
            throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
        }
        Map<String, Object> zone = new LinkedHashMap<>();
        zone.put("zoneName", zoneName);
        if (ownerName != null) zone.put("ownerRecordName", ownerName);
        if (zoneType != null) zone.put("zoneType", zoneType);
        return zone;
    }

    private static String boundedZoneString(Map<String, Object> raw, String key, int maxLength, boolean required)
            throws AlbumException {
        Object value = raw.get(key);
        if (value == null && !required) return null;
        if (!(value instanceof String) || ((String) value).isEmpty() || ((String) value).length() > maxLength) {
            throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
        }
        return (String) value;
    }

    private static double fieldNumber(Map<String, Object> fields, String key) {
        Map<String, Object> field = SafeJson.object(fields.get(key));
        Object value = field == null ? null : field.get("value");
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            return Double.isFinite(number) && number >= 0 ? number : -1;
        }
        if (value instanceof String) {
            try {
                double number = Double.parseDouble((String) value);
                return Double.isFinite(number) && number >= 0 ? number : -1;
            } catch (NumberFormatException ignored) { }
        }
        return -1;
    }
}
