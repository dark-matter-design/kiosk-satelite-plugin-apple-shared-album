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

`tools/test.py` compiles the SDK, plugin, and tests and runs synthetic protocol tests plus the upstream Python tooling tests. It does not make network requests. `inspect_package.py` verifies the Plugin Manager entry allowlist and uniqueness rule, package names, fixed timestamps, the matching embedded/release manifests, checksum, DEX presence, and size limits. The installable ZIP includes `LICENSE`; `NOTICE` remains in the repository and release-tag README because Plugin Manager rejects it as a package entry. `audit_repository.py` rejects credential-like files and scans tracked text plus Git history for common secret markers and live Apple album URLs.

## Manual device gate

Before publishing the first release:

1. Install the local ZIP on Kiosk Satellite 2026.10.18 or newer.
2. Verify start, rotation, fit/fill, refresh, disable, re-enable, URL replacement, and uninstall.
3. With owner permission, test one public album of each supported URL format without recording its link, traffic, metadata, or images.
4. Verify private, invite-only, approval-required, revoked, empty, oversized, slow, and offline states show only sanitized status text.
5. Confirm disabling or replacing the plugin stops network traffic and a stale image never reappears.

The repository and automated suite must continue to contain synthetic fixtures only.
