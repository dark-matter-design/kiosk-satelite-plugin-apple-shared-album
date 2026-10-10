#!/usr/bin/env python3
"""Verify the locally built plugin package and its deterministic metadata."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'dist'
packages = sorted(DIST.glob('*.zip'))
if len(packages) != 1:
    raise SystemExit(f'Expected one package ZIP, found {len(packages)}')
package = packages[0]
if package.stat().st_size > 4 * 1024 * 1024:
    raise SystemExit('Compressed package exceeds 4 MiB')

with zipfile.ZipFile(package) as archive:
    names = archive.namelist()
    required = {'kiosk-satellite-plugin.json', 'plugin.jar', 'LICENSE', 'NOTICE'}
    if set(names) != required or names != sorted(names):
        raise SystemExit(f'Unexpected or non-sorted package entries: {names}')
    if any(info.date_time != (2020, 1, 1, 0, 0, 0) for info in archive.infolist()):
        raise SystemExit('Package entry timestamp is not deterministic')
    expanded = sum(info.file_size for info in archive.infolist())
    if expanded > 4 * 1024 * 1024:
        raise SystemExit('Expanded package exceeds 4 MiB')
    embedded_manifest = archive.read('kiosk-satellite-plugin.json')
    plugin_jar = archive.read('plugin.jar')

standalone_manifest = (DIST / 'kiosk-satellite-plugin.json').read_bytes()
if embedded_manifest != standalone_manifest:
    raise SystemExit('Standalone and embedded manifests differ')
manifest = json.loads(embedded_manifest)
expected_name = f"{manifest['id']}-{manifest['version']}.zip"
if package.name != expected_name:
    raise SystemExit(f'Package name mismatch: {package.name} != {expected_name}')

digest = hashlib.sha256(package.read_bytes()).hexdigest()
checksum = package.with_suffix('.zip.sha256').read_text(encoding='utf-8')
if checksum != f'{digest}  {package.name}\n':
    raise SystemExit('Checksum file does not match package')

jar_path = DIST / '.inspect-plugin.jar'
try:
    jar_path.write_bytes(plugin_jar)
    with zipfile.ZipFile(jar_path) as jar:
        dex_names = jar.namelist()
        if not dex_names or any(not name.endswith('.dex') for name in dex_names):
            raise SystemExit(f'plugin.jar has unexpected entries: {dex_names}')
finally:
    jar_path.unlink(missing_ok=True)

print(f'Package inspection passed: {package.name}; compressed={package.stat().st_size}; '
      f'expanded={expanded}; sha256={digest}; entries={len(names)}')
