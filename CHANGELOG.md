# Changelog

## 0.1.3 — 2026-10-10

- Fix current public CloudKit albums whose signed image URLs use Apple's `${f}` filename template.
- Pair `CPLAsset` and `CPLMaster` records so edited-photo renditions stored on the asset are displayed, while retaining the older master-only response fallback.
- Include the current `sharing_url_key` request parameter and keep pagination, response, item, host, redirect, cancellation, and stale-session bounds intact.
- Make the per-photo duration configurable from 5 seconds through 1 hour in 5-second steps, preserving the 30-second default and all previously saved values.
- Add synthetic regressions for current CloudKit paired/template payloads and legacy SharedStreams partition handoff; no live album data is included.

No GitHub release is created by this source change.
