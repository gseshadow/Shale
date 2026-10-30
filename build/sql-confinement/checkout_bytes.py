"""Offline exact-byte checkout/receipt preflight. No SQL execution or text normalization."""
from pathlib import Path
import argparse
import hashlib
import os
import re
import stat
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
PATTERNS = ('docs/sql/ai-integration/*.sql', 'shale-data/src/main/resources/ai/*.sql',
            'shale-data/src/main/resources/ai/confinement*-modules.properties',
            'build/sql-confinement/*.sql', 'build/sql-confinement/*.sha256',
            'build/sql-confinement/reviewed-case-module-variants-v2.json')
RECEIPTS = {
    'V1': ('phase3b7', 'b949079a9d1e639db206f535502441b59aada5cc6af74da5fda1549f566db7a3'),
    'V2': ('phase3b8', '60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891'),
}


def git(root, *args):
    result = subprocess.run(['git', *args], cwd=root, capture_output=True)
    if result.returncode:
        raise ValueError('Git preflight failed; preserve local changes and inspect the repository')
    return result.stdout


def inventory(root):
    root = Path(root).resolve()
    if Path(git(root, 'rev-parse', '--show-toplevel').decode().strip()).resolve() != root:
        raise ValueError('Run against the repository root containing this checkout tool')
    paths = [name.decode('utf-8') for name in git(root, 'ls-files', '-z', '--', *PATTERNS).split(b'\0') if name]
    if not paths:
        raise ValueError('No tracked pinned inputs found')
    entries = git(root, 'ls-tree', '-r', '-z', 'HEAD', '--', *paths).split(b'\0')
    expected = {}
    for entry in filter(None, entries):
        metadata, name = entry.split(b'\t', 1)
        mode, kind, oid = metadata.split()
        if mode != b'100644' or kind != b'blob':
            raise ValueError('Pinned inputs must be regular tracked files')
        path = name.decode('utf-8')
        expected[path] = git(root, 'cat-file', 'blob', oid.decode('ascii'))
    if set(expected) != set(paths):
        raise ValueError('Pinned index and committed path sets differ; preserve staged changes')
    git(root, 'diff', '--cached', '--quiet', 'HEAD', '--', *PATTERNS)
    attributes = git(root, 'check-attr', '-z', 'eol', '--', *expected).split(b'\0')
    if any(attributes[n + 2] != b'lf' for n in range(0, len(attributes) - 1, 3)):
        raise ValueError('Current PR LF attributes required')
    for raw in expected.values():
        if b'\r' in raw:
            raise ValueError('Committed pinned bytes changed; review required, do not normalize')
    return expected


def source_receipt(raw, expected):
    """Same raw-byte, zeroed-source-field calculation formerly in test_checkout.py."""
    matches = list(re.finditer(rb'@sourceHash varbinary\(32\)=0x([0-9a-f]{64})', raw))
    if len(matches) != 1 or matches[0][1].decode('ascii') != expected:
        raise ValueError('Reviewed source receipt field mismatch')
    match = matches[0]
    actual = hashlib.sha256(raw[:match.start(1)] + b'0' * 64 + raw[match.end(1):]).hexdigest()
    if actual != expected:
        raise ValueError('Raw source receipt mismatch; stop before Maven/Azure setup')
    return actual


def verify_receipts(files):
    result = {}
    for version, (name, receipt) in RECEIPTS.items():
        result[version] = source_receipt(files[f'docs/sql/ai-integration/{name}-confinement.sql'], receipt)
    return result


def verify(root=ROOT):
    expected = inventory(root)
    result = verify_bytes(root, expected)
    verify_git_clean(root, expected)
    return result


def verify_bytes(root, expected):
    actual = {}
    for path, raw in expected.items():
        target = root / path
        if target.is_symlink() or not target.resolve().is_relative_to(root.resolve()) or not target.is_file():
            raise ValueError('Pinned input missing or unsafe: ' + path)
        actual[path] = target.read_bytes()
        if actual[path] != raw:
            raise ValueError('Pinned working-tree bytes differ: ' + path + '; use --refresh for clean conversion-only files')
    return len(actual), verify_receipts(actual)


def verify_git_clean(root, expected):
    git(root, 'diff', '--quiet', '--', *expected)
    if git(root, 'diff-files', '--raw', '--', *expected):
        raise ValueError('Pinned worktree/index stat mismatch; run --refresh before Maven or Azure')
    if git(root, 'status', '--porcelain', '--', *expected):
        raise ValueError('Pinned inputs are not clean in Git; preserve edits and inspect before continuing')


def refresh(root=ROOT):
    root = Path(root).resolve()
    expected = inventory(root)
    index_before = git(root, 'ls-files', '--stage', '-z')
    flags_before = git(root, 'ls-files', '-v', '-z')
    originals = {}
    # Git status can be clean for CRLF bytes. Accept only the two exact, known byte forms;
    # never overwrite a genuine edit, deletion, symlink or a staged change.
    for path, raw in expected.items():
        target = root / path
        if target.is_symlink() or not target.resolve().is_relative_to(root) or not target.is_file():
            raise ValueError('Pinned input missing or unsafe: ' + path)
        originals[path] = target.read_bytes()
        if originals[path] not in (raw, raw.replace(b'\n', b'\r\n')):
            raise ValueError('Local edit in pinned input; preserve it before refresh: ' + path)
    # A new prefix has no cached worktree stat entry. Materialize with Git's attributes,
    # verify ALL staged bytes/receipts, then replace conversion-only worktree copies.
    with tempfile.TemporaryDirectory(prefix='shale-pinned-checkout-') as directory:
        staging = Path(directory)
        git(root, 'checkout-index', '--force', '--prefix=' + staging.as_posix() + '/', '--', *expected)
        staged = {path: (staging / path).read_bytes() for path in expected}
        if any(staged[path] != raw for path, raw in expected.items()):
            raise ValueError('Git materialized unexpected bytes; inspect attributes/filters, do not normalize')
        verify_receipts(staged)
        git(root, 'diff', '--cached', '--quiet', 'HEAD', '--', *PATTERNS)
        for path, raw in staged.items():
            if originals[path] == raw:
                continue
            target = root / path
            # Adjacent temporary files allow atomic replacement even when TEMP is on another drive.
            descriptor, temporary = tempfile.mkstemp(prefix='.shale-pinned-', dir=target.parent)
            temporary = Path(temporary)
            try:
                with os.fdopen(descriptor, 'wb') as output:
                    output.write(raw)
                os.chmod(temporary, stat.S_IMODE(target.stat().st_mode))
                if target.is_symlink() or target.read_bytes() != originals[path]:
                    raise ValueError('Pinned input changed during refresh; preserve local edits: ' + path)
                os.replace(temporary, target)
            finally:
                temporary.unlink(missing_ok=True)
    # Atomic replacement changes inode/size/mtime. A plain update-index --refresh can
    # retain "needs update" against cached CRLF sizes even when the LF bytes match HEAD.
    # First prove actual destination bytes/receipts, then let Git check out its EXISTING
    # entries and update their stat metadata (-u). No worktree content is staged.
    verify_bytes(root, expected)
    git(root, 'diff', '--cached', '--quiet', 'HEAD', '--', *PATTERNS)
    git(root, 'checkout-index', '--force', '--index', '--', *expected)
    result = verify(root)
    if index_before != git(root, 'ls-files', '--stage', '-z'):
        raise ValueError('Index blob/mode/stage records changed during stat refresh; stop and inspect')
    if flags_before != git(root, 'ls-files', '-v', '-z'):
        raise ValueError('Index tracking flags changed during stat refresh; stop and inspect')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--refresh', action='store_true', help='Refresh clean pinned files, then verify actual bytes')
    args = parser.parse_args()
    try:
        count, receipts = refresh() if args.refresh else verify()
        print(f'PINNED_CHECKOUT PASS files={count}')
        print('PINNED_INDEX PASS clean=true')
        for version, receipt in receipts.items():
            print(f'PINNED_RECEIPT {version} PASS {receipt}')
        return 0
    except ValueError as error:
        print('PINNED_CHECKOUT FAIL: ' + str(error))
        return 1
    except (OSError, KeyError):
        print('PINNED_CHECKOUT FAIL: preserve local edits; inspect checkout/attributes/receipts before Maven or Azure setup')
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
