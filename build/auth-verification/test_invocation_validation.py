"""Fail closed when intrinsic payloads leak data or lossy telemetry looks healthy."""
import copy
import importlib.util
import hashlib
import json
import tempfile
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

    def test_complete_evidence_rejects_stopped_stale_restarted_and_hidden_final_payload(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            token = 'a' * 32
            state = dict(Token=token, Status='Collected', Facts={'DatabaseId': 5},
                         Modules=[dict(Id=i, Name=n) for i, n in [(42, 'probe_a'), (43, 'probe_b'), (44, 'probe_c')]], Files=[])
            def write(name, value):
                data = json.dumps(value).encode()
                (directory / name).write_bytes(data)
                state['Files'] = [i for i in state['Files'] if i['Name'] != name]
                state['Files'].append(dict(Name=name, SHA256=hashlib.sha256(data).hexdigest()))
            empty = payload(totalEventsProcessed='0', eventCount='0')
            empty.remove(empty.find('event'))
            normal = copy.deepcopy(empty)
            for oid, name in [(42, 'probe_a'), (43, 'probe_b'), (44, 'probe_c')]:
                for event_name in ['module_start', 'module_end']:
                    for trial in range(7):
                        event = copy.deepcopy(payload().find('event'))
                        event.set('name', event_name)
                        seconds = [1, 3, 5, 7, 7, 10, 12][trial]
                        fraction = (100 if trial == 4 else 0) + (500 if event_name == 'module_end' else 0)
                        event.set('timestamp', '2026-10-15T22:29:%02d.%03dZ' % (seconds, fraction))
                        event.find("data[@name='object_id']/value").text = str(oid)
                        event.find("data[@name='object_name']/value").text = name
                        if event_name == 'module_end':
                            for field in ['duration', 'row_count']:
                                data = ET.SubElement(event, 'data', name=field)
                                ET.SubElement(data, 'type', name=v.TYPES[field], package='package0')
                                ET.SubElement(data, 'value').text = '0'
                        normal.append(event)
            normal.set('eventCount', '42')
            normal.set('totalEventsProcessed', '42')
            stress = copy.deepcopy(normal)
            for event in list(stress)[8:]:
                stress.remove(event)
            stress.set('eventCount', '8')
            stress.set('totalEventsProcessed', '2000')
            for kind, raw in [('normal', normal), ('stress', stress), ('wrongdb', empty)]:
                row = dict(SessionName='iv_' + token + '_' + kind, StartedAt='same-start',
                           CollectedUtc='2026-10-15T22:29:04', DroppedEvents=0, DroppedBuffers=0,
                           LargestDroppedEvent=0, FailedTargetBuffers=0, BufferSize=1024,
                           RegularBuffers=3, TargetName='ring_buffer', TargetExecutions=1,
                           RawTarget=ET.tostring(empty, encoding='unicode'))
                write(kind + '-before.json', [copy.deepcopy(row)])
                row['CollectedUtc'] = '2026-10-15T22:29:10'
                row['RawTarget'] = ET.tostring(raw, encoding='unicode')
                write(kind + '-after.json', [copy.deepcopy(row)])
                write(kind + '-stopped.json', [])
            self.assertEqual('REFUSED', v.validate(directory, state)['production_activation'])
            original = json.loads((directory / 'normal-after.json').read_text())
            for key, value in [('StartedAt', 'different-start'), ('CollectedUtc', '2026-10-15T22:29:04')]:
                bad = copy.deepcopy(original)
                bad[0][key] = value
                write('normal-after.json', bad)
                with self.assertRaises(v.Rejected, msg=key):
                    v.validate(directory, state)
            write('normal-after.json', original)
            failed_overlap = ET.fromstring(original[0]['RawTarget'])
            for event in failed_overlap:
                if event.get('name') == 'module_start':
                    event.set('timestamp', '2026-10-15T22:29:59.000Z')
            bad = copy.deepcopy(original)
            bad[0]['RawTarget'] = ET.tostring(failed_overlap, encoding='unicode')
            write('normal-after.json', bad)
            with self.assertRaises(v.Rejected):
                v.validate(directory, state)
            write('normal-after.json', original)
            write('normal-stopped.json', original)
            with self.assertRaises(v.Rejected):
                v.validate(directory, state)
            write('normal-stopped.json', [])
            # Even a leaked field only in a final/emergency snapshot must invalidate acceptance.
            hidden = payload()
            hidden.find("event/data[@name='statement']/value").text = 'EXEC private_module'
            write('normal-final-unique.json', [dict(RawTarget=ET.tostring(hidden, encoding='unicode'))])
            with self.assertRaises(v.Rejected):
                v.validate(directory, state)

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
