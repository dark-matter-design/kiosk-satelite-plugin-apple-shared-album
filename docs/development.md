# Development and verification

## Toolchain

The current upstream template workflow uses Ubuntu 24.04, `actions/checkout@v4`, `actions/setup-java@v4` with Temurin 21, `android-actions/setup-android@v4`, Android platform 35, and build-tools 35.0.0. The builder compiles Java with `javac --release 8`, targets manifest `minAndroidSdk` 24, and runs D8 with `--min-api 24`. Python 3 is required; upstream does not pin its patch version.

## Local verification

```sh
python3 tools/test.py
python3 tools/build.py --android-platform 35
python3 tools/inspect_package.py
python3 tools/audit_repository.py
```

`tools/test.py` compiles the SDK, plugin, and tests and runs synthetic protocol tests plus the upstream Python tooling tests. It does not make network requests. The protocol fixtures mirror the documented reference shapes: legacy numeric derivatives and string dimensions, CloudKit zone metadata, explicit `:443` partition bases, concrete image/video UTIs, paired asset/master records, and multiple JPEG/original rendition names. Duration tests cover SDK-style `Double` values plus malformed, non-finite, negative, fractional, and 64-bit-extreme inputs. `inspect_package.py` verifies the Plugin Manager entry allowlist and uniqueness rule, package names, fixed timestamps, the matching embedded/release manifests, checksum, DEX presence, and size limits. The installable ZIP includes `LICENSE`; `NOTICE` remains in the repository and release-tag README because Plugin Manager rejects it as a package entry. `audit_repository.py` rejects credential-like files and scans tracked text plus Git history for common secret markers and live Apple album URLs.

SDK 1 number settings are finite JSON numbers constrained to the manifest's inclusive `min`/`max` and exact `step`. Kiosk Satellite renders them as a stepped slider and sends a `Number` (normally a `Double`) to the plugin. The display-duration manifest exposes 5–300 seconds in 5-second steps with a 30-second default. Runtime code independently rejects non-finite/non-numeric input, rounds finite values to the nearest 5-second step, clamps to the same bounds before scheduling, and stores the delay as `long`.

## Manual device gate

Before promoting this implementation as a verified stable release:

1. Install the local ZIP on Kiosk Satellite 2026.10.18 or newer.
2. Verify start, rotation, fit/fill, None/Fade/Slide, transition duration, Ken Burns and reduced-motion behavior, refresh additions/removals, screen-off/inactive pause and resume, disable, re-enable, URL replacement, and uninstall.
3. With owner permission, test one public album of each supported URL format without recording its link, traffic, metadata, or images.
4. Verify private, invite-only, approval-required, revoked, empty, oversized, slow, and offline states show only sanitized status text.
5. Confirm disabling or replacing the plugin stops network traffic and a stale image never reappears.

The repository and automated suite must continue to contain synthetic fixtures only.
