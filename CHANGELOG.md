# Changelog

## Unreleased

## 0.1.5 — 2026-10-10

- Add an explicit, off-by-default overlay pixel-shift setting. Only the overlay container moves; the photo element and its fit/fill transform remain stationary.
- Match the relevant stock Kiosk Satellite safeguards with a one-minute interval, a deterministic per-axis maximum of 10 px, a 28 px overlay inset, all four anchors, bounded layout, and timer teardown when the inline document leaves the page.
- Add synthetic regressions that separately inspect the image CSS and shift script, proving that transform writes target only `.overlay`, along with amplitude, interval, inset, anchor, disabled-state, and lifecycle checks.
- Prefer bounded, sanitized human-readable place names from explicit IPTC City/Sub-location/Province-State/Country or XMP IPTC Location/Photoshop City/State/Country fields already present in the downloaded JPEG. Never treat title, caption, description, or other free-form text as a location.
- Keep source-labeled EXIF GPS coordinates as the fallback. No reverse-geocoding request, external host, credential, live album, or live coordinate was added or used.
- Update privacy, security, development, and research documentation for local place metadata and the external reverse-geocoding decision boundary.

No GitHub release is created by this source change.

## 0.1.4 — 2026-10-10

- Add optional current-time, current-date, photo-capture-date, and photo-location overlays in all four corners; every overlay remains off by default and existing settings are preserved.
- Mirror the stock small Clock overlay's top-right, 12-hour, no-seconds, no-date, near-white defaults where SDK 1 can express them, while adding explicit 24-hour, seconds, generic font, weight, color, and size controls.
- Keep current date and photo capture date unambiguous and independently togglable.
- Read legacy `dateCreated` and CloudKit `assetDate`, with a bounded local JPEG EXIF fallback for capture date and GPS. Location is shown only as sanitized coordinates; no geocoding or third-party request occurs.
- Add synthetic tests for overlay options, four positions, formatting hooks, escaping, missing metadata, privacy defaults, EXIF bounds, metadata payload shapes, manifest discovery, package inspection, and deterministic output.
- Pin packaging to Android build-tools 35.0.0 instead of whichever D8 is newest on the host, so the documented local and CI toolchains produce identical output.
- Retain the v0.1.3 synchronous renderer-discovery proof, exact SDK interface check, and documented Remote Admin refresh workaround.

No GitHub release is created by this source change.

## 0.1.3 — 2026-10-10

- Fix current public CloudKit albums whose signed image URLs use Apple's `${f}` filename template.
- Pair `CPLAsset` and `CPLMaster` records so edited-photo renditions stored on the asset are displayed, while retaining the older master-only response fallback.
- Include the current `sharing_url_key` request parameter and keep pagination, response, item, host, redirect, cancellation, and stale-session bounds intact.
- Make the per-photo duration configurable from 5 seconds through 1 hour in 5-second steps, preserving the 30-second default and all previously saved values.
- Add synthetic regressions for current CloudKit paired/template payloads and legacy SharedStreams partition handoff; no live album data is included.

No GitHub release is created by this source change.
