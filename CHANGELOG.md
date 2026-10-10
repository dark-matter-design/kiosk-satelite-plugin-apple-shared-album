# Changelog

## Unreleased

## 0.1.4 — 2026-10-10

- Add optional current-time, current-date, photo-capture-date, and photo-location overlays in all four corners; every overlay remains off by default and existing settings are preserved.
- Mirror the stock small Clock overlay's top-right, 12-hour, no-seconds, no-date, near-white defaults where SDK 1 can express them, while adding explicit 24-hour, seconds, generic font, weight, color, and size controls.
- Keep current date and photo capture date unambiguous and independently togglable.
- Read legacy `dateCreated` and CloudKit `assetDate`, with a bounded local JPEG EXIF fallback for capture date and GPS. Location is shown only as sanitized coordinates; no geocoding or third-party request occurs.
- Add synthetic tests for overlay options, four positions, formatting hooks, escaping, missing metadata, privacy defaults, EXIF bounds, metadata payload shapes, manifest discovery, package inspection, and deterministic output.
- Retain the v0.1.3 synchronous renderer-discovery proof, exact SDK interface check, and documented Remote Admin refresh workaround.

No GitHub release is created by this source change.

## 0.1.3 — 2026-10-10

- Fix current public CloudKit albums whose signed image URLs use Apple's `${f}` filename template.
- Pair `CPLAsset` and `CPLMaster` records so edited-photo renditions stored on the asset are displayed, while retaining the older master-only response fallback.
- Include the current `sharing_url_key` request parameter and keep pagination, response, item, host, redirect, cancellation, and stale-session bounds intact.
- Make the per-photo duration configurable from 5 seconds through 1 hour in 5-second steps, preserving the 30-second default and all previously saved values.
- Add synthetic regressions for current CloudKit paired/template payloads and legacy SharedStreams partition handoff; no live album data is included.

No GitHub release is created by this source change.
