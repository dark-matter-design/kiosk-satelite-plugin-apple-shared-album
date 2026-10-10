# Privacy

The plugin sends the public album token or opaque ID only to Apple's public iCloud API and downloads selected image bytes only from validated Apple iCloud media hosts. It sends nothing to this project, its maintainers, analytics providers, or Home Assistant.

The album URL is an ordinary Kiosk Satellite SDK 1 string setting, not a masked secret field. Anyone who can view plugin settings or an included backup may be able to read it. Revoke the album's public website in Apple Photos when access is no longer required.

Album lists, short-lived anonymous CloudKit grants, signed image URLs, compressed display images, and rendered HTML stay in process memory. The plugin writes no cache or log containing them. Disabling, reconfiguring, replacing, or uninstalling the plugin cancels work and drops its in-memory references; Kiosk Satellite controls deletion of stored plugin settings on uninstall.
