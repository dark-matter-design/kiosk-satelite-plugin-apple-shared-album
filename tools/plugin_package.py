"""Mirror Kiosk Satellite's SDK 1 package-entry allowlist and uniqueness rule."""
import re


REQUIRED_ENTRIES = frozenset({
    'kiosk-satellite-plugin.json',
    'plugin.jar',
    'LICENSE',
})
NATIVE_PATH = re.compile(r'native/(arm64-v8a|armeabi-v7a|x86_64)/lib[a-zA-Z0-9_]+\.so')
ASSET_PART = re.compile(r'[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}')


def valid_asset_path(path):
    return 1 <= len(path) <= 240 and all(
        part not in ('.', '..') and ASSET_PART.fullmatch(part)
        for part in path.split('/')
    )


def validate_package_entries(entries):
    """Validate ``(name, is_directory)`` entries like PluginPackage.extract."""
    seen = set()
    for name, is_directory in entries:
        allowed = (
            name in REQUIRED_ENTRIES
            or NATIVE_PATH.fullmatch(name)
            or (name.startswith('assets/') and valid_asset_path(name.removeprefix('assets/')))
        )
        if is_directory or not allowed or name in seen:
            raise ValueError(f'Unexpected or duplicate ZIP entry: {name}')
        seen.add(name)
        if len(seen) > 512:
            raise ValueError('At most 512 package files are supported')
    if not REQUIRED_ENTRIES.issubset(seen):
        raise ValueError('Package needs kiosk-satellite-plugin.json, plugin.jar and LICENSE')
    return seen
