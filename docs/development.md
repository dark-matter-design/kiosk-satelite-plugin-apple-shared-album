# Development and verification

## Toolchain

The current upstream template workflow uses Ubuntu 24.04, `actions/checkout@v4`, `actions/setup-java@v4` with Temurin 21, `android-actions/setup-android@v4`, Android platform 35, and build-tools 35.0.0. The builder compiles Java with `javac --release 8`, targets manifest `minAndroidSdk` 24, and runs D8 with `--min-api 24`. Python 3 is required; upstream does not pin its patch version.

## Local verification

```sh
python3 tools/test.py
python3 tools/build.py --android-platform 35
python3 tools/check_upstream_screensaver.py /path/to/kiosk-satellite
python3 tools/inspect_package.py
python3 tools/audit_repository.py
```

`tools/test.py` compiles the SDK, plugin, and tests and runs synthetic protocol tests plus the upstream Python tooling tests. It does not make network requests. Its lifecycle fixture proves that `start` publishes the `apple_album` renderer synchronously even with no album URL, and that `stop` removes it. The protocol fixtures cover legacy string/number derivative metadata, requested-checksum filtering and bounded Apple-only 330 handoff; CloudKit zone reconstruction and bounds; `sharing_url_key`; paired `CPLAsset`/`CPLMaster` records; edited renditions stored on the asset; literal and percent-encoded `${f}` filename templates; `resJPEGScreenRes` and generic validated `resJPEG*Res` renditions; HEIC originals with JPEG derivatives; web-safe originals; and missing `itemType`. Duration tests cover SDK-style `Number`/`Double` values, non-finite values, fractional values, and 64-bit extremes. `check_upstream_screensaver.py` compares the vendored SDK byte-for-byte and checks the pinned application source paths that accept the capability, register the renderer, derive `plugin:apple-shared-album:apple_album`, and expose it to settings. `inspect_package.py` verifies the Plugin Manager entry allowlist and uniqueness rule, package names, fixed timestamps, matching manifests, checksum, DEX presence, public manifest entry class, direct `KioskPlugin` implementation, and size limits. The installable ZIP includes `LICENSE`; `NOTICE` remains in the repository and release-tag README because Plugin Manager rejects it as a package entry. `audit_repository.py` rejects credential-like files and scans tracked text plus Git history for common secret markers and live Apple album URLs.

### Kiosk Satellite 2026.10.18 selection semantics

The manifest capability permits `PluginHost.publishScreensaver`; it does not create a static dropdown entry. The enabled plugin must publish a renderer in its live session. Kiosk Satellite then derives the stable mode `plugin:PLUGIN_ID:KEY` and label `Title (Plugin Name)`. This plugin publishes `plugin:apple-shared-album:apple_album` before scheduling any album work, so invalid, empty, offline, or still-loading albums remain selectable and render a sanitized status document.

The native host batches publication for 250 ms and sends the live catalog to Flutter. The on-device Screensaver settings page listens directly to that catalog and rebuilds. In the same upstream commit, Remote Admin's settings schema refreshes on `SettingOptionsChanged`, but plugin renderer publication does not emit that event. A Remote Admin page whose settings schema was loaded before plugin initialization therefore keeps only the built-in options until the page refetches settings. Reload Remote Admin after the plugin row says **Running**, or select the mode on-device. Restart Kiosk Satellite only when the plugin row reports a failed/unclean session; ordinary enable and update paths start it without a restart.

SDK 1 number settings are finite JSON numbers constrained to the manifest's inclusive `min`/`max` and exact `step`. The display-duration manifest exposes 5–3,600 seconds in 5-second steps with a 30-second default (719 divisions, below SDK 1's 10,000-division limit). Runtime code independently rejects non-finite/non-numeric input, clamps finite values before arithmetic, rounds to the nearest 5-second step, clamps the normalized result again, and stores the scheduled delay as `long`. Every value accepted by the earlier 5–300 second range remains valid and unchanged.

## Manual device gate

Before publishing v0.1.3:

1. Install the local ZIP on Kiosk Satellite 2026.10.18 or newer.
2. Verify start, rotation, fit/fill, refresh, disable, re-enable, URL replacement, and uninstall.
3. With owner permission, test one public album of each supported URL format without recording its link, traffic, metadata, or images.
4. Verify private, invite-only, approval-required, revoked, empty, oversized, slow, and offline states show only sanitized status text.
5. Confirm disabling or replacing the plugin stops network traffic and a stale image never reappears.

The repository and automated suite must continue to contain synthetic fixtures only.
