"""Offline Stage 09 review. Only expected() launches processes: existing javac/exporter, never SQL."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = '4B64B50DA531EBC6A2EB9DC26C6555EF555621DF371FE9D8A5CF994FB6E8417F'
FUNCTIONS = ('fn_FilterByTenant', 'fn_FilterByTenantOrGlobal')
TARGETS = ('Users', 'auditLog', 'Statuses')
# Reviewed source bytes, NOT a predicate allowlist. All bindings are parsed below.
# .gitattributes pins these SQL files to LF on Windows too.
STAGE_SOURCES = (
    ('02-phase3b1', 'phase3b1.sql', 'A25CF6FEAB9C399BE743827A783B2B75C6EFB1C26BAC917D787DC936650F84EF'),
    ('03-phase3b2b', 'phase3b2b.sql', '47ADADB3EAD42EE9ED693AEA438ECB906AAB0981B05CF592C6D9EA970179692F'),
    ('04-phase3b2c1', 'phase3b2c1.sql', '3768750D096B5B7B320E47ED197D121D98250BB42145CD7B5BCBB6A4F4124E2C'),
    ('05-phase3b2c2', 'phase3b2c2.sql', '60546C5BD11B91A02C0061FBF0A685EAEBBE491ED12B9711CEF4C7F251BE0668'),
    ('06-revocation', 'revocation.sql', '4C3A0E3987E4E19A65D0A70511A901DBBFB532459B2D817C907D5A14D9C8E08D'),
    ('07-phase3b2d', 'phase3b2d.sql', '9EB9C2A16688A3C5627960B58F226C387CFA97137776082BE874AE3468F81A07'),
    ('08-phase3b3', 'phase3b3.sql', '77AA82D6BB2944A8125E4E92EE55CC17FD6E010F7139915405B63E791E9A5021'),
)


def require(ok, message):
    if not ok:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest().upper()


def tokens(sql):
    """Bounded lexer: comments ignored, identifiers unquoted, literal bytes retained."""
    result, i = [], 0
    pattern = re.compile(r"\[(?:[^\]]|\]\])*\]|\"(?:[^\"]|\"\")*\"|N?'(?:[^']|'')*'|@[A-Za-z_][\w]*|[A-Za-z_][\w]*|\d+|[^\s]", re.I)
    while i < len(sql):
        if sql[i].isspace():
            i += 1
        elif sql.startswith('--', i):
            end = sql.find('\n', i)
            i = len(sql) if end < 0 else end
        elif sql.startswith('/*', i):
            depth, i = 1, i + 2
            while depth and i < len(sql):
                if sql.startswith('/*', i):
                    depth, i = depth + 1, i + 2
                elif sql.startswith('*/', i):
                    depth, i = depth - 1, i + 2
                else:
                    i += 1
            require(depth == 0, 'unterminated comment')
        else:
            m = pattern.match(sql, i)
            require(m is not None, 'unsupported token')
            value = m.group()
            if value.startswith('['):
                value = value[1:-1].replace(']]', ']')
            elif value.startswith('"'):
                value = value[1:-1].replace('""', '"')
            result.append(value)
            i = m.end()
    return result


class Cursor:
    def __init__(self, values):
        self.values, self.i = values, 0

    def take(self):
        require(self.i < len(self.values), 'unexpected end of RLS definition')
        value = self.values[self.i]
        self.i += 1
        return value

    def peek(self):
        return self.values[self.i].upper() if self.i < len(self.values) else ''

    def expect(self, value):
        require(self.take().upper() == value.upper(), f'expected {value} in RLS definition')

    def name(self):
        a = self.take()
        self.expect('.')
        return [a, self.take()]


def binding(c):
    function = c.name()
    c.expect('(')
    args, arg = [], []
    while c.peek() != ')':
        value = c.take()
        require(value != '(', 'nested predicate arguments require separate review')
        if value == ',':
            require(arg, 'empty predicate argument')
            args.append(arg)
            arg = []
        else:
            arg.append(value)
    c.expect(')')
    require(arg or not args, 'empty predicate argument')
    if arg:
        args.append(arg)
    return function, args


def additions(c, name):
    predicates = []
    while c.peek() == 'ADD':
        c.take()
        kind = c.take().upper()
        require(kind in ('FILTER', 'BLOCK'), 'unknown predicate kind')
        c.expect('PREDICATE')
        function, args = binding(c)
        c.expect('ON')
        target, operation = c.name(), None
        if kind == 'BLOCK':
            operation = c.take().upper() + ' ' + c.take().upper()
            require(operation in ('AFTER INSERT', 'AFTER UPDATE', 'BEFORE UPDATE', 'BEFORE DELETE'), 'unknown BLOCK operation')
        predicates.append(dict(policy=name, target=target, kind=kind, operation=operation,
                               function=function, arguments=args))
        if c.peek() == ',':
            c.take()
            require(c.peek() == 'ADD', 'expected ADD after predicate separator')
        else:
            break
    require(predicates, 'expected security predicate additions')
    return predicates


def policy(values):
    c = Cursor(values)
    for word in ('CREATE', 'SECURITY', 'POLICY'):
        c.expect(word)
    name = c.name()
    predicates = additions(c, name)
    c.expect('WITH')
    c.expect('(')
    options = {}
    while True:
        key = c.take().upper()
        require(key in ('STATE', 'SCHEMABINDING') and key not in options, 'unsupported/duplicate policy option')
        c.expect('=')
        value = c.take().upper()
        require(value in ('ON', 'OFF'), 'unknown policy state')
        options[key] = value == 'ON'
        if c.peek() != ',':
            break
        c.take()
    c.expect(')')
    if c.peek() == ';':
        c.take()
    require(c.peek() == '' and options.get('STATE') is True, 'unsupported policy suffix/state')
    return dict(name=name, enabled=True, bound=options.get('SCHEMABINDING', True)), predicates


def table_declarations(values):
    tables = []
    for i in range(len(values) - 2):
        if [v.upper() for v in values[i:i+2]] == ['CREATE', 'TABLE']:
            c = Cursor(values[i+2:])
            tables.append(tuple(c.name()))
            c.expect('(')
    require(len(tables) == len(set(tables)), 'duplicate table definition')
    return set(tables)


def migration_predicates(source, tenant_policy):
    """Decode only the reviewed literal EXEC and SET @sql/@policy forms; never execute."""
    values, predicates = tokens(source), []
    for i, value in enumerate(values):
        if value.startswith(("N'", "n'", "'")):
            sql = value[value.index("'")+1:-1].replace("''", "'")
            if not re.search(r'\bSECURITY\s+POLICY\b', sql, re.I):
                continue
            statement = tokens(sql)
            if statement == ['ALTER', 'SECURITY', 'POLICY']:
                # The reviewed Stage 02 resolves its sole enabled TenantFilter by
                # name. Resolve against the fixture, not an arbitrary destination.
                require(values[i-3:i] == ['SET', '@sql', '=']
                        and values[i+1:i+4] == ['+', '@policy', '+'], 'unsupported dynamic policy expression')
                tail = values[i+4]
                require(tail.startswith("N'") and values[i+5:i+12] ==
                        [';', 'EXEC', 'sys', '.', 'sp_executesql', '@sql', ';'],
                        'unsupported dynamic policy execution')
                statement += [tenant_policy[0], '.', tenant_policy[1]] + tokens(tail[2:-1].replace("''", "'"))
            else:
                require([v.upper() for v in values[max(0, i-2):i]] == ['EXEC', '(']
                        and values[i+1:i+3] == [')', ';'], 'unsupported security policy SQL wrapper')
            c = Cursor(statement)
            for word in ('ALTER', 'SECURITY', 'POLICY'):
                c.expect(word)
            name = c.name()
            require(name == tenant_policy, f'unexpected migration policy: {name}')
            predicates.extend(additions(c, name))
            c.expect(';')
            require(c.peek() == '', 'unsupported migration policy suffix')
        else:
            require([v.upper() for v in values[i:i+2]] != ['SECURITY', 'POLICY'],
                    'unsupported nonliteral security policy SQL')
    return predicates, table_declarations(values)


def migration_inventory(policies, existing_tables=(), root=ROOT):
    manifest = json.loads((root / 'build/azure-mcp-acceptance/stages.json').read_text(encoding='utf-8'))
    names = [s['name'] for s in manifest]
    require(len(names) == len(set(names)), 'duplicate stage name')
    span = manifest[names.index('01-fixture')+1:names.index('09-normalize')]
    reviewed = [dict(name=name, sources=['docs/sql/ai-integration/' + file]) for name, file, _ in STAGE_SOURCES]
    require(span == reviewed, 'Stage 02-08 source order/manifest differs from review')
    tenant = [p for p in policies if p['name'][1] == 'TenantFilter']
    require(tenant == [dict(name=['sec', 'TenantFilter'], enabled=True, bound=True)],
            'Stage 02 requires the sole enabled schema-bound sec.TenantFilter')
    predicates, provenance, tables = [], [], set(existing_tables)
    for stage, (_, _, digest) in zip(span, STAGE_SOURCES):
        path = stage['sources'][0]
        data = (root / path).read_bytes()
        require(sha(data) == digest, f'unreviewed migration source SHA256: {path}')
        rows, created = migration_predicates(data.decode('utf-8'), tenant[0]['name'])
        require(not tables.intersection(created), f'conflicting table definitions: {path}')
        require(all(tuple(p['target']) in created for p in rows), f'predicate target not created by stage: {path}')
        tables.update(created)
        predicates.extend(rows)
        provenance.append(dict(stage=stage['name'], source=path, sha256=digest, predicateCount=len(rows)))
    require(len(predicates) == 39, f'Stage 02-08 must add exactly 39 predicates; parsed {len(predicates)}')
    unique_predicates(predicates)
    return predicates, provenance


def unique_predicates(predicates):
    seen = set()
    for p in predicates:
        key = keyed([p['policy'], p['target'], p['kind'], p['operation']])
        require(key not in seen, f'duplicate/conflicting predicate slot: {key}')
        seen.add(key)


def predicate_counts(predicates):
    """Count parsed bindings without dropping duplicates or applying an approval."""
    counts = Counter()
    for p in predicates:
        scope = 'target' if p['target'] in [['dbo', t] for t in TARGETS] else 'nonTarget'
        counts[(tuple(p['policy']), p['kind'], scope)] += 1
    groups = sorted({(policy, kind) for policy, kind, _ in counts})
    rows = [dict(policy=list(policy), kind=kind,
                 target=counts[policy, kind, 'target'], nonTarget=counts[policy, kind, 'nonTarget'])
            for policy, kind in groups]
    return dict(total=len(predicates), target=sum(r['target'] for r in rows),
                nonTarget=sum(r['nonTarget'] for r in rows), byPolicyAndType=rows)


def baseline(batches, diagnostics=False):
    functions, policies, predicates, schemas, tables = [], [], [], {'dbo': 'dbo'}, set()
    for batch in batches:
        t = tokens(batch)
        created = table_declarations(t)
        require(not tables.intersection(created), 'duplicate fixture table definition')
        tables.update(created)
        if [v.upper() for v in t[:2]] == ['CREATE', 'SCHEMA']:
            require(len(t) in (3, 4, 5, 6), 'unsupported schema ownership')
            rest = t[3:]
            if rest and rest[-1] == ';':
                rest = rest[:-1]
            require(not rest or len(rest) == 2 and rest[0].upper() == 'AUTHORIZATION', 'unsupported schema ownership')
            schemas[t[2]] = rest[1] if rest else 'dbo'  # fixture installed by dbo
        if [v.upper() for v in t[:3]] == ['CREATE', 'SECURITY', 'POLICY']:
            p, bindings = policy(t)
            policies.append(p)
            predicates.extend(bindings)
        if [v.upper() for v in t[:2]] == ['CREATE', 'FUNCTION']:
            c = Cursor(t[2:])
            name = c.name()
            if name not in [['sec', f] for f in FUNCTIONS]:
                continue
            c.expect('(')
            parameter = c.take()
            c.expect('int')
            c.expect(')')
            require(parameter.startswith('@'), 'unexpected function signature')
            for word in ('RETURNS', 'TABLE', 'WITH', 'SCHEMABINDING', 'AS', 'RETURN'):
                c.expect(word)
            body = c.values[c.i:]
            # These two fixture predicates are expression-only. Never guess dependency
            # graphs for a new relational body or a user-defined function/type reference.
            require(not any(v.upper() in ('FROM', 'JOIN', 'APPLY') for v in body)
                    and '.' not in body, f'unsupported dependency syntax in {name}; STOP for review')
            functions.append(dict(name=name, definitionHex=batch.encode('utf-16le').hex().upper(),
                                  parameter=parameter))
    require(sorted(f['name'][1] for f in functions) == sorted(FUNCTIONS), 'expected exactly two RLS functions')
    require(len({tuple(p['name']) for p in policies}) == len(policies), 'duplicate policy')
    require('sec' in schemas, 'missing sec schema ownership')
    fixture_counts = predicate_counts(predicates)
    if diagnostics:
        print_counts('fixture', predicates)
    require(fixture_counts['nonTarget'] == 77,
            f'fixture must contain exactly 77 original non-target predicates; parsed {fixture_counts["nonTarget"]}')
    migration_rows, provenance = migration_inventory(policies, tables)
    predicates.extend(migration_rows)
    unique_predicates(predicates)
    counts = predicate_counts(predicates)
    if diagnostics:
        for source in provenance:
            print(f'  {source["stage"]}: migration additions={source["predicateCount"]} SHA256={source["sha256"]}')
        print_counts('Stage 09', predicates)
    require(counts['nonTarget'] == 116,
            f'Stage 09 must contain exactly 116 non-target predicates; parsed {counts["nonTarget"]}')
    owners = []
    objects = {tuple(p['name']) for p in policies} | {tuple(p['target']) for p in predicates}
    objects |= {('sec', f) for f in FUNCTIONS} | {('dbo', t) for t in TARGETS}
    for name in sorted(objects):
        require(name[0] in schemas, f'unknown schema owner: {name[0]}')
        owners.append(dict(name=list(name), schemaOwner=schemas[name[0]], owner=schemas[name[0]]))
    result = dict(version=2, fixtureSha256=FIXTURE, functions=functions, fixtureCounts=fixture_counts,
                  migrationSources=provenance, policies=policies, predicates=predicates, owners=owners)
    require(schemas['dbo'] == schemas['sec'] == 'dbo', 'Phase1R requires dbo schema ownership')
    return result


def print_counts(label, predicates):
    counts = predicate_counts(predicates)
    print(f'Parsed {label} predicates: total={counts["total"]} target={counts["target"]} non-target={counts["nonTarget"]}')
    for row in counts['byPolicyAndType']:
        print(f'  {keyed(row["policy"])} {row["kind"]}: total={row["target"] + row["nonTarget"]} '
              f'target={row["target"]} non-target={row["nonTarget"]}')


def expected(fixture, output):
    require(sha(fixture.read_bytes()) == FIXTURE, 'authoritative fixture hash mismatch')
    with tempfile.TemporaryDirectory(prefix='phase1r-', dir=output.parent) as tmp:
        classes, export = Path(tmp) / 'classes', Path(tmp) / 'export.sql'
        classes.mkdir()
        subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(classes),
                        str(ROOT / 'shale-data/src/test/java/com/shale/data/verification/CaseReadVerificationSafety.java'),
                        str(ROOT / 'build/azure-mcp-acceptance/ExportFixture.java')], check=True)
        subprocess.run(['java', '-cp', str(classes), 'com.shale.data.verification.ExportFixture',
                        str(fixture), str(export)], check=True)
        raw = re.findall(r'CONVERT\(nvarchar\(max\),0x([0-9a-f]+)\); EXEC sys.sp_executesql @FixtureBatch;',
                         export.read_text(encoding='utf-8'))
        require(raw, 'no parser-exported batches')
        result = baseline([bytes.fromhex(h).decode('utf-16le') for h in raw], diagnostics=True)
    with output.open('x', encoding='utf-8') as stream:
        json.dump(result, stream, ensure_ascii=True, indent=2)
    print(f'PASS baseline generated; SHA256={sha(output.read_bytes())}; not approval')


def keyed(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=True, separators=(',', ':'))


def compare(expected_data, actual):
    errors = []

    def check(ok, message):
        if not ok:
            errors.append(message)

    def same(label, want, got):
        check(want == got, f'{label}: expected {keyed(want)}, actual {keyed(got)}')

    def inventory(label, want, got):
        missing, extra = Counter(map(keyed, want)), Counter(map(keyed, got))
        for item, count in sorted((missing - extra).items()):
            errors.append(f'{label}: missing {count} x {item}')
        for item, count in sorted((extra - missing).items()):
            errors.append(f'{label}: unexpected {count} x {item}')

    same('baseline format', 2, expected_data['version'])
    same('fixture hash', FIXTURE, expected_data['fixtureSha256'])
    migration_rows, provenance = migration_inventory(expected_data['policies'])
    same('reviewed migration sources', provenance, expected_data['migrationSources'])
    migration_targets = {tuple(p['target']) for p in migration_rows}
    inventory('expected migration binding', migration_rows,
              [p for p in expected_data['predicates'] if tuple(p['target']) in migration_targets])
    fixture_rows = [p for p in expected_data['predicates'] if tuple(p['target']) not in migration_targets]
    same('original fixture count', 77, predicate_counts(fixture_rows)['nonTarget'])
    same('fixture count provenance', predicate_counts(fixture_rows), expected_data['fixtureCounts'])
    same('expected Stage 09 count', 116, predicate_counts(expected_data['predicates'])['nonTarget'])
    unique_predicates(expected_data['predicates'])
    identity = actual['identity']
    same('database', 'ShaleMcpAcceptance', identity['database'])
    check(identity['server'] in ('shale', 'shale.database.windows.net'), 'unexpected logical server')
    same('engine', 5, identity['engine'])
    same('collation', 'SQL_Latin1_General_CP1_CI_AS', identity['collation'])
    check(bool(identity['physical']), 'missing Azure physical incarnation')
    same('Phase1R receipt count', 0, identity['receipts'])
    same('invocation object', None, identity['invocation'])
    same('function count', 2, len(actual['functions']))
    hashes = {}
    for f in expected_data['functions']:
        name = f['name']
        found = [a for a in actual['functions'] if a['name'] == name]
        if len(found) != 1:
            errors.append(f'function {name}: expected exactly one, found {len(found)}')
            continue
        a = found[0]
        want, got = bytes.fromhex(f['definitionHex']), bytes.fromhex(a['definitionHex'])
        if want != got:
            offset = next((i for i, (x, y) in enumerate(zip(want, got)) if x != y), min(len(want), len(got)))
            errors.append(f'function {name}: byte mismatch at offset {offset}; expected length={len(want)} SHA256={sha(want)}, actual length={len(got)} SHA256={sha(got)}; next 32 bytes expected={want[offset:offset+32].hex()} actual={got[offset:offset+32].hex()}')
        same(f'function {name} byte length', len(got), a['byteLength'])
        same(f'function {name} hash integrity', sha(got), a['hash'])
        hashes[name[1]] = a['hash']
        same(f'function {name} flags', ['IF', True, True, True, None],
             [a['type'], a['bound'], a['ansi'], a['quoted'], a['executeAs']])
        same(f'function {name} signature',
             [dict(id=1, name=f['parameter'], systemType=56, userType=56, output=False)], a['parameters'])
        dependencies, dependency_count = a.get('dependencies'), a.get('dependencyCount')
        array_evidence = type(dependencies) is list
        count_evidence = type(dependency_count) is int and dependency_count >= 0
        check(array_evidence, f'function {name} dependencies: missing or non-array evidence')
        check(count_evidence, f'function {name} dependencyCount: missing or invalid catalog count')
        if array_evidence and count_evidence:
            same(f'function {name} dependency count/array length', dependency_count, len(dependencies))
        same(f'function {name} dependencies', [], dependencies)
    inventory('policies', expected_data['policies'], actual['policies'])
    inventory('ownership', expected_data['owners'], actual['owners'])
    bindings = []
    for p in actual['predicates']:
        c = Cursor(tokens(p['definition']))
        # Catalog predicates have one optional enclosing pair of parentheses.
        wrapped = c.peek() == '('
        if wrapped:
            c.take()
        function, args = binding(c)
        if wrapped:
            c.expect(')')
        require(c.peek() == '', f'unsupported catalog predicate: {p["definition"]}')
        bindings.append(dict(policy=p['policy'], target=p['target'], kind=p['kind'],
                             operation=p['operation'], function=function, arguments=args))
    inventory('predicate', expected_data['predicates'], bindings)
    targets = [p for p in bindings if p['target'] in [['dbo', t] for t in TARGETS]]
    required = [dict(policy=['sec', 'TenantFilter'], target=['dbo', t], kind='FILTER', operation=None,
                     function=['sec', FUNCTIONS[t == 'Statuses']], arguments=[['ShaleClientId']]) for t in TARGETS]
    inventory('target FILTER', required, targets)
    other = [p for p in bindings if p not in targets]
    same('non-target count', 116, len(other))
    inventory('target columns', [dict(target=t, systemType=56, userType=56, computed=False) for t in TARGETS], actual['columns'])
    local = json.loads(actual['otherJson'])
    same('raw other inventory count', 116, len(local))
    # Reconcile ID-bearing raw rows to every resolved OTHER binding; IDs never
    # participate in cross-database comparison, but are not silently discarded.
    resolved = [p for p in actual['predicates'] if p['target'] not in [['dbo', t] for t in TARGETS]]
    inventory('raw/resolved other bindings',
              [dict(policyId=p['policyId'], targetId=p['targetId'], predicateId=p['predicateId'],
                    definition=p['definition'], kind=p['kind'], operation=p['operation']) for p in resolved],
              [dict(policyId=p['object_id'], targetId=p['target_object_id'], predicateId=p['security_predicate_id'],
                    definition=p['predicate_definition'], kind=p['predicate_type_desc'], operation=p.get('operation_desc')) for p in local])
    same('other hash integrity', sha(actual['otherJson'].encode('utf-16le')), actual['otherHash'])
    return dict(outcome='FAIL' if errors else 'PASS', discrepancies=errors,
                fixtureSha256=FIXTURE, collectedUtc=identity.get('collectedUtc'),
                otherPredicateCount=len(other), observedFunctionHashes=hashes,
                observedOtherPredicatesHash=actual['otherHash'], physicalDatabase=identity['physical'],
                normalizationApproved=False)


def capture(path):
    # SQLCMD output consists solely of bounded hex lines. Reject banners/errors,
    # empty/truncated JSON, duplicate keys and concatenated/partial exports.
    lines = path.read_text(encoding='utf-8-sig').splitlines()
    parts = [line.strip() for line in lines if line.strip()]
    require(parts and all(re.fullmatch('[0-9A-Fa-f]+', p) and len(p) % 2 == 0 for p in parts),
            'capture must contain only complete hex lines')

    def unique(pairs):
        result = {}
        for k, v in pairs:
            require(k not in result, f'duplicate JSON key: {k}')
            result[k] = v
        return result
    return json.loads(bytes.fromhex(''.join(parts)).decode('utf-16le'), object_pairs_hook=unique)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    e = commands.add_parser('expected')
    e.add_argument('--fixture', type=Path, required=True)
    e.add_argument('--out', type=Path, required=True)
    c = commands.add_parser('compare')
    c.add_argument('--expected', type=Path, required=True)
    c.add_argument('--capture', type=Path, required=True)
    c.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.command == 'expected':
            expected(args.fixture, args.out)
            return 0
        report = compare(json.loads(args.expected.read_text(encoding='utf-8')), capture(args.capture))
        report.update(expectedFileSha256=sha(args.expected.read_bytes()), captureSha256=sha(args.capture.read_bytes()))
    except (ValueError, KeyError, TypeError, IndexError, OSError, subprocess.CalledProcessError) as error:
        report = dict(outcome='FAIL', discrepancies=[str(error)], normalizationApproved=False)
    if args.command == 'compare':
        with args.report.open('x', encoding='utf-8') as stream:
            json.dump(report, stream, indent=2)
    print(report['outcome'])
    for error in report['discrepancies']:
        print(error)
    return 0 if report['outcome'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
