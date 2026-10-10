"""Regress the Plugin Manager package-entry allowlist and uniqueness contract."""
import io
import unittest
import warnings
import zipfile

from plugin_package import validate_package_entries
from plugin_dex import _modified_utf8


class PackageEntryTest(unittest.TestCase):
    required = [
        ('kiosk-satellite-plugin.json', False),
        ('plugin.jar', False),
        ('LICENSE', False),
    ]

    def test_current_plugin_manager_allowlist(self):
        entries = self.required + [
            ('assets/slides/image-1.jpg', False),
            ('native/arm64-v8a/libexample_1.so', False),
        ]
        self.assertEqual(
            validate_package_entries(entries),
            {name for name, _ in entries},
        )
        for name in ('NOTICE', 'README.md', 'manifest.json', 'nested/plugin.jar'):
            with self.subTest(name=name), self.assertRaisesRegex(
                ValueError, f'Unexpected or duplicate ZIP entry: {name}'
            ):
                validate_package_entries(self.required + [(name, False)])

    def test_duplicate_archive_entry_is_rejected(self):
        data = io.BytesIO()
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(data, 'w') as archive:
                for name, _ in self.required:
                    archive.writestr(name, b'x')
                archive.writestr('LICENSE', b'duplicate')
        with zipfile.ZipFile(data) as archive, self.assertRaisesRegex(
            ValueError, 'Unexpected or duplicate ZIP entry: LICENSE'
        ):
            validate_package_entries(
                (entry.filename, entry.is_dir()) for entry in archive.infolist()
            )

    def test_dex_modified_utf8_accepts_embedded_nul_and_unicode(self):
        self.assertEqual(_modified_utf8(b'Exif\xc0\x80\xc2\xb0'), 'Exif\0°')


if __name__ == '__main__':
    unittest.main()
