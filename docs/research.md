# Tooling, protocol, and license research

Research snapshot: 2026-10-10.

## Current Kiosk Satellite template

- Source: <https://github.com/jxlarrea/kiosk-satellite-plugin-hello-world>
- Inspected commit: `faa4385e7d6dce28d6d7dd604ec83a810adb9ef9` (2026-10-08)
- License: Apache-2.0 (`LICENSE` in that repository)
- SDK: schema 1 / API 1, Android minimum API 24; current documentation requires JDK 17+ and an Android SDK.
- Exact workflow: Ubuntu 24.04; `actions/checkout@v4`; `actions/setup-java@v4`, Temurin 21; `android-actions/setup-android@v4`; `platforms;android-35`; `build-tools;35.0.0`; Java source/target `--release 8`.
- Imported here: SDK interfaces and Python build/manifest/asset/test tooling, with plugin-specific adaptations and attribution in NOTICE.
- Current Kiosk Satellite release observed: `2026.10.18`, published 2026-10-09. This is the documented minimum for the first plugin release so the release targets the exact current SDK/template baseline inspected.
- Kiosk Satellite application license: CC BY-NC-ND 4.0 (`NOASSERTION` in GitHub metadata), with `PLUGIN-EXCEPTION.md` expressly allowing separately distributed independent plugins under their chosen license. The published SDK interfaces are separately Apache-2.0. No application implementation code is included here.

## SDK number-setting semantics

- Source: <https://github.com/jxlarrea/kiosk-satellite>
- Inspected application commit: `abc70b0839842e251b8d3917bbbb44569036556c` (2026-10-10 snapshot).
- Relevant source: [`PluginManifest.kt`](https://github.com/jxlarrea/kiosk-satellite/blob/abc70b0839842e251b8d3917bbbb44569036556c/app/android/app/src/main/kotlin/me/jxl/kiosk_satellite/plugins/PluginManifest.kt) and [`plugin_settings.dart`](https://github.com/jxlarrea/kiosk-satellite/blob/abc70b0839842e251b8d3917bbbb44569036556c/app/lib/ui/plugin_settings.dart).
- Semantics: a `number` default/value must be a finite JSON number within inclusive `min`/`max`, aligned exactly to `step`; `(max-min)/step` must be an integer from 1 through 10,000. The UI uses a slider with that many divisions, rounds to the nearest step, and persists a numeric value that commonly crosses the platform channel as `Double`.
- Rendering source: [`PluginScreensavers.kt`](https://github.com/jxlarrea/kiosk-satellite/blob/abc70b0839842e251b8d3917bbbb44569036556c/app/android/app/src/main/kotlin/me/jxl/kiosk_satellite/plugins/PluginScreensavers.kt) and [`plugin_screensaver.dart`](https://github.com/jxlarrea/kiosk-satellite/blob/abc70b0839842e251b8d3917bbbb44569036556c/app/lib/ui/plugin_screensaver.dart) confirm that a new inline publication updates the mounted renderer immediately; Kiosk Satellite does not impose a 300-second rotation period.
- Discovery source: [`PluginManifest.kt`](https://github.com/jxlarrea/kiosk-satellite/blob/abc70b0839842e251b8d3917bbbb44569036556c/app/android/app/src/main/kotlin/me/jxl/kiosk_satellite/plugins/PluginManifest.kt), [`PluginBridge.kt`](https://github.com/jxlarrea/kiosk-satellite/blob/abc70b0839842e251b8d3917bbbb44569036556c/app/android/app/src/main/kotlin/me/jxl/kiosk_satellite/plugins/PluginBridge.kt), [`plugin_manager.dart`](https://github.com/jxlarrea/kiosk-satellite/blob/abc70b0839842e251b8d3917bbbb44569036556c/app/lib/managers/plugins/plugin_manager.dart), [`settings_manager.dart`](https://github.com/jxlarrea/kiosk-satellite/blob/abc70b0839842e251b8d3917bbbb44569036556c/app/lib/managers/settings/settings_manager.dart), and [`settings_screen.dart`](https://github.com/jxlarrea/kiosk-satellite/blob/abc70b0839842e251b8d3917bbbb44569036556c/app/lib/ui/settings_screen.dart). The manifest capability gates host access; the live `publishScreensaver` call creates the catalog item; Flutter names it `plugin:PLUGIN_ID:KEY`; settings appends live catalog keys; and the on-device screen listens to catalog changes. Remote Admin settings updates are driven by `SettingOptionsChanged`, which this application commit does not emit for plugin screensaver catalog changes, so a settings page loaded before publication needs a reload/refetch.
- License: application source is CC BY-NC-ND 4.0 with `PLUGIN-EXCEPTION.md`; inspected for interoperability only. No application implementation was copied.

## Exact local verification tools

- macOS 26.6.2 arm64.
- Python 3.9.6 (PSF license family; system-provided interpreter).
- Homebrew OpenJDK 21.0.12.1, `GPL-2.0-only WITH Classpath-exception-2.0` per Homebrew metadata.
- Android command-line tools cask `15859902`, `sdkmanager` 22.0, distributed under the Android SDK license (`android-sdk-license`).
- Android SDK Platform 15 / API 35, package revision 2, and Android build-tools 35.0.0, distributed under the Android SDK license.
- D8 8.6.2-dev, build `abaab469b5ebd4dd2bb91ba0ed6f45277faae4ca` (part of Android build-tools; R8 source is BSD-3-Clause).

## Synology architecture reference

- Source: <https://github.com/petergerdes/kiosk-satellite-synology-photos-plugin>
- Inspected commit: `2a09104ce9215e63b7e89de64168300dc3c80b0d` (2026-10-02)
- Plugin license: GPL-3.0-only. Its separately identified SDK/tooling is Apache-2.0.
- Use: architecture/lifecycle/package review only. No GPL implementation source was copied or adapted. This project remains Apache-2.0.

## Permissive protocol references

- <https://github.com/home-screens/home-screens>, commit `bda99d5c388de1fa5e34abccba94ab7480626748`, MIT. Consulted for interoperability observations around legacy sharedstreams and anonymous CloudKit resolution/query flow. Its synthetic fixtures are documented as modeled on real legacy responses and CloudKit responses captured 2026-07-09: legacy derivative dimensions/file sizes are strings, CloudKit partitions may include `:443`, query pages contain paired `CPLAsset`/`CPLMaster` records, HEIC masters expose JPEG renditions, and JPEG masters may expose only a web-safe original. Relevant source: [`icloud-album.ts`](https://github.com/home-screens/home-screens/blob/bda99d5c388de1fa5e34abccba94ab7480626748/src/lib/icloud-album.ts), [`icloud-link.ts`](https://github.com/home-screens/home-screens/blob/bda99d5c388de1fa5e34abccba94ab7480626748/src/lib/icloud-link.ts), and their tests.
- <https://github.com/starbrightlab/immortal>, commit `640ed8b43e3c6f34b0de80dd62bd795171cc668d`, MIT. Consulted for independent confirmation of the two URL families, numeric legacy still-image derivative keys, generic `res*Res` CloudKit rendition naming with sibling width fields, and video-track exclusion. Relevant source: [`RemoteAlbum.kt`](https://github.com/starbrightlab/immortal/blob/640ed8b43e3c6f34b0de80dd62bd795171cc668d/app/src/main/java/com/immortal/launcher/RemoteAlbum.kt) and [`RemoteAlbumTest.kt`](https://github.com/starbrightlab/immortal/blob/640ed8b43e3c6f34b0de80dd62bd795171cc668d/app/src/test/java/com/immortal/launcher/RemoteAlbumTest.kt).
- <https://github.com/infinc/dashboard-for-android>, commit `d4c4ae50a450ec6174860a7eb4285e88058cdeae` (2026-10-06), MIT. Consulted for an independently maintained Android implementation of current public Shared Collections. Its `PhotoRepository.kt` confirms that query responses pair `CPLAsset` records to `CPLMaster` through `masterRef`, edited-photo `resJPEGFullRes` can live on `CPLAsset`, signed `downloadURL` values contain a `${f}` filename template that must be replaced before use, and current requests carry `sharing_url_key`. No source was copied.
- Independent field evidence: immortal commit [`55e9f1a0`](https://github.com/starbrightlab/immortal/commit/55e9f1a0cc564b2e675eb8e2c45baf8d0953c3cf) records a real 116-photo legacy album taking about 14 seconds for `webstream`; this plugin retains a bounded timeout but raises metadata reads from 20 to 30 seconds while keeping the 10-second connect and 20-second image-read bounds.

These references are MIT licensed; their license files were inspected at the cited commits. The Java protocol code and synthetic fixtures in this repository are original, shape-compatible implementations rather than copied source or captured owner data. No live/private album or Apple album endpoint was queried during research or automated verification. Apple publishes no supported public Shared Album API contract; the feature is therefore best-effort interoperability with public anonymous web endpoints.
