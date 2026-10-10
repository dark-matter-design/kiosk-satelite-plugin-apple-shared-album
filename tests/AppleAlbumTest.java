package com.darkmatter.kiosk.applealbum;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import me.jxl.kiosk.plugins.PluginHost;

public final class AppleAlbumTest {
    private static final AppleAlbumClient.Cancellation ACTIVE = new AppleAlbumClient.Cancellation() {
        @Override public void check() { }
    };

    public static void main(String[] args) throws Exception {
        parsesOnlySupportedUrls();
        legacyListsSyntheticImages();
        legacySkipsVideoShapedItemWithoutMediaType();
        legacyFollowsBodyRedirect();
        legacyRejectsUnsafeRedirectHost();
        cloudKitPaginatesSyntheticRecords();
        cloudKitParsesReferenceShapedRenditions();
        cloudKitParsesCurrentPairedTemplateRenditions();
        cloudKitRejectsOversizedZone();
        cloudKitRejectsRepeatedMarker();
        cloudKitRejectsAuthenticationRequired();
        durationSettingIsBoundedBeforeScheduling();
        overlayOptionsAreBoundedAndPrivateByDefault();
        overlayPositionsAndUnavailableMetadataRenderSafely();
        overlayFormattingHooksAreDeterministic();
        extractsBoundedExifMetadataLocally();
        enforcesSafeHostsAndInlineBudget();
        sanitizesEveryPublicError();
        jsonRoundTripsAndBoundsDepth();
        publishesSelectableScreensaverBeforeAlbumIo();
        lifecycleCancellationDoesNotWaitForMetadataIo();
        System.out.println("AppleAlbumTest: 21 checks passed");
    }

    private static void parsesOnlySupportedUrls() throws Exception {
        assert AlbumUrl.parse("https://www.icloud.com/sharedalbum/#B12abcDEF").kind == AlbumUrl.Kind.LEGACY;
        assert AlbumUrl.parse("https://photos.icloud.com/shared/album/OPAQUE_123").kind == AlbumUrl.Kind.CLOUDKIT;
        rejectsUrl("http://www.icloud.com/sharedalbum/#B12abcDEF");
        rejectsUrl("https://evil.example/sharedalbum/#B12abcDEF");
        rejectsUrl("https://photos.icloud.com/shared/album/");
        rejectsUrl("https://share.icloud.com/photos/NOT_AN_ALBUM");
        rejectsUrl("B12abcDEF");
    }

    private static void legacyListsSyntheticImages() throws Exception {
        FakeTransport fake = new FakeTransport();
        fake.add(200, "{\"photos\":["
                + "{\"photoGuid\":\"photo-one\",\"dateCreated\":\"2024-06-15T10:30:00Z\",\"derivatives\":{"
                + "\"342\":{\"checksum\":\"small\",\"fileSize\":\"43050\",\"width\":\"257\",\"height\":\"342\"},"
                + "\"2049\":{\"checksum\":\"large\",\"fileSize\":985681,\"width\":1537,\"height\":2049}}},"
                + "{\"photoGuid\":\"video-one\",\"mediaAssetType\":\"video\",\"derivatives\":{\"720p\":{\"checksum\":\"movie\"}}}]}");
        fake.add(200, "{\"items\":{"
                + "\"large\":{\"url_location\":\"cvws.icloud-content.com\",\"url_path\":\"/synthetic/image.jpg\"},"
                + "\"unrequested-checksum\":{\"url_location\":\"cvws.icloud-content.com\",\"url_path\":\"/synthetic/unrequested.jpg\"}}}");
        AppleAlbumClient client = new AppleAlbumClient(fake);
        List<AppleAlbumClient.ImageRef> images = client.list("https://www.icloud.com/sharedalbum/#B12abcDEF", ACTIVE);
        assert images.size() == 1;
        assert images.get(0).uri.equals(URI.create("https://cvws.icloud-content.com/synthetic/image.jpg"));
        assert images.get(0).metadata.captureMillis != null;
        assert fake.requests.get(0).endsWith("/webstream");
        assert fake.requests.get(1).endsWith("/webasseturls");
        assert fake.bodies.get(1).contains("photo-one") && !fake.bodies.get(1).contains("video-one");
    }

    private static void legacySkipsVideoShapedItemWithoutMediaType() throws Exception {
        FakeTransport fake = new FakeTransport();
        fake.add(200, "{\"photos\":["
                + "{\"photoGuid\":\"still\",\"derivatives\":{\"2049\":{\"checksum\":\"still-large\"}}},"
                + "{\"photoGuid\":\"video\",\"derivatives\":{\"PosterFrame\":{\"checksum\":\"poster\"},"
                + "\"720p\":{\"checksum\":\"movie\"}}}]}");
        fake.add(200, "{\"items\":{\"still-large\":{\"url_location\":\"cvws.icloud-content.com\","
                + "\"url_path\":\"/synthetic/still.jpg\"}}}");
        List<AppleAlbumClient.ImageRef> images = new AppleAlbumClient(fake)
                .list("https://www.icloud.com/sharedalbum/#D20v3Co2e_yw_F4H", ACTIVE);
        assert images.size() == 1;
        assert !fake.bodies.get(1).contains("video");
    }

    private static void legacyFollowsBodyRedirect() throws Exception {
        FakeTransport fake = new FakeTransport();
        fake.add(330, "{\"X-Apple-MMe-Host\":\"p42-sharedstreams.icloud.com\"}");
        fake.add(200, "{\"photos\":[{\"photoGuid\":\"still\","
                + "\"derivatives\":{\"2049\":{\"checksum\":\"large\"}}}]}");
        fake.add(200, "{\"items\":{\"large\":{\"url_location\":\"cvws.icloud-content.com\","
                + "\"url_path\":\"/synthetic/redirected.jpg\"}}}");
        List<AppleAlbumClient.ImageRef> images = new AppleAlbumClient(fake)
                .list("https://www.icloud.com/sharedalbum/#B12abcDEF", ACTIVE);
        assert images.size() == 1;
        assert fake.requests.get(1).startsWith("https://p42-sharedstreams.icloud.com/");
        assert fake.requests.get(2).startsWith("https://p42-sharedstreams.icloud.com/");
    }

    private static void legacyRejectsUnsafeRedirectHost() throws Exception {
        FakeTransport fake = new FakeTransport();
        Map<String, String> headers = new LinkedHashMap<>(); headers.put("x-apple-mme-host", "attacker.example");
        fake.add(new AppleAlbumClient.Response(330, headers, new byte[0], URI.create("https://p01-sharedstreams.icloud.com/")));
        try {
            new AppleAlbumClient(fake).list("https://www.icloud.com/sharedalbum/#B12abcDEF", ACTIVE);
            throw new AssertionError("unsafe redirect accepted");
        } catch (AlbumException expected) { assert expected.code == AlbumException.Code.INVALID_RESPONSE; }
    }

    private static void cloudKitPaginatesSyntheticRecords() throws Exception {
        FakeTransport fake = new FakeTransport();
        fake.add(200, "{\"results\":[{\"zoneID\":{\"zoneName\":\"synthetic-zone\",\"ownerRecordName\":\"synthetic-owner\"},"
                + "\"anonymousPublicAccess\":{\"databasePartition\":\"https://p99-ckdatabasews.icloud.com\",\"token\":\"synthetic-access\"}}]}");
        fake.add(200, "{\"records\":[{\"recordType\":\"CPLMaster\",\"recordName\":\"one\",\"fields\":{"
                + "\"itemType\":{\"value\":\"public.jpeg\"},\"resJPEGMedRes\":{\"value\":{\"downloadURL\":\"https://p99-content.icloud.com/synthetic/one.jpg\"}}}}],"
                + "\"continuationMarker\":\"synthetic-next\"}");
        fake.add(200, "{\"records\":["
                + "{\"recordType\":\"CPLMaster\",\"recordName\":\"two\",\"fields\":{\"resJPEGFullRes\":{\"value\":{\"downloadURL\":\"https://cvws.icloud-content.com/synthetic/two.jpg\"}}}},"
                + "{\"recordType\":\"CPLMaster\",\"fields\":{\"itemType\":{\"value\":\"public.mpeg-4-video\"},\"resJPEGFullRes\":{\"value\":{\"downloadURL\":\"https://cvws.icloud-content.com/synthetic/poster.jpg\"}}}}]}");
        List<AppleAlbumClient.ImageRef> images = new AppleAlbumClient(fake)
                .list("https://photos.icloud.com/shared/album/OPAQUE_123", ACTIVE);
        assert images.size() == 2;
        assert fake.requests.size() == 3;
        assert fake.bodies.get(2).contains("synthetic-next");
        assert fake.bodies.get(1).contains("DESCENDING");
        assert fake.requests.get(1).contains("publicAccessAuthToken=synthetic-access");
    }

    private static void cloudKitParsesReferenceShapedRenditions() throws Exception {
        FakeTransport fake = new FakeTransport();
        fake.add(200, "{\"results\":[{\"zoneID\":{\"zoneName\":\"CMM-SYNTHETIC\","
                + "\"ownerRecordName\":\"_synthetic-owner\",\"zoneType\":\"REGULAR_CUSTOM_ZONE\","
                + "\"ignoredNestedData\":{\"mustNot\":\"be echoed\"}},\"anonymousPublicAccess\":{"
                + "\"databasePartition\":\"https://p164-ckdatabasews.icloud.com:443\",\"token\":\"synthetic access/+\"}}]}");
        fake.add(200, "{\"records\":["
                + "{\"recordType\":\"CPLAsset\",\"fields\":{\"assetDate\":{\"value\":1}}},"
                + "{\"recordType\":\"CPLMaster\",\"recordName\":\"heic-master\",\"fields\":{"
                + "\"itemType\":{\"value\":\"public.heic\"},\"resOriginalFileType\":{\"value\":\"public.heic\"},"
                + "\"resOriginalRes\":{\"value\":{\"downloadURL\":\"https://cvws.icloud-content.com/synthetic/original.heic\"}},"
                + "\"resJPEGScreenRes\":{\"value\":{\"downloadURL\":\"https://cvws.icloud-content.com/synthetic/heic.jpg\"}},"
                + "\"resJPEGScreenWidth\":{\"value\":1536.0}}},"
                + "{\"recordType\":\"CPLMaster\",\"recordName\":\"generic-master\",\"fields\":{"
                + "\"resJPEGDisplayRes\":{\"value\":{\"downloadURL\":\"https://cvws.icloud-content.com/synthetic/display.jpg\"}},"
                + "\"resJPEGDisplayWidth\":{\"value\":\"2048\"},"
                + "\"resJPEGBrokenRes\":{\"value\":{\"downloadURL\":\"http://example.invalid/not-accepted.jpg\"}},"
                + "\"resJPEGBrokenWidth\":{\"value\":\"Infinity\"}}},"
                + "{\"recordType\":\"CPLMaster\",\"recordName\":\"jpeg-master\",\"fields\":{"
                + "\"itemType\":{\"value\":\"public.jpeg\"},\"resOriginalFileType\":{\"value\":\"public.jpeg\"},"
                + "\"resOriginalRes\":{\"value\":{\"downloadURL\":\"https://cvws.icloud-content.com/synthetic/original.jpg\"}}}},"
                + "{\"recordType\":\"CPLMaster\",\"recordName\":\"video-without-type\",\"fields\":{"
                + "\"resVidMedRes\":{\"value\":{\"downloadURL\":\"https://cvws.icloud-content.com/synthetic/video.mp4\"}},"
                + "\"resJPEGMedRes\":{\"value\":{\"downloadURL\":\"https://cvws.icloud-content.com/synthetic/poster.jpg\"}}}}]}");
        List<AppleAlbumClient.ImageRef> images = new AppleAlbumClient(fake)
                .list("https://photos.icloud.com/shared/album/OPAQUE_123", ACTIVE);
        assert images.size() == 3;
        assert images.get(0).uri.equals(URI.create("https://cvws.icloud-content.com/synthetic/heic.jpg"));
        assert images.get(1).uri.equals(URI.create("https://cvws.icloud-content.com/synthetic/display.jpg"));
        assert images.get(2).uri.equals(URI.create("https://cvws.icloud-content.com/synthetic/original.jpg"));
        assert fake.bodies.get(1).contains("REGULAR_CUSTOM_ZONE");
        assert !fake.bodies.get(1).contains("ignoredNestedData");
    }

    private static void cloudKitParsesCurrentPairedTemplateRenditions() throws Exception {
        FakeTransport fake = new FakeTransport();
        fake.add(200, "{\"results\":[{\"zoneID\":{\"zoneName\":\"CMM-SYNTHETIC\","
                + "\"ownerRecordName\":\"_synthetic-owner\",\"zoneType\":\"REGULAR_CUSTOM_ZONE\"},"
                + "\"anonymousPublicAccess\":{\"databasePartition\":\"https://p164-ckdatabasews.icloud.com:443\","
                + "\"token\":\"synthetic access/+\"}}]}");
        fake.add(200, "{\"records\":["
                + "{\"recordType\":\"CPLAsset\",\"recordName\":\"asset-edited\",\"fields\":{"
                + "\"masterRef\":{\"value\":{\"recordName\":\"master-edited\"}},"
                + "\"assetDate\":{\"value\":1718447400000},"
                + "\"resJPEGFullRes\":{\"value\":{\"downloadURL\":\"https://cvws.icloud-content.com/synthetic/${f}?sig=edited\"}},"
                + "\"resJPEGFullWidth\":{\"value\":2048}}},"
                + "{\"recordType\":\"CPLMaster\",\"recordName\":\"master-edited\",\"fields\":{"
                + "\"itemType\":{\"value\":\"public.heic\"},"
                + "\"resJPEGMedRes\":{\"value\":{\"downloadURL\":\"https://cvws.icloud-content.com/synthetic/master-${f}\"}}}},"
                + "{\"recordType\":\"CPLAsset\",\"recordName\":\"asset-original\",\"fields\":{"
                + "\"masterRef\":{\"value\":{\"recordName\":\"master-original\"}}}},"
                + "{\"recordType\":\"CPLMaster\",\"recordName\":\"master-original\",\"fields\":{"
                + "\"itemType\":{\"value\":\"public.jpeg\"},\"resOriginalFileType\":{\"value\":\"public.jpeg\"},"
                + "\"resOriginalRes\":{\"value\":{\"downloadURL\":\"https://cvws.icloud-content.com/synthetic/%24%7Bf%7D?sig=original\"}}}},"
                + "{\"recordType\":\"CPLAsset\",\"recordName\":\"asset-video\",\"fields\":{"
                + "\"masterRef\":{\"value\":{\"recordName\":\"master-video\"}}}},"
                + "{\"recordType\":\"CPLMaster\",\"recordName\":\"master-video\",\"fields\":{"
                + "\"itemType\":{\"value\":\"com.apple.quicktime-movie\"},"
                + "\"resJPEGMedRes\":{\"value\":{\"downloadURL\":\"https://cvws.icloud-content.com/synthetic/poster-${f}\"}}}}]}");
        List<AppleAlbumClient.ImageRef> images = new AppleAlbumClient(fake)
                .list("https://photos.icloud.com/shared/album/OPAQUE_123", ACTIVE);
        assert images.size() == 2;
        assert images.get(0).id.equals("asset-edited");
        assert images.get(0).uri.equals(URI.create("https://cvws.icloud-content.com/synthetic/photo.jpg?sig=edited"));
        assert images.get(0).metadata.captureMillis.equals(1718447400000L);
        assert images.get(1).id.equals("asset-original");
        assert images.get(1).uri.equals(URI.create("https://cvws.icloud-content.com/synthetic/photo.jpg?sig=original"));
        assert fake.requests.get(0).contains("sharing_url_key=OPAQUE_123");
        assert fake.requests.get(1).contains("sharing_url_key=OPAQUE_123");
        assert fake.requests.get(1).contains("publicAccessAuthToken=synthetic+access%2F%2B");
    }

    private static void cloudKitRejectsOversizedZone() throws Exception {
        FakeTransport fake = new FakeTransport();
        StringBuilder oversized = new StringBuilder();
        for (int i = 0; i < 257; i++) oversized.append('z');
        fake.add(200, "{\"results\":[{\"zoneID\":{\"zoneName\":\"" + oversized + "\"},"
                + "\"anonymousPublicAccess\":{\"databasePartition\":\"https://p99-ckdatabasews.icloud.com\","
                + "\"token\":\"synthetic-access\"}}]}");
        try {
            new AppleAlbumClient(fake).list("https://photos.icloud.com/shared/album/OPAQUE_123", ACTIVE);
            throw new AssertionError("oversized zone accepted");
        } catch (AlbumException expected) { assert expected.code == AlbumException.Code.INVALID_RESPONSE; }
    }

    private static void cloudKitRejectsRepeatedMarker() throws Exception {
        FakeTransport fake = new FakeTransport();
        fake.add(200, "{\"results\":[{\"zoneID\":{\"zoneName\":\"synthetic-zone\"},"
                + "\"anonymousPublicAccess\":{\"databasePartition\":\"https://p99-ckdatabasews.icloud.com\",\"token\":\"synthetic-access\"}}]}");
        fake.add(200, "{\"records\":[],\"continuationMarker\":\"same-marker\"}");
        fake.add(200, "{\"records\":[],\"continuationMarker\":\"same-marker\"}");
        try {
            new AppleAlbumClient(fake).list("https://photos.icloud.com/shared/album/OPAQUE_123", ACTIVE);
            throw new AssertionError("repeated continuation marker accepted");
        } catch (AlbumException expected) { assert expected.code == AlbumException.Code.INVALID_RESPONSE; }
    }

    private static void cloudKitRejectsAuthenticationRequired() throws Exception {
        FakeTransport fake = new FakeTransport();
        fake.add(200, "{\"results\":[{\"requiresAuthentication\":true,\"zoneID\":{}}]}");
        try {
            new AppleAlbumClient(fake).list("https://photos.icloud.com/shared/album/OPAQUE_123", ACTIVE);
            throw new AssertionError("authenticated album accepted");
        } catch (AlbumException expected) { assert expected.code == AlbumException.Code.NOT_PUBLIC; }
    }

    private static void durationSettingIsBoundedBeforeScheduling() {
        assert AppleSharedAlbumPlugin.boundedIntervalSeconds(null) == 30;
        assert AppleSharedAlbumPlugin.boundedIntervalSeconds("300") == 30;
        assert AppleSharedAlbumPlugin.boundedIntervalSeconds(Double.NaN) == 30;
        assert AppleSharedAlbumPlugin.boundedIntervalSeconds(Double.POSITIVE_INFINITY) == 30;
        assert AppleSharedAlbumPlugin.boundedIntervalSeconds(Long.MIN_VALUE) == 5;
        assert AppleSharedAlbumPlugin.boundedIntervalSeconds(7.4d) == 5;
        assert AppleSharedAlbumPlugin.boundedIntervalSeconds(7.5d) == 10;
        assert AppleSharedAlbumPlugin.boundedIntervalSeconds(300) == 300;
        assert AppleSharedAlbumPlugin.boundedIntervalSeconds(1800L) == 1800;
        assert AppleSharedAlbumPlugin.boundedIntervalSeconds(3598.0d) == 3600;
        assert AppleSharedAlbumPlugin.boundedIntervalSeconds(3600) == 3600;
        assert AppleSharedAlbumPlugin.boundedIntervalSeconds(Long.MAX_VALUE) == 3600;
    }

    private static void overlayOptionsAreBoundedAndPrivateByDefault() {
        OverlayOptions defaults = OverlayOptions.from(Collections.<String, Object>emptyMap());
        assert !defaults.clock && !defaults.currentDate && !defaults.photoDate && !defaults.location;
        assert OverlayOptions.TOP_RIGHT.equals(defaults.position);
        assert "#FAFAFA".equals(defaults.color) && defaults.scale == 100;

        Map<String, Object> settings = new HashMap<>();
        settings.put("showClock", true); settings.put("clock24Hour", true); settings.put("clockSeconds", true);
        settings.put("showCurrentDate", true); settings.put("showPhotoDate", true); settings.put("showLocation", true);
        settings.put("overlayPosition", "<script>bad corner</script>");
        settings.put("overlayColor", "red;background:url(https://example.invalid)");
        settings.put("overlayFont", "url(evil)"); settings.put("overlayWeight", "900;display:none");
        settings.put("overlayScale", Double.POSITIVE_INFINITY);
        OverlayOptions bounded = OverlayOptions.from(settings);
        assert bounded.clock && bounded.use24Hour && bounded.seconds && bounded.currentDate && bounded.photoDate && bounded.location;
        assert OverlayOptions.TOP_RIGHT.equals(bounded.position);
        assert "#FAFAFA".equals(bounded.color) && "sans-serif".equals(bounded.font) && bounded.weight == 300;
        assert bounded.scale == 100;
    }

    private static void overlayPositionsAndUnavailableMetadataRenderSafely() throws Exception {
        String[] positions = {OverlayOptions.TOP_LEFT, OverlayOptions.TOP_RIGHT,
                OverlayOptions.BOTTOM_LEFT, OverlayOptions.BOTTOM_RIGHT};
        for (String position : positions) {
            Map<String, Object> settings = new HashMap<>();
            settings.put("showClock", true); settings.put("showPhotoDate", true); settings.put("showLocation", true);
            settings.put("overlayPosition", position);
            String html = SlideshowHtml.image(new byte[32], "Fit whole photo", OverlayOptions.from(settings),
                    PhotoMetadata.EMPTY, new Date(0), Locale.US, TimeZone.getTimeZone("UTC"));
            assert html.contains("class=overlay") && html.contains("class=clock");
            assert !html.contains("class=meta") && !html.contains("class=location") && !html.contains("class=capture");
            assert !html.contains(">undefined<") && !html.contains(">null<");
            if (position.startsWith("Top")) assert html.contains("top:0"); else assert html.contains("bottom:0");
            if (position.endsWith("left")) assert html.contains("left:0"); else assert html.contains("right:0");
        }
        String escaped = SlideshowHtml.message("<img src=x onerror=alert(1)> & \"private\"");
        assert !escaped.contains("<img src=x") && escaped.contains("&lt;img") && escaped.contains("&amp;");
    }

    private static void overlayFormattingHooksAreDeterministic() throws Exception {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        Date instant = new Date(1718447405000L); // 2024-06-15 10:30:05Z
        assert "10:30".equals(SlideshowHtml.formatTime(instant, true, false, utc));
        assert "10:30:05".equals(SlideshowHtml.formatTime(instant, true, true, utc));
        assert "10:30 AM".equals(SlideshowHtml.formatTime(instant, false, false, utc));
        assert SlideshowHtml.formatCurrentDate(instant, Locale.US, utc).contains("2024");
        PhotoMetadata metadata = new PhotoMetadata(instant.getTime(), -37.8136, 144.9631);
        assert metadata.formattedCaptureDate(Locale.US, utc).contains("2024");
        assert "37.81360° S, 144.96310° E".equals(metadata.formattedLocation());
        assert PhotoMetadata.parseAppleDate("2024-06-15T10:30:05Z").equals(instant.getTime());
        assert PhotoMetadata.parseAppleDate("not a date") == null;
    }

    private static void extractsBoundedExifMetadataLocally() throws Exception {
        PhotoMetadata metadata = ExifMetadata.read(syntheticExifJpeg());
        assert metadata.captureMillis != null;
        assert Math.abs(metadata.latitude - 37.8083333333) < 0.000001;
        assert Math.abs(metadata.longitude + 122.4041666667) < 0.000001;
        assert metadata.formattedLocation().length() < 80;
        assert ExifMetadata.read(new byte[] {1, 2, 3}).captureMillis == null;
        byte[] malformed = syntheticExifJpeg();
        le32(malformed, 12 + 4, Integer.MAX_VALUE);
        assert ExifMetadata.read(malformed).captureMillis == null;
        assert new PhotoMetadata(null, 91d, 181d).formattedLocation() == null;
    }

    private static byte[] syntheticExifJpeg() {
        byte[] tiff = new byte[178];
        tiff[0] = 'I'; tiff[1] = 'I'; le16(tiff, 2, 42); le32(tiff, 4, 8);
        le16(tiff, 8, 2);
        entry(tiff, 10, 0x8769, 4, 1, 38);
        entry(tiff, 22, 0x8825, 4, 1, 76);
        le32(tiff, 34, 0);
        le16(tiff, 38, 1);
        entry(tiff, 40, 0x9003, 2, 20, 56);
        le32(tiff, 52, 0);
        byte[] date = "2024:06:15 10:30:05\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(date, 0, tiff, 56, date.length);
        le16(tiff, 76, 4);
        entryInlineAscii(tiff, 78, 1, 'N');
        entry(tiff, 90, 2, 5, 3, 130);
        entryInlineAscii(tiff, 102, 3, 'W');
        entry(tiff, 114, 4, 5, 3, 154);
        le32(tiff, 126, 0);
        rational(tiff, 130, 37, 1); rational(tiff, 138, 48, 1); rational(tiff, 146, 30, 1);
        rational(tiff, 154, 122, 1); rational(tiff, 162, 24, 1); rational(tiff, 170, 15, 1);
        byte[] jpeg = new byte[192];
        jpeg[0] = (byte) 0xff; jpeg[1] = (byte) 0xd8; jpeg[2] = (byte) 0xff; jpeg[3] = (byte) 0xe1;
        jpeg[4] = 0; jpeg[5] = (byte) 186;
        byte[] exif = {'E','x','i','f',0,0}; System.arraycopy(exif, 0, jpeg, 6, exif.length);
        System.arraycopy(tiff, 0, jpeg, 12, tiff.length);
        jpeg[190] = (byte) 0xff; jpeg[191] = (byte) 0xd9;
        return jpeg;
    }

    private static void entry(byte[] out, int at, int tag, int type, int count, int value) {
        le16(out, at, tag); le16(out, at + 2, type); le32(out, at + 4, count); le32(out, at + 8, value);
    }
    private static void entryInlineAscii(byte[] out, int at, int tag, char value) {
        le16(out, at, tag); le16(out, at + 2, 2); le32(out, at + 4, 2); out[at + 8] = (byte) value;
    }
    private static void rational(byte[] out, int at, int numerator, int denominator) {
        le32(out, at, numerator); le32(out, at + 4, denominator);
    }
    private static void le16(byte[] out, int at, int value) { out[at] = (byte) value; out[at + 1] = (byte) (value >>> 8); }
    private static void le32(byte[] out, int at, int value) {
        out[at] = (byte) value; out[at + 1] = (byte) (value >>> 8);
        out[at + 2] = (byte) (value >>> 16); out[at + 3] = (byte) (value >>> 24);
    }

    private static void enforcesSafeHostsAndInlineBudget() throws Exception {
        SafeHttp.validateAppleUri(URI.create("https://p01.icloud.com/synthetic"));
        SafeHttp.validateAppleUri(URI.create("https://cvws.icloud-content.com/synthetic"));
        try {
            SafeHttp.validateAppleUri(URI.create("https://example.com/synthetic"));
            throw new AssertionError("non-Apple host accepted");
        } catch (AlbumException expected) { assert expected.code == AlbumException.Code.INVALID_RESPONSE; }
        String html = SlideshowHtml.image(new byte[PhotoEncoder.MAX_ENCODED_BYTES], "Fit whole photo");
        assert html.getBytes(StandardCharsets.UTF_8).length < SlideshowHtml.SDK_INLINE_LIMIT;
        try {
            SlideshowHtml.image(new byte[400 * 1024], "Fill screen");
            throw new AssertionError("oversized inline document accepted");
        } catch (AlbumException expected) { assert expected.code == AlbumException.Code.TOO_LARGE; }
    }

    private static void sanitizesEveryPublicError() {
        String sensitive = "OPAQUE_SECRET_TOKEN";
        for (AlbumException.Code code : AlbumException.Code.values()) {
            String message = new AlbumException(code).publicMessage();
            assert !message.contains(sensitive);
            assert !message.contains("http");
            assert !message.contains("Exception");
        }
    }

    private static void jsonRoundTripsAndBoundsDepth() throws Exception {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("text", "quote \" and newline\n");
        List<Object> list = new ArrayList<>(); list.add(Boolean.TRUE); list.add(42); value.put("list", list);
        Map<String, Object> parsed = SafeJson.object(SafeJson.parse(SafeJson.encode(value).getBytes(StandardCharsets.UTF_8)));
        assert "quote \" and newline\n".equals(parsed.get("text"));
        StringBuilder deep = new StringBuilder(); for (int i = 0; i < 30; i++) deep.append('[');
        for (int i = 0; i < 30; i++) deep.append(']');
        try {
            SafeJson.parse(deep.toString().getBytes(StandardCharsets.UTF_8));
            throw new AssertionError("deep JSON accepted");
        } catch (AlbumException expected) { assert expected.code == AlbumException.Code.TOO_LARGE; }
    }

    private static void lifecycleCancellationDoesNotWaitForMetadataIo() throws Exception {
        BlockingTransport transport = new BlockingTransport();
        AppleSharedAlbumPlugin plugin = new AppleSharedAlbumPlugin(transport);
        Map<String, Object> settings = new HashMap<>();
        settings.put("albumUrl", "https://www.icloud.com/sharedalbum/#B12abcDEF");
        plugin.start(new FakeHost(), settings);
        assert transport.entered.await(2, TimeUnit.SECONDS) : "metadata request did not start";
        long started = System.nanoTime();
        plugin.stop();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assert transport.cancelled : "stop did not cancel active transport";
        assert elapsedMillis < 500 : "stop waited for metadata I/O: " + elapsedMillis + " ms";
    }

    /** Discovery cannot depend on Apple or a configured URL: KS lists a renderer only after this call. */
    private static void publishesSelectableScreensaverBeforeAlbumIo() {
        AppleSharedAlbumPlugin plugin = new AppleSharedAlbumPlugin(new FakeTransport());
        FakeHost host = new FakeHost();
        plugin.start(host, Collections.<String, Object>emptyMap());
        assert "apple_album".equals(host.screensaverKey);
        assert "Apple Shared Album".equals(host.screensaverTitle);
        assert host.screensaverHtml != null && host.screensaverHtml.contains("Add a public Apple Shared Album link");
        plugin.stop();
        assert "apple_album".equals(host.removedScreensaverKey);
    }

    private static void rejectsUrl(String value) throws Exception {
        try { AlbumUrl.parse(value); throw new AssertionError("invalid URL accepted"); }
        catch (AlbumException expected) { assert expected.code == AlbumException.Code.INVALID_URL; }
    }

    private static final class FakeTransport implements AppleAlbumClient.Transport {
        final Deque<AppleAlbumClient.Response> responses = new ArrayDeque<>();
        final List<String> requests = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();

        void add(int status, String body) {
            add(new AppleAlbumClient.Response(status, Collections.<String, String>emptyMap(),
                    body.getBytes(StandardCharsets.UTF_8), URI.create("https://ckdatabasews.icloud.com/synthetic")));
        }
        void add(AppleAlbumClient.Response response) { responses.add(response); }
        @Override public AppleAlbumClient.Response post(URI uri, String json, AppleAlbumClient.Cancellation cancel) throws AlbumException {
            cancel.check(); requests.add(uri.toASCIIString()); bodies.add(json);
            if (responses.isEmpty()) throw new AssertionError("unexpected request");
            return responses.remove();
        }
        @Override public AppleAlbumClient.Response getImage(URI uri, AppleAlbumClient.Cancellation cancel) {
            throw new AssertionError("unexpected image request");
        }
        @Override public void cancelAll() { }
    }

    private static final class BlockingTransport implements AppleAlbumClient.Transport {
        final CountDownLatch entered = new CountDownLatch(1);
        volatile boolean cancelled;

        @Override public AppleAlbumClient.Response post(URI uri, String json, AppleAlbumClient.Cancellation cancel)
                throws AlbumException {
            entered.countDown();
            while (!cancelled) {
                cancel.check();
                try { Thread.sleep(10); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AlbumException(AlbumException.Code.CANCELLED);
                }
            }
            throw new AlbumException(AlbumException.Code.CANCELLED);
        }

        @Override public AppleAlbumClient.Response getImage(URI uri, AppleAlbumClient.Cancellation cancel)
                throws AlbumException {
            throw new AssertionError("unexpected image request");
        }

        @Override public void cancelAll() { cancelled = true; }
    }

    private static final class FakeHost implements PluginHost {
        String screensaverKey;
        String screensaverTitle;
        String screensaverHtml;
        String removedScreensaverKey;
        @Override public void publishScreensaver(String key, String title, String html) {
            screensaverKey = key; screensaverTitle = title; screensaverHtml = html;
        }
        @Override public void removeScreensaver(String key) { removedScreensaverKey = key; }
        @Override public void status(String message, boolean error) { }
        @Override public void showWindow(String title, String message, String buttonLabel) { }
        @Override public void hideWindow() { }
        @Override public void log(String message) { }
    }
}
