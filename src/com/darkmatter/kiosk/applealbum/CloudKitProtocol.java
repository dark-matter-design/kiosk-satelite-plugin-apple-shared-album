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
        Map<String, Object> zone = result == null ? null : SafeJson.object(result.get("zoneID"));
        if (result == null || access == null || zone == null || SafeJson.bool(result, "requiresAuthentication")) {
            throw new AlbumException(AlbumException.Code.NOT_PUBLIC);
        }
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
        if (itemType == null) return false;
        itemType = itemType.toLowerCase();
        return itemType.contains("movie") || itemType.contains("video") || itemType.contains("mpeg-4");
    }

    private static URI pickImage(Map<String, Object> fields) {
        String[] preferred = {"resJPEGFullRes", "resJPEGMedRes", "resJPEGThumbRes"};
        for (String key : preferred) {
            URI uri = renditionUri(fields, key);
            if (uri != null) return uri;
        }
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
}
