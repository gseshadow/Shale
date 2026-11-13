"""Offline tests only: synthetic catalog payloads; never invoke SQLCMD/JDBC."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import subprocess
import sys
import shutil
from contextlib import redirect_stdout
from io import StringIO
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('evidence', Path(__file__).with_name('phase1r_evidence.py'))
E = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(E)


def unit_baseline(transform=lambda batches: batches, *, diagnostics=False,
                  non_target_filters=60,
                  block_operations=('AFTER INSERT',) * 8 + ('AFTER UPDATE',) * 8 + ('BEFORE UPDATE',)):
    batches = ['CREATE SCHEMA sec AUTHORIZATION dbo;']
    for name in E.FUNCTIONS:
        batches.append(f'CREATE FUNCTION [sec].[{name}](@tenant int) RETURNS TABLE WITH SCHEMABINDING AS RETURN SELECT 1 AS Visible WHERE @tenant=CONVERT(int,SESSION_CONTEXT(N\'ShaleClientId\'));\r\n')
    clauses = [f'ADD FILTER PREDICATE sec.{E.FUNCTIONS[t == "Statuses"]}(ShaleClientId) ON dbo.{t}' for t in E.TARGETS]
    # Distinct targets plus a BLOCK exercise both operation and duplicate accounting.
    clauses += [f'ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.Unit{i}' for i in range(non_target_filters)]
    clauses += [f'ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.Unit{i} {operation}'
                for i, operation in enumerate(block_operations)]
    batches.append('CREATE SECURITY POLICY sec.TenantFilter ' + ',\n'.join(clauses) + ' WITH (STATE=ON, SCHEMABINDING=ON);')
    return E.baseline(transform(batches), diagnostics=diagnostics)


def catalog(baseline, offset=100):
    functions = []
    for f in baseline['functions']:
        raw = bytes.fromhex(f['definitionHex'])
        functions.append(dict(name=f['name'], definitionHex=f['definitionHex'], byteLength=len(raw), hash=E.sha(raw),
                              type='IF', bound=True, ansi=True, quoted=True, executeAs=None, dependencies=[], dependencyCount=0,
                              parameters=[dict(id=1, name=f['parameter'], systemType=56, userType=56, output=False)]))
    predicates, raw = [], []
    for i, p in enumerate(baseline['predicates']):
        args = ','.join(' '.join(a) for a in p['arguments'])
        definition = '([' + p['function'][0] + '].[' + p['function'][1] + '](' + args + '))'
        row = dict(policy=p['policy'], target=p['target'], kind=p['kind'], operation=p['operation'],
                   definition=definition, policyId=offset, targetId=offset + i + 1, predicateId=i + 1)
        predicates.append(row)
        if p['target'] not in [['dbo', t] for t in E.TARGETS]:
            raw.append(dict(object_id=row['policyId'], target_object_id=row['targetId'], security_predicate_id=row['predicateId'],
                            predicate_definition=definition, predicate_type_desc=p['kind'], operation_desc=p['operation']))
    other = json.dumps(raw, separators=(',', ':'))
    return dict(identity=dict(database='ShaleMcpAcceptance', server='shale', engine=5,
                             collation='SQL_Latin1_General_CP1_CI_AS', physical='unit-incarnation', receipts=0, invocation=None),
                functions=functions, policies=copy.deepcopy(baseline['policies']), owners=copy.deepcopy(baseline['owners']),
                predicates=predicates, otherJson=other, otherHash=E.sha(other.encode('utf-16le')),
                columns=[dict(target=t, systemType=56, userType=56, computed=False) for t in E.TARGETS])


class EvidenceTest(unittest.TestCase):
    def setUp(self):
        self.baseline = unit_baseline()
        self.actual = catalog(self.baseline)

    def review(self):
        return E.compare(self.baseline, self.actual)

    def test_fixture_and_composed_counts_precede_unchanged_116_gate(self):
        # Synthetic shape reproduces the measured fixture counts, not its SQL bytes.
        output = StringIO()
        with redirect_stdout(output):
            result = unit_baseline(diagnostics=True)
        self.assertEqual([
            'Parsed fixture predicates: total=80 target=3 non-target=77',
            '  ["sec","TenantFilter"] BLOCK: total=17 target=0 non-target=17',
            '  ["sec","TenantFilter"] FILTER: total=63 target=3 non-target=60',
        ], output.getvalue().splitlines()[:3])
        self.assertEqual([
            'Parsed Stage 09 predicates: total=119 target=3 non-target=116',
            '  ["sec","TenantFilter"] BLOCK: total=43 target=0 non-target=43',
            '  ["sec","TenantFilter"] FILTER: total=76 target=3 non-target=73',
        ], output.getvalue().splitlines()[-3:])
        rows, sources = E.migration_inventory(result['policies'])
        with patch.object(E, 'migration_inventory', return_value=(rows[:-1], sources)), \
                self.assertRaisesRegex(ValueError, 'exactly 116.*parsed 115'):
            unit_baseline()

    def test_diagnostic_groups_all_policies_types_and_preserves_duplicates(self):
        target = dict(policy=['sec', 'TenantFilter'], kind='FILTER', target=['dbo', 'Users'])
        other = dict(policy=['sec', 'OtherPolicy'], kind='BLOCK', target=['dbo', 'Unit'])
        counts = E.predicate_counts([target, other, other])
        self.assertEqual((3, 1, 2), (counts['total'], counts['target'], counts['nonTarget']))
        self.assertEqual([
            dict(policy=['sec', 'OtherPolicy'], kind='BLOCK', target=0, nonTarget=2),
            dict(policy=['sec', 'TenantFilter'], kind='FILTER', target=1, nonTarget=0),
        ], counts['byPolicyAndType'])

    def test_reviewed_migrations_are_derived_in_manifest_order_with_new_owners(self):
        rows, sources = E.migration_inventory(self.baseline['policies'])
        self.assertEqual([27, 3, 3, 0, 0, 0, 6], [s['predicateCount'] for s in sources])
        self.assertEqual([s[0] for s in E.STAGE_SOURCES], [s['stage'] for s in sources])
        self.assertEqual((39, 13, 26), (len(rows), sum(r['kind'] == 'FILTER' for r in rows),
                                       sum(r['kind'] == 'BLOCK' for r in rows)))
        owners = {tuple(o['name']): o for o in self.baseline['owners']}
        for row in rows:
            self.assertIn(row, self.baseline['predicates'])
            self.assertEqual('dbo', owners[tuple(row['target'])]['owner'])
        self.assertEqual(77, self.baseline['fixtureCounts']['nonTarget'])
        self.assertEqual(116, E.predicate_counts(self.baseline['predicates'])['nonTarget'])

    def test_migration_parser_keeps_policy_target_kind_operation_function_and_ordered_args(self):
        sql = "EXEC(N'ALTER SECURITY POLICY [sec].[TenantFilter] ADD BLOCK PREDICATE [sec].[F]([A], [B]) ON [dbo].[Unit] BEFORE DELETE;');"
        rows, _ = E.migration_predicates(sql, ['sec', 'TenantFilter'])
        self.assertEqual([dict(policy=['sec', 'TenantFilter'], target=['dbo', 'Unit'], kind='BLOCK',
                               operation='BEFORE DELETE', function=['sec', 'F'], arguments=[['A'], ['B']])], rows)

    def test_unsupported_security_sql_is_never_silently_ignored(self):
        for sql in [
            'ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.F(A) ON dbo.T;',
            "EXEC(N'ALTER SECURITY POLICY sec.TenantFilter WITH (STATE=OFF);');",
            "EXEC(N'ALTER SECURITY POLICY sec.TenantFilter DROP FILTER PREDICATE ON dbo.T;');",
            "EXEC(N'ALTER SECURITY POLICY sec.Other ADD FILTER PREDICATE sec.F(A) ON dbo.T;');",
            "EXEC(N'ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.F(A,) ON dbo.T;');",
            "EXEC(N'ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.F(A) ON dbo.T; SELECT 1;');",
            "EXEC(N'ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.F(A) ON dbo.T;' + @suffix);",
            "SET @sql=N'ALTER SECURITY POLICY '+@other+N' ADD FILTER PREDICATE sec.F(A) ON dbo.T;'; EXEC sys.sp_executesql @sql;",
            "EXEC(N'CREATE PROCEDURE dbo.P AS EXEC(N''ALTER SECURITY POLICY sec.TenantFilter WITH (STATE=OFF);'');');",
        ]:
            with self.subTest(sql=sql), self.assertRaises(ValueError):
                E.migration_predicates(sql, ['sec', 'TenantFilter'])

    def test_changed_missing_duplicated_or_unexpected_source_predicates_fail_hash_review(self):
        source = E.ROOT / 'docs/sql/ai-integration/phase3b2b.sql'
        original = source.read_text()
        line = next(line for line in original.splitlines(keepends=True) if "EXEC(N'ALTER SECURITY POLICY" in line)
        variants = [original.replace(line, '', 1), original + line,
                    original + line.replace('AiAuthorizationTransaction', 'Unexpected'),
                    original.replace('sec.fn_FilterByTenant(ShaleClientId)', 'sec.Other(ShaleClientId)', 1),
                    original.replace('sec.fn_FilterByTenant(ShaleClientId)', 'sec.fn_FilterByTenant(WrongColumn)', 1),
                    original.replace('AFTER INSERT;', 'BEFORE DELETE;', 1),
                    original.replace('POLICY sec.TenantFilter ADD', 'POLICY sec.Other ADD', 1)]
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for path in ['build/azure-mcp-acceptance/stages.json'] + [
                    'docs/sql/ai-integration/' + file for _, file, _ in E.STAGE_SOURCES]:
                (root / path).parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(E.ROOT / path, root / path)
            target = root / source.relative_to(E.ROOT)
            for variant in variants:
                with self.subTest(variant=variants.index(variant)):
                    self.assertNotEqual(original, variant)
                    target.write_bytes(variant.encode('utf-8'))
                    with self.assertRaisesRegex(ValueError, 'unreviewed migration source SHA256'):
                        E.migration_inventory(self.baseline['policies'], root=root)
            target.write_bytes(original.encode('utf-8'))
            manifest_path = root / 'build/azure-mcp-acceptance/stages.json'
            manifest = json.loads(manifest_path.read_text())
            manifest[3], manifest[4] = manifest[4], manifest[3]
            manifest_path.write_text(json.dumps(manifest), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'source order/manifest differs'):
                E.migration_inventory(self.baseline['policies'], root=root)

    def test_duplicate_and_conflicting_binding_slots_fail_even_when_function_differs(self):
        rows, _ = E.migration_inventory(self.baseline['policies'])
        for replacement in [copy.deepcopy(rows[0]), dict(rows[0], function=['sec', 'Other'])]:
            with self.assertRaisesRegex(ValueError, 'duplicate/conflicting predicate slot'):
                E.unique_predicates(rows + [replacement])
        # A fixture declaration conflicting with a migration table must also stop.
        with self.assertRaisesRegex(ValueError, 'conflicting table definitions'):
            unit_baseline(lambda batches: ['CREATE TABLE dbo.TenantAiIntegrationPolicy(Id int);'] + batches)

    def test_complete_migration_inventory_comparison_rejects_catalog_and_expected_drift(self):
        rows, _ = E.migration_inventory(self.baseline['policies'])
        index = self.baseline['predicates'].index(rows[0])
        for change in [lambda ps: ps.pop(index), lambda ps: ps.append(copy.deepcopy(ps[index])),
                       lambda ps: ps[index].update(target=['dbo', 'Unexpected']),
                       lambda ps: ps[index].update(function=['sec', 'Other']),
                       lambda ps: ps[index].update(arguments=[['WrongColumn'], ['ShaleClientId']]),
                       lambda ps: ps[index].update(operation='AFTER UPDATE'),
                       lambda ps: ps[index].update(policy=['sec', 'Other'])]:
            altered = copy.deepcopy(self.baseline)
            change(altered['predicates'])
            self.assertEqual('FAIL', E.compare(self.baseline, catalog(altered))['outcome'])
            try:
                report = E.compare(altered, catalog(altered))
            except ValueError:  # malformed expected input: CLI also returns FAIL
                continue
            self.assertEqual('FAIL', report['outcome'], 'Expected-file edits cannot bless migration drift')

    def test_policy_state_and_old_baseline_format_still_fail(self):
        with self.assertRaisesRegex(ValueError, 'sole enabled schema-bound'):
            unit_baseline(lambda batches: [b.replace('SCHEMABINDING=ON', 'SCHEMABINDING=OFF') for b in batches])
        old = copy.deepcopy(self.baseline)
        old['version'] = 1
        self.assertEqual('FAIL', E.compare(old, self.actual)['outcome'])

    def test_complete_match_and_database_specific_id_remapping(self):
        first = self.review()
        second = E.compare(self.baseline, catalog(self.baseline, 90000))
        self.assertEqual('PASS', first['outcome'])
        self.assertEqual('PASS', second['outcome'])
        self.assertNotEqual(first['observedOtherPredicatesHash'], second['observedOtherPredicatesHash'])
        self.assertFalse(first['normalizationApproved'])

    def test_module_byte_change_reports_offset_even_with_consistent_new_hash(self):
        f = self.actual['functions'][0]
        raw = bytes.fromhex(f['definitionHex']).replace(b'\r\x00\n\x00', b'\n\x00')
        f.update(definitionHex=raw.hex(), byteLength=len(raw), hash=E.sha(raw))
        report = self.review()
        self.assertEqual('FAIL', report['outcome'])
        self.assertTrue(any('byte mismatch at offset' in d for d in report['discrepancies']))

    def test_missing_extra_duplicate_function_argument_policy_and_operation_are_reported(self):
        for field, value in [('definition', '([sec].[fn_FilterByTenant]([WrongColumn]))'),
                             ('definition', '([sec].[OtherFunction]([ShaleClientId]))'),
                             ('policy', ['sec', 'OtherPolicy']), ('target', ['dbo', 'OtherTable']),
                             ('operation', 'BEFORE UPDATE')]:
            with self.subTest(field=field, value=value):
                a = catalog(self.baseline)
                a['predicates'][4][field] = value
                report = E.compare(self.baseline, a)
                self.assertEqual('FAIL', report['outcome'])
                self.assertTrue(any('predicate: missing' in d for d in report['discrepancies']))
        self.actual['predicates'].append(copy.deepcopy(self.actual['predicates'][4]))
        self.assertEqual('FAIL', self.review()['outcome'])

    def test_target_conflict_receipt_ownership_signature_dependency_and_policy_drift(self):
        changes = [lambda a: a['predicates'][0].update(kind='BLOCK', operation='AFTER INSERT'),
                   lambda a: a['identity'].update(receipts=1),
                   lambda a: a['identity'].update(invocation=123),
                   lambda a: a['identity'].update(database='Shale'),
                   lambda a: a['owners'][0].update(owner='unexpected'),
                   lambda a: a['functions'][0]['parameters'][0].update(systemType=127),
                   lambda a: a['functions'][0].update(dependencies=[{'entityName': 'Unexpected'}]),
                   lambda a: a['functions'][0].update(bound=False),
                   lambda a: a['policies'][0].update(enabled=False),
                   lambda a: a['columns'][0].update(computed=True)]
        for change in changes:
            a = catalog(self.baseline)
            change(a)
            self.assertEqual('FAIL', E.compare(self.baseline, a)['outcome'])

    def test_empty_dependencies_require_independent_zero_count_and_actual_array(self):
        self.assertEqual('PASS', self.review()['outcome'])
        for field in ('dependencies', 'dependencyCount'):
            a = catalog(self.baseline)
            del a['functions'][0][field]
            report = E.compare(self.baseline, a)
            self.assertEqual('FAIL', report['outcome'])
            self.assertIn('missing', '\n'.join(report['discrepancies']))
        for malformed in (None, {}, '[]', 0, False):
            a = catalog(self.baseline)
            a['functions'][0]['dependencies'] = malformed
            report = E.compare(self.baseline, a)
            self.assertEqual('FAIL', report['outcome'], 'A zero count must never normalize missing/malformed JSON')
            self.assertIn('non-array evidence', '\n'.join(report['discrepancies']))
        for invalid_count in (None, -1, '0', False, 0.0, 1):
            a = catalog(self.baseline)
            a['functions'][0]['dependencyCount'] = invalid_count
            self.assertEqual('FAIL', E.compare(self.baseline, a)['outcome'])

    def test_unexpected_dependencies_fail_even_with_consistent_positive_count(self):
        self.actual['functions'][0].update(dependencyCount=1, dependencies=[{'entityName': 'Unexpected'}])
        report = self.review()
        self.assertEqual('FAIL', report['outcome'])
        self.assertTrue(any('dependencies: expected [], actual [' in d for d in report['discrepancies']))
        self.assertFalse(any('count/array length' in d for d in report['discrepancies']))

    def test_collector_empty_array_is_guarded_by_same_function_catalog_count(self):
        sql = Path(__file__).with_name('Collect-Phase1R.sql').read_text()
        self.assertIn('dep.[DependencyCount] AS [dependencyCount]', sql)
        self.assertIn("JSON_QUERY(CASE WHEN dep.[DependencyCount]=0 THEN N'[]' ELSE", sql)
        self.assertIn('CROSS APPLY (SELECT COUNT_BIG(*) AS [DependencyCount]', sql)
        self.assertIn('WHERE counted.referencing_id=o.object_id', sql)
        self.assertIn('WHERE d.referencing_id=o.object_id', sql)
        self.assertNotRegex(sql, r'(?is)(?:COALESCE|ISNULL)\s*\([^;]*N\'\[\]\'')

    def test_raw_catalog_hash_and_resolved_id_mismatch_fail(self):
        self.actual['otherHash'] = '0' * 64
        self.assertIn('other hash integrity', '\n'.join(self.review()['discrepancies']))
        self.actual = catalog(self.baseline)
        self.actual['predicates'][4]['targetId'] = 99999
        self.assertIn('raw/resolved', '\n'.join(self.review()['discrepancies']))

    def test_export_transport_roundtrip_truncation_and_unexpected_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'capture.hex'
            raw = json.dumps(self.actual).encode('utf-16le').hex()
            path.write_text('\ufeff' + '\n'.join(raw[i:i + 2048] for i in range(0, len(raw), 2048)), encoding='utf-8')
            self.assertEqual(self.actual, E.capture(path))
            for value in [raw[:-4], 'SQLCMD error\n' + raw, '']:
                path.write_text(value, encoding='utf-8')
                with self.assertRaises((ValueError, UnicodeError)):
                    E.capture(path)

    def test_policy_lexer_nested_comments_ordered_args_and_unsupported_syntax(self):
        p, rows = E.policy(E.tokens('/* a /* b */ c */ CREATE SECURITY POLICY [sec].[P] '
                                   'ADD BLOCK PREDICATE [sec].[F]([A], [B]) ON [dbo].[T] BEFORE DELETE WITH (STATE=ON);'))
        self.assertTrue(p['bound'])
        self.assertEqual([['A'], ['B']], rows[0]['arguments'])
        self.assertEqual('BEFORE DELETE', rows[0]['operation'])
        with self.assertRaises(ValueError):
            E.policy(E.tokens('CREATE SECURITY POLICY sec.P WITH (STATE=ON) NOT FOR REPLICATION;'))
        with self.assertRaises(ValueError):
            E.tokens('/* incomplete')

    def test_baseline_refuses_unproven_dependencies_signature_and_wrong_inventory(self):
        for old, new in [('RETURN SELECT', 'RETURN SELECT dbo.OtherFunction() +'),
                         ('RETURN SELECT', 'RETURN SELECT * FROM dbo.OtherTable WHERE'),
                         ('@tenant int', '@tenant bigint'),
                         ('AUTHORIZATION dbo', 'AUTHORIZATION OtherOwner'),
                         ('dbo.Unit59', 'dbo.Users')]:
            with self.subTest(new=new), self.assertRaises(ValueError):
                unit_baseline(lambda batches: [b.replace(old, new) for b in batches])

    def test_hash_gate_refuses_before_launching_any_process(self):
        with tempfile.TemporaryDirectory() as tmp:
            fixture = Path(tmp) / 'unit.sql'
            fixture.write_text('unit data only', encoding='utf-8')
            with patch.object(E.subprocess, 'run') as process:
                with self.assertRaisesRegex(ValueError, 'fixture hash mismatch'):
                    E.expected(fixture, Path(tmp) / 'expected.json')
                process.assert_not_called()

    def test_cli_pass_failure_and_create_new_reports(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            expected, capture, report = (root / n for n in ('expected.json', 'capture.hex', 'report.json'))
            expected.write_text(json.dumps(self.baseline), encoding='utf-8')
            capture.write_text(json.dumps(self.actual).encode('utf-16le').hex(), encoding='utf-8')
            command = [sys.executable, str(Path(E.__file__)), 'compare', '--expected', str(expected),
                       '--capture', str(capture), '--report', str(report)]
            result = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr + result.stdout)
            self.assertEqual('PASS', json.loads(report.read_text())['outcome'])
            original = report.read_bytes()
            self.assertNotEqual(0, subprocess.run(command, capture_output=True).returncode)
            self.assertEqual(original, report.read_bytes(), 'Existing report must never be overwritten')
            report.unlink()
            capture.write_text('truncated', encoding='utf-8')
            self.assertNotEqual(0, subprocess.run(command, capture_output=True).returncode)
            self.assertEqual('FAIL', json.loads(report.read_text())['outcome'])

    def test_collector_contains_only_system_catalog_reads_and_no_owner_row_fence(self):
        sql = Path(__file__).with_name('Collect-Phase1R.sql').read_text()
        # No FROM/JOIN of any application schema, no owner.sql include.
        self.assertNotRegex(sql, r'(?i)\b(?:FROM|JOIN)\s+(?:dbo|sec)\.')
        self.assertNotIn(':r docs/sql/ai-integration/azure-acceptance/owner.sql', sql)
        self.assertIn('SELECT p.* FROM sys.security_predicates p', sql)
        self.assertNotRegex(sql, r'(?im)^\s*(?:ALTER|INSERT|UPDATE|DELETE|EXEC|GRANT|DENY|REVOKE|CREATE)\b')


if __name__ == '__main__':
    unittest.main()
