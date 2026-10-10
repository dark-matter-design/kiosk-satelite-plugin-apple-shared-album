#!/usr/bin/env python3
"""Check this plugin against the pinned Kiosk Satellite screensaver discovery source."""
import argparse
import json
from pathlib import Path
import subprocess


PINNED_COMMIT = 'abc70b0839842e251b8d3917bbbb44569036556c'
ROOT = Path(__file__).resolve().parents[1]


def require(text, needle, label):
    if needle not in text:
        raise SystemExit(f'Upstream screensaver contract changed: {label}')


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('upstream', help='Checkout of github.com/jxlarrea/kiosk-satellite')
args = parser.parse_args()
upstream = Path(args.upstream).resolve()
head = subprocess.run(
    ['git', '-C', str(upstream), 'rev-parse', 'HEAD'],
    check=True, capture_output=True, text=True,
).stdout.strip()
if head != PINNED_COMMIT:
    raise SystemExit(f'Expected pinned upstream {PINNED_COMMIT}, got {head}')

manifest = json.loads((ROOT / 'kiosk-satellite-plugin.json').read_text(encoding='utf-8'))
if manifest.get('schemaVersion') != 1 or manifest.get('apiVersion') != 1:
    raise SystemExit('Plugin must use upstream schema 1 / API 1')
if 'screensaver' not in manifest.get('capabilities', []):
    raise SystemExit('Plugin does not declare the screensaver capability')

sdk = upstream / 'app/android/app/src/main/java/me/jxl/kiosk/plugins'
for source in sorted((ROOT / 'sdk/src/me/jxl/kiosk/plugins').glob('*.java')):
    target = sdk / source.name
    if not target.exists() or source.read_bytes() != target.read_bytes():
        raise SystemExit(f'Published SDK mismatch: {source.name}')

base = upstream / 'app'
manifest_source = (base / 'android/app/src/main/kotlin/me/jxl/kiosk_satellite/plugins/PluginManifest.kt').read_text()
bridge_source = (base / 'android/app/src/main/kotlin/me/jxl/kiosk_satellite/plugins/PluginBridge.kt').read_text()
manager_source = (base / 'lib/managers/plugins/plugin_manager.dart').read_text()
settings_source = (base / 'lib/managers/settings/settings_manager.dart').read_text()
device_ui_source = (base / 'lib/ui/settings_screen.dart').read_text()
remote_source = (base / 'lib/managers/remote/remote_manager.dart').read_text()

require(manifest_source, '"screensaver"', 'manifest no longer accepts screensaver capability')
require(bridge_source, 'screensavers.publish(key, title, html)', 'host no longer registers inline renderers')
require(bridge_source, 'notifyScreensavers()', 'host no longer notifies Flutter after publication')
require(manager_source, "next['plugin:$id:${raw['key']}']", 'renderer mode identity changed')
require(manager_source, "'${entry.value['title']} (${installed.value", 'renderer option label changed')
require(settings_source, '...pluginScreensavers().keys', 'settings no longer include live plugin modes')
require(device_ui_source, 'plugins.screensavers.addListener', 'device picker no longer refreshes on publication')
require(remote_source, "SettingOptionsChanged() => {'settings'}", 'remote settings refresh event changed')

# This exact host version updates the on-device picker, but publication does not
# emit SettingOptionsChanged. A Remote Admin page opened too early therefore
# needs a settings refetch/reload even though the native renderer is registered.
if 'SettingOptionsChanged' in manager_source:
    raise SystemExit('Upstream now invalidates remote settings for plugin catalog changes; review the documented workaround')

print('Upstream screensaver contract passed:')
print(f'- source: jxlarrea/kiosk-satellite@{head}')
print(f'- manifest capability: screensaver (schema {manifest["schemaVersion"]}, API {manifest["apiVersion"]})')
print('- mode after publication: plugin:apple-shared-album:apple_album')
print('- label after publication: Apple Shared Album (Apple Shared Album)')
print('- host behavior: device picker refreshes live; Remote Admin requires a settings refetch/reload')
