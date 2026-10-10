# Apple Shared Album for Kiosk Satellite

Display still images from a **public** Apple Shared Album in the stock [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite) screensaver. The plugin uses no Apple ID, password, cookie, API key, Home Assistant integration, or companion server.

## Requirements

- **Kiosk Satellite 2026.10.18 or newer**, with SDK 1 plugin screensavers.
- Android 7.0 (API 24) or newer.
- An Apple Shared Album with its public website enabled.
- One of these complete HTTPS links:

  ```text
  https://www.icloud.com/sharedalbum/#TOKEN
  https://photos.icloud.com/shared/album/OPAQUE_ID
  ```

The first form uses Apple's legacy `sharedstreams` service. The second uses anonymous public CloudKit. Invite links, approval flows, iCloud Links (`share.icloud.com/photos/...`), private albums, and anything requiring sign-in are deliberately rejected.

## Install and configure

1. In Kiosk Satellite, enable **Plugin Manager**.
2. Add this GitHub repository, review the source and manifest, and install a published stable release. For development, use **Install from ZIP** with the package from `dist/`.
3. Enable **Apple Shared Album** and paste the complete public album link.
4. Under **Screensaver**, select **Apple Shared Album (Apple Shared Album)**.

The settings also control display duration (5 seconds–1 hour in 5-second steps, 30 seconds by default), order, and fit versus fill. Existing saved durations remain unchanged after updating. **Reconnect and refresh album** discards the current list and reconnects immediately. The normal list refresh interval is 15 minutes.

## Privacy and security

The kiosk connects directly to Apple. The plugin has no analytics, telemetry, account login, cookies, disk photo cache, or third-party service. Images and album metadata remain in memory and are discarded as sessions change.

The album URL grants public read access and should be treated as sensitive. **SDK 1 only offers a regular string setting for this value:** Kiosk Satellite stores it as an ordinary plugin setting and displays it in the plugin UI. Protect Remote Admin and backups containing plugin settings. Disable the album's public website in Photos to revoke access.

All requests require HTTPS and an Apple-controlled `icloud.com` or `icloud-content.com` host. Every API base, media URL, and redirect hop is validated. Requests have connection/read timeouts and strict bounds on redirects, response bytes, pages, records, items, image dimensions, decoded pixels, rendered image bytes, and the SDK's 512 KiB inline HTML limit. Lifecycle changes cancel active connections; stale workers cannot publish to a replacement session. User-visible errors are fixed messages and never include exceptions, request URLs, album tokens/IDs, server bodies, or album metadata.

See [SECURITY.md](SECURITY.md) and [docs/privacy.md](docs/privacy.md) for the threat model and disclosure guidance.

## Protocol and rendering architecture

`AppleAlbumClient` parses the supplied link and chooses one implementation behind a common interface:

- `LegacyProtocol`: `webstream` → bounded 25-item `webasseturls` batches, including Apple's validated HTTP 330 partition handoff and response filtering to the checksums requested by each batch.
- `CloudKitProtocol`: public `records/resolve` → anonymous shared-zone `records/query`, with a bounded reconstructed zone ID, bounded continuation-marker pagination, repeated-marker rejection, current `CPLAsset`/`CPLMaster` pairing, edited-photo rendition support, fixed filename-template expansion, video exclusion, and validated web-safe still-image rendition selection.

Both produce validated Apple image references. `SafeHttp` performs native Android HTTPS transport. `PhotoEncoder` bounds native bitmap decoding, scales to at most 1920 px on the longest edge, and emits a JPEG no larger than 360 KiB. `SlideshowHtml` base64-embeds that JPEG in an isolated inline screensaver document and verifies the complete UTF-8 document stays below 512 KiB. Videos and unsupported image renditions are skipped.

## Build and test

The release toolchain is pinned to Ubuntu 24.04, Temurin 21, Android platform 35, Android build-tools 35.0.0, Java `--release 8`, and Python 3 as supplied by the runner. Locally, set `JAVA_HOME` and `ANDROID_HOME`, then run:

```sh
python3 tools/test.py
python3 tools/build.py --android-platform 35
python3 tools/inspect_package.py
python3 tools/audit_repository.py
```

The build writes a deterministic versioned ZIP such as `dist/apple-shared-album-0.1.3.zip`, matching release manifest, SHA-256 file, and compile-time SDK JAR. The ZIP contains exactly the Plugin Manager root files (`kiosk-satellite-plugin.json`, `plugin.jar`, and `LICENSE`) plus any supported `assets/` files; `NOTICE` remains in the repository and is linked below for upstream attribution. ZIP entries have fixed timestamps and ordering. Toolchain changes can still alter DEX bytes, so release assets must come from one workflow run.

Tests use only synthetic JSON, fake transport responses, and reserved fake paths. They never contact an album. See [docs/development.md](docs/development.md) for the verification matrix and [docs/research.md](docs/research.md) for source/version/license provenance.

## Limitations and live gate

- Apple's public album protocols are undocumented and may change.
- Still images only; videos and Live Photo motion are skipped.
- Albums are capped at 1,000 displayable images; CloudKit traversal is capped at 20 pages and 2,500 records.
- No image is cached on disk. Restarting or refreshing needs network access.
- Automated tests prove parsing, protocol shaping, safety bounds, pagination, packaging, and compilation with synthetic fixtures. **A maintainer must still install the ZIP on a Kiosk Satellite 2026.10.18+ device and test owner-provided public albums in both URL formats before publishing the first release.** Never put a live album URL in an issue, fixture, CI variable, commit, or log.

## License

[Apache-2.0](LICENSE). See [NOTICE](NOTICE) for upstream attribution.
