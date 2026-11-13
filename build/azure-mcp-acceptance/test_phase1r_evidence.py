"""Offline tests only: synthetic catalog payloads; never invoke SQLCMD/JDBC."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import subprocess
import sys
from contextlib import redirect_stdout
from io import StringIO
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('evidence', Path(__file__).with_name('phase1r_evidence.py'))
E = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(E)


def unit_baseline(transform=lambda batches: batches, *, diagnostics=False,
                  non_target_filters=115, block_operations=('AFTER UPDATE',)):
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
                              type='IF', bound=True, ansi=True, quoted=True, executeAs=None, dependencies=[],
                              parameters=[dict(id=1, name=f['parameter'], systemType=56, userType=56, output=False)]))
    predicates, raw = [], []
    for i, p in enumerate(baseline['predicates']):
        definition = '([' + p['function'][0] + '].[' + p['function'][1] + ']([' + p['arguments'][0][0] + ']))'
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

    def test_fixture_count_diagnostic_precedes_unchanged_116_failure(self):
        # Synthetic shape reproduces the measured fixture counts, not its SQL bytes.
        output = StringIO()
        with redirect_stdout(output), self.assertRaisesRegex(ValueError, 'exactly 116.*parsed 77'):
            unit_baseline(diagnostics=True, non_target_filters=60,
                          block_operations=('AFTER INSERT',) * 8 + ('AFTER UPDATE',) * 8 + ('BEFORE UPDATE',))
        self.assertEqual([
            'Parsed fixture predicates: total=80 target=3 non-target=77',
            '  ["sec","TenantFilter"] BLOCK: total=17 target=0 non-target=17',
            '  ["sec","TenantFilter"] FILTER: total=63 target=3 non-target=60',
        ], output.getvalue().splitlines())

    def test_diagnostic_groups_all_policies_types_and_preserves_duplicates(self):
        target = dict(policy=['sec', 'TenantFilter'], kind='FILTER', target=['dbo', 'Users'])
        other = dict(policy=['sec', 'OtherPolicy'], kind='BLOCK', target=['dbo', 'Unit'])
        counts = E.predicate_counts([target, other, other])
        self.assertEqual((3, 1, 2), (counts['total'], counts['target'], counts['nonTarget']))
        self.assertEqual([
            dict(policy=['sec', 'OtherPolicy'], kind='BLOCK', target=0, nonTarget=2),
            dict(policy=['sec', 'TenantFilter'], kind='FILTER', target=1, nonTarget=0),
        ], counts['byPolicyAndType'])

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
                         ('dbo.Unit114', 'dbo.Users')]:
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
