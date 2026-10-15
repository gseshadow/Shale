"""Fail closed when intrinsic payloads leak data or lossy telemetry looks healthy."""
import copy
import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = ROOT / 'docs/sql/auth/invocation-validation'
spec = importlib.util.spec_from_file_location('invocation_assertions', PACKAGE / 'assertions.py')
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)


def payload(**changes):
    attrs = dict(truncated='0', processingTime='0', totalEventsProcessed='1', eventCount='1', droppedCount='0', memoryUsed='100')
    attrs.update(changes)
    root = ET.Element('RingBufferTarget', attrs)
    event = ET.SubElement(root, 'event', name='module_start', package='sqlserver', timestamp='2026-10-15T22:29:04.000Z')
    values = dict(source_database_id='5', object_id='42', line_number='1', offset='0', offset_end='-1', object_type='P ', object_name='probe_a', statement='')
    for field, value in values.items():
        data = ET.SubElement(event, 'data', name=field)
        ET.SubElement(data, 'type', name=v.TYPES[field], package='package0')
        ET.SubElement(data, 'value').text = value
    return root


def parse(root):
    return v.raw_payload(ET.tostring(root, encoding='unicode'), 5, {42: 'probe_a'})


class InvocationValidationTest(unittest.TestCase):
    def test_disabled_intrinsic_statement_and_identity_are_reviewed(self):
        root, counts = parse(payload())
        self.assertEqual(1, counts[42, 'module_start'])
        for value in ['EXEC dbo.private_module', 'IV_FORBIDDEN_TOKEN', 'ordinary-private-value']:
            raw = payload()
            raw.find("event/data[@name='statement']/value").text = value
            with self.assertRaises(v.Rejected, msg='nonempty intrinsic SQL must fail even without a sentinel'):
                parse(raw)

    def test_unknown_actions_intrinsics_causality_and_nested_values_fail(self):
        for kind in ('action', 'extra', 'causality', 'nested', 'comment', 'raw-text'):
            raw = payload()
            event = raw.find('event')
            if kind == 'action':
                ET.SubElement(event, 'action', name='sql_text')
            elif kind == 'extra':
                ET.SubElement(event, 'data', name='parameters')
            elif kind == 'causality':
                event.set('activity_id', 'unreviewed')
            elif kind == 'nested':
                ET.SubElement(event.find("data[@name='statement']/value"), 'private')
            elif kind == 'comment':
                event.append(ET.Comment('unreviewed payload'))
            else:
                event.text = 'unreviewed payload'
            with self.assertRaises(v.Rejected, msg=kind):
                parse(raw)

    def test_exact_module_and_database_exclude_near_names_and_collisions(self):
        for field, value in [('object_id', '43'), ('source_database_id', '6'), ('object_name', 'probe_a_decoy')]:
            raw = payload()
            raw.find("event/data[@name='%s']/value" % field).text = value
            with self.assertRaises(v.Rejected):
                parse(raw)

    def test_target_overwrite_is_incomplete_even_when_session_drops_are_zero(self):
        row = dict(DroppedEvents=0, DroppedBuffers=0, LargestDroppedEvent=0,
                   FailedTargetBuffers=0, TargetName='ring_buffer', TargetExecutions=1)
        raw, _ = parse(payload())
        v.complete(raw, row, 1)
        for changes in [dict(totalEventsProcessed='1000'), dict(truncated='1'), dict(droppedCount='1')]:
            with self.assertRaises(v.Rejected):
                v.complete(payload(**changes), row, 1)
        for key in ['DroppedEvents', 'DroppedBuffers', 'LargestDroppedEvent', 'FailedTargetBuffers', 'TargetExecutions']:
            bad = copy.copy(row)
            bad[key] = 1 if key != 'TargetExecutions' else 0
            with self.assertRaises(v.Rejected, msg=key):
                v.complete(raw, bad, 1)
        del row['FailedTargetBuffers']
        with self.assertRaises(v.Rejected):
            v.complete(raw, row, 1)

    def test_cleanup_never_deletes_evidence_or_adopts_names_by_prefix(self):
        script = (PACKAGE / 'harness.ps1').read_text()
        cleanup = script.split("'Cleanup' {")[1]
        self.assertNotIn('Remove-Item', script)
        self.assertIn('Verify-Objects', cleanup)
        self.assertIn('Verify-Session $session', cleanup)
        self.assertIn('RetainedEvidenceSHA256', cleanup)
        self.assertIn('Retained evidence copy missing or changed', cleanup)
        self.assertNotRegex(cleanup, r'(?i)LIKE|DROP DATABASE|DROP CREDENTIAL')
        sql = (PACKAGE / 'session.sql').read_text()
        self.assertIn('ON DATABASE', sql)
        self.assertEqual(2, sql.count('collect_statement=(0)'))
        self.assertNotIn('ACTION', sql)


if __name__ == '__main__':
    unittest.main()
