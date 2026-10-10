#!/usr/bin/env python3
"""Reject common credential material and accidental live album links."""
import re
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
tracked = subprocess.run(
    ['git', '-C', str(ROOT), 'ls-files', '-co', '--exclude-standard'],
    check=True, capture_output=True, text=True,
).stdout.splitlines()

forbidden_names = re.compile(r'(^|/)(\.env($|\.)|id_rsa|id_ed25519|.*\.(p12|pfx|jks|keystore))$', re.I)
secret_patterns = [
    re.compile('-----BEGIN ' + 'PRIVATE KEY-----'),
    re.compile('AKIA' + r'[A-Z0-9]{16}'),
    re.compile('gh' + r'[pousr]_[A-Za-z0-9]{30,}'),
    re.compile('xox' + r'[abprs]-[A-Za-z0-9-]{20,}'),
]
legacy_link = re.compile(r'https://(?:www\.)?icloud\.com/sharedalbum/#([A-Za-z0-9_-]{6,128})')
current_link = re.compile(r'https://photos\.icloud\.com/shared/album/([A-Za-z0-9_-]{6,128})')
allowed_examples = {'TOKEN', 'OPAQUE_ID', 'B12abcDEF', 'OPAQUE_123'}

problems = []
for relative in tracked:
    if forbidden_names.search(relative):
        problems.append(f'credential-like filename: {relative}')
        continue
    path = ROOT / relative
    if not path.is_file() or relative == 'tools/audit_repository.py' or path.stat().st_size > 2 * 1024 * 1024:
        continue
    try:
        text = path.read_text(encoding='utf-8')
    except UnicodeDecodeError:
        continue
    for pattern in secret_patterns:
        if pattern.search(text):
            problems.append(f'credential-like content: {relative}')
    if not relative.startswith('tests/'):
        for pattern in (legacy_link, current_link):
            for match in pattern.finditer(text):
                if match.group(1) not in allowed_examples:
                    problems.append(f'non-fixture Apple album URL: {relative}')

if problems:
    raise SystemExit('Repository audit failed:\n- ' + '\n- '.join(sorted(set(problems))))

history = subprocess.run(
    ['git', '-C', str(ROOT), 'log', '--all', '--format=fuller', '--patch', '--no-ext-diff'],
    check=True, capture_output=True, text=True,
).stdout
for pattern in secret_patterns:
    if pattern.search(history):
        problems.append('credential-like content in Git history')
for pattern in (legacy_link, current_link):
    for match in pattern.finditer(history):
        if match.group(1) not in allowed_examples:
            problems.append('non-fixture Apple album URL in Git history')

if problems:
    raise SystemExit('Repository audit failed:\n- ' + '\n- '.join(sorted(set(problems))))
print(f'Repository audit passed: {len(tracked)} tracked/unignored paths plus Git history scanned; '
      'no credential material or live album URLs found')
