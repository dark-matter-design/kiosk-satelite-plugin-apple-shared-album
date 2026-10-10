# Security policy

## Supported versions

Until the first release, security fixes are made on `main`. After release, only the newest stable version is supported.

## Report privately

Use GitHub's private security-advisory form for this repository. Do not open a public issue containing an album URL, token, opaque ID, signed media URL, photo, response body, hostname, or device log with personal data.

Include the plugin version, Kiosk Satellite version, Android version, URL **format** (legacy or current, not the value), and a minimal redacted reproduction. Synthetic fixtures are preferred.

## Security invariants

- Public, anonymous, read-only albums only; no Apple authentication or invite acceptance.
- HTTPS and allowlisted Apple host suffixes at input, response-derived endpoints, media references, and every redirect.
- No cookies, credentials, telemetry, disk photo cache, or exception/server-body logging.
- Fixed upper bounds on time, redirects, pages, records, items, JSON depth/nodes, metadata bytes, image bytes/dimensions/pixels, and inline output.
- EXIF/IPTC/XMP parsing is local, JPEG-only, segment-bounded to 64 KiB, entry-count/resource bounded, offset-checked, and limited to capture date, GPS, and explicit place fields. Captions/descriptions are excluded. Coordinates and rendered strings are range/length checked, control/format characters are removed, and HTML is escaped.
- No reverse geocoding or metadata network service; optional capture date, place name, and GPS stay in the in-memory renderer and default off.
- Pixel shift writes only the overlay container's CSS transform. The image element, source, dimensions, object-fit, and transform remain unchanged.
- Session generation checks and active-connection cancellation before publication.
- Fixed user-facing error messages that contain no untrusted values.
