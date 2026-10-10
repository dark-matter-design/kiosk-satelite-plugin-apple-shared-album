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

- <https://github.com/home-screens/home-screens>, commit `bda99d5c388de1fa5e34abccba94ab7480626748`, MIT. Consulted for interoperability observations around legacy sharedstreams and anonymous CloudKit resolution/query flow.
- <https://github.com/starbrightlab/immortal>, commit `640ed8b43e3c6f34b0de80dd62bd795171cc668d`, MIT. Consulted for independent confirmation of the two URL families and public CloudKit record/rendition shapes.

The Java protocol code in this repository is an original bounded implementation. No live/private album was queried during research or automated verification. Apple publishes no supported public Shared Album API contract; the feature is therefore best-effort interoperability with public anonymous web endpoints.
