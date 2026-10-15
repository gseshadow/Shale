#!/usr/bin/env python3
"""Offline full-payload and bounded coverage assertions. Never authorizes live activation."""
import argparse
import collections
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET


class Rejected(ValueError):
    pass


def require(ok, reason):
    if not ok:
        raise Rejected(reason)


# Exact types/packages and complete child/attribute vocabulary, including intrinsic fields.
TYPES = {'source_database_id': 'uint32', 'object_id': 'int32', 'line_number': 'int32',
         'offset': 'int32', 'offset_end': 'int32', 'object_type': 'ansi_string',
         'object_name': 'unicode_string', 'statement': 'unicode_string',
         'duration': 'uint64', 'row_count': 'uint64'}
BASE = set(TYPES) - {'duration', 'row_count'}
ROOT_ATTRS = {'truncated', 'processingTime', 'totalEventsProcessed', 'eventCount',
              'droppedCount', 'memoryUsed'}


def raw_payload(raw, database_id, modules):
    require('IV_FORBIDDEN_' not in raw.upper(), 'privacy: synthetic sentinel captured')
    require('<!DOCTYPE' not in raw.upper() and '<!ENTITY' not in raw.upper(), 'unexpected XML declarations')
    try:
        root = ET.fromstring(raw, parser=ET.XMLParser(target=ET.TreeBuilder(insert_comments=True, insert_pis=True)))
    except ET.ParseError:
        raise Rejected('raw target XML unavailable or malformed') from None
    require(root.tag == 'RingBufferTarget' and set(root.attrib) == ROOT_ATTRS, 'unexpected target attributes')
    for value in root.attrib.values():
        require(re.fullmatch(r'\d+', value), 'unexpected target value')
    for node in root.iter():
        require(isinstance(node.tag, str), 'unexpected XML comment/instruction')
        require(not (node.tail or '').strip(), 'unexpected raw trailing content')
        if node.tag != 'value':
            require(not (node.text or '').strip(), 'unexpected raw intrinsic content')
    counts = collections.Counter()
    for event in root:
        require(event.tag == 'event' and set(event.attrib) == {'name', 'package', 'timestamp'}, 'unexpected event envelope/causality')
        name = event.get('name')
        require(name in {'module_start', 'module_end'} and event.get('package') == 'sqlserver', 'unexpected event')
        require(re.fullmatch(r'\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z', event.get('timestamp', '')), 'event time not UTC')
        fields = {}
        for data in event:
            require(data.tag == 'data' and set(data.attrib) == {'name'}, 'unexpected action or intrinsic node')
            field = data.get('name')
            require(field in TYPES and field not in fields, 'unexpected/duplicate intrinsic field')
            children = list(data)
            require([c.tag for c in children] == ['type', 'value'], 'unexpected intrinsic serialization')
            typ, val = children
            require(typ.attrib == {'name': TYPES[field], 'package': 'package0'} and not list(typ) and not (typ.text or '').strip(), 'unexpected intrinsic type')
            require(not val.attrib and not list(val), 'nested/attributed intrinsic value')
            fields[field] = val.text or ''
        expected = BASE | ({'duration', 'row_count'} if name == 'module_end' else set())
        # statement may be absent or empty when collect_statement=0; nonempty ALWAYS fails.
        require(set(fields) in (expected, expected - {'statement'}), 'missing or extra intrinsic fields')
        require(fields.get('statement', '') == '', 'privacy: intrinsic statement captured')
        for field in set(fields) - {'statement', 'object_type', 'object_name'}:
            require(re.fullmatch(r'-?\d+', fields[field]), 'unexpected nonnumeric execution metadata')
        require(int(fields['source_database_id']) == database_id, 'wrong database captured')
        oid = int(fields['object_id'])
        require(oid in modules and fields['object_name'] == modules[oid], 'unrelated module captured')
        require(fields['object_type'].strip() == 'P', 'unexpected module type')
        counts[oid, name] += 1
    require(int(root.get('eventCount')) == len(root), 'target eventCount/serialization mismatch')
    return root, counts


def overlapping_trial(root, oid):
    times = {'module_start': [], 'module_end': []}
    for event in root:
        if int(event.find("data[@name='object_id']/value").text) == oid:
            times[event.get('name')].append(event.get('timestamp'))
    starts = sorted(times['module_start'])
    ends = sorted(times['module_end'])
    require(len(starts) >= 5 and len(ends) >= 5 and
            max(starts[3:5]) < min(ends[3:5]), 'concurrent calls did not demonstrably overlap')


def complete(root, row, expected_total):
    for key in ('DroppedEvents', 'DroppedBuffers', 'LargestDroppedEvent', 'FailedTargetBuffers'):
        require(type(row.get(key)) is int and row[key] == 0, 'session event loss or unavailable health')
    require(row.get('TargetName') == 'ring_buffer' and row.get('TargetExecutions', 0) > 0, 'target not processing')
    require(root.get('truncated') == '0' and root.get('droppedCount') == '0', 'target truncated/dropped')
    require(int(root.get('totalEventsProcessed')) == int(root.get('eventCount')) == expected_total, 'target overwrite, missing or extra events')


def as_rows(value):
    return value if isinstance(value, list) else [value]


def validate(directory, state):
    directory = Path(directory)
    require(state['Status'] in {'Collected', 'Cleaned'}, 'workload incomplete')
    require(re.fullmatch('[a-f0-9]{32}', state['Token']), 'invalid ownership token')
    for item in state['Files']:
        require(re.fullmatch(r'[a-z0-9-]+\.json', item['Name']), 'unsafe evidence path')
        data = (directory / item['Name']).read_bytes()
        require(hashlib.sha256(data).hexdigest().upper() == item['SHA256'].upper(), 'evidence hash mismatch')
    def read(name):
        require(name in {i['Name'] for i in state['Files']}, 'unowned/missing evidence')
        return json.loads((directory / name).read_text(encoding='utf-8-sig'))
    modules = {int(m['Id']): m['Name'] for m in state['Modules']}
    require(len(modules) == 3 and set(modules.values()) == {'probe_a', 'probe_b', 'probe_c'}, 'module mapping incomplete')
    dbid = int(state['Facts']['DatabaseId'])
    # Inspect EVERY captured raw target, including final/emergency/cancel-entry snapshots.
    def inspect(value):
        if isinstance(value, list):
            for child in value:
                inspect(child)
        elif isinstance(value, dict):
            for key, child in value.items():
                if key == 'RawTarget':
                    require(isinstance(child, str), 'raw payload missing')
                    raw_payload(child, dbid, modules)
                else:
                    inspect(child)
    for item in state['Files']:
        inspect(read(item['Name']))
    results = {}
    for kind in ('normal', 'stress', 'wrongdb'):
        before = as_rows(read(kind + '-before.json'))
        after = as_rows(read(kind + '-after.json'))
        require(len(before) == len(after) == 1, 'monitor missing/multiple targets')
        b, a = before[0], after[0]
        require(b['SessionName'] == a['SessionName'] == 'iv_' + state['Token'] + '_' + kind, 'session identity mismatch')
        require(b['StartedAt'] == a['StartedAt'], 'session restart/discontinuity')
        require(b['CollectedUtc'] < a['CollectedUtc'], 'stale or reversed collection boundary')
        first, _ = raw_payload(b['RawTarget'], dbid, modules)
        require(int(first.get('totalEventsProcessed')) == 0, 'nonempty initial interval')
        root, counts = raw_payload(a['RawTarget'], dbid, modules)
        require(int(root.get('memoryUsed')) <= 1024 * 1024, 'target memory bound exceeded')
        require(a.get('BufferSize', 0) > 0 and a.get('RegularBuffers', 0) > 0, 'session buffer health unavailable')
        require(read(kind + '-stopped.json') == [], 'monitor stop not verified')
        if kind == 'normal':
            for oid in modules:
                require(counts[oid, 'module_start'] == 7, 'direct/nested/dynamic/concurrent/failed/cancelled entry count mismatch')
                require(counts[oid, 'module_end'] in (6, 7), 'exit coverage differs; cancelled completion unresolved')
                overlapping_trial(root, oid)
            complete(root, a, sum(counts.values()))
            results['normal'] = {modules[o]: {'starts': counts[o, 'module_start'], 'ends': counts[o, 'module_end']} for o in modules}
        elif kind == 'wrongdb':
            require(not counts and int(root.get('totalEventsProcessed')) == 0, 'database predicate failed to exclude wrong database ID')
            results['wrong_database_predicate'] = 'Excluded all six expected source events'
        else:
            require(int(root.get('eventCount')) <= 8, 'configured target bound exceeded')
            try:
                complete(root, a, 2000)
            except Rejected:
                results['stress'] = 'INCOMPLETE interval correctly rejected'
            else:
                raise Rejected('stress loss/overwrite was not demonstrated')
            require(int(root.get('totalEventsProcessed')) > int(root.get('eventCount')) or a['DroppedEvents'] > 0 or a['DroppedBuffers'] > 0, 'stress health did not demonstrate loss')
    return {'bounded_synthetic_checks': results, 'production_activation': 'REFUSED',
            'unrun': ['Azure checks unless operator ran this exact package there',
                      'cross-database context/colliding object IDs', 'restart/failover/replicas',
                      'durable multi-day continuity', 'fleet/job inventory and installed desktop acceptance'],
            'gate': 'Synthetic success never closes #1874 retirement or authorizes a live candidate'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--state', type=Path, required=True)
    args = parser.parse_args()
    try:
        state = json.loads(args.state.read_text(encoding='utf-8-sig'))
        result = validate(state['Directory'], state)
        print(json.dumps(result, indent=2))
    except (Rejected, OSError, KeyError, TypeError, ValueError):
        print('REFUSED: privacy/coverage/evidence assertion failed; preserve complete raw evidence. Production activation REFUSED.')
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
