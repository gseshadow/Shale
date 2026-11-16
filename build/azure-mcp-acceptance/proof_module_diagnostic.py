"""Read-only offline audit of Stage 21 source or a retained rendered packet.

Never imports a SQL driver or runs a process. Output contains only fixed public
module names, hashes and comparison results; input SQL and paths are suppressed.
This cannot establish SQL Server's transient module metadata before a rollback.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
INSTALLER = ROOT / 'docs/sql/ai-integration/universal-pr4a-proof.sql'
VERIFIER = ROOT / 'docs/sql/ai-integration/universal-pr4a-proof-verify.sql'
REGISTRY = re.compile(r'DECLARE @C4aModules table\([^;]+; INSERT @C4aModules VALUES ([^;]+);')
ENTRY = re.compile(r"\(N'([A-Za-z0-9_]+)',0x([0-9a-f]{64}),([01])\)")
PAYLOAD = re.compile(r'DECLARE @C4aModule(\d+) nvarchar\(max\)=CONVERT\(nvarchar\(max\),0x([0-9a-f]+)\)')
XML_OPTIONS = ('SET ANSI_NULLS ON;', 'SET ANSI_PADDING ON;', 'SET ANSI_WARNINGS ON;',
               'SET ARITHABORT ON;', 'SET CONCAT_NULL_YIELDS_NULL ON;',
               'SET QUOTED_IDENTIFIER ON;', 'SET NUMERIC_ROUNDABORT OFF;')


def registry(text):
    found = REGISTRY.findall(text)
    if not found:
        raise ValueError('Expected registry missing')
    return [ENTRY.findall(part) for part in found]


def audit(path):
    canonical = INSTALLER.read_text(encoding='utf-8')
    verify = VERIFIER.read_text(encoding='utf-8')
    expected_rows = registry(canonical)[0]
    expected = {name: (digest, owner) for name, digest, owner in expected_rows}
    if len(expected) != 16 or len(expected_rows) != 16:
        raise ValueError('Unsupported canonical registry')
    raw = Path(path).read_bytes()
    text = raw.decode('utf-8-sig').replace('\r\n', '\n')
    payloads = PAYLOAD.findall(text)
    if len(payloads) != 16 or {int(i) for i, _ in payloads} != set(range(16)):
        raise ValueError('Incomplete or duplicate payloads')
    results = []
    seen = set()
    for _, encoded in payloads:
        data = bytes.fromhex(encoded)
        body = data.decode('utf-16le')
        match = re.match(r'CREATE (?:PROCEDURE|TRIGGER) dbo\.([A-Za-z0-9_]+)\b', body)
        if not match or match[1] not in expected or match[1] in seen:
            raise ValueError('Unexpected payload name')
        name = match[1]
        seen.add(name)
        digest, owner = expected[name]
        calculated = hashlib.sha256(data).hexdigest()
        results.append(dict(ModuleName=name, ExpectedDefinitionHash=digest,
                            EncodedDefinitionHash=calculated, DefinitionBytes=len(data),
                            DefinitionHashMatches=(calculated == digest),
                            ExpectedExecutionContext='OWNER' if owner == '1' else 'CALLER',
                            EncodedOwnerClauseMatches=(('WITH EXECUTE AS OWNER' in body) == (owner == '1'))))
    registries_match = all(rows == expected_rows for rows in registry(text))
    installer_at = text.find(canonical)
    verifier_at = text.find(verify)
    # This is evidence of supplied preamble bytes, not observed engine SET flags.
    preamble = '\n'.join(XML_OPTIONS) + '\nGO\n'
    packet_options = (installer_at >= 0 and verifier_at > installer_at
                      and text[:installer_at].endswith(preamble)
                      and text[:verifier_at].endswith(preamble))
    return dict(InputSHA256=hashlib.sha256(raw).hexdigest(), ModuleCount=len(results),
                ExpectedRegistriesMatch=registries_match,
                InstallerMatchesReviewedSource=(installer_at >= 0),
                VerifierMatchesReviewedSource=(verifier_at >= 0),
                PacketSessionPreamblesMatch=packet_options,
                AllEncodedDefinitionsMatch=all(r['DefinitionHashMatches'] and r['EncodedOwnerClauseMatches'] for r in results),
                EngineMetadataObserved=False, Modules=results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, required=True,
                        help='Retained rendered 21-proof.sql or public installer; read only')
    args = parser.parse_args()
    try:
        result = audit(args.input)
    except (OSError, ValueError, UnicodeError):
        print('STOP: unsupported/incomplete Stage 21 input; SQL, paths and exception text suppressed.', file=sys.stderr)
        return 2
    print(json.dumps(result, indent=2))
    return 0 if result['AllEncodedDefinitionsMatch'] and result['ExpectedRegistriesMatch'] else 1


if __name__ == '__main__':
    sys.exit(main())
