"""Security-contract tests; private production/schema bytes are opt-in execution inputs only."""
from pathlib import Path
import base64
import hashlib
import importlib.util
import json
import os
import re
import unittest

BUILD = Path(__file__).resolve().parent
REPO = BUILD.parent.parent
spec = importlib.util.spec_from_file_location('generate_v2', BUILD / 'generate-v2.py')
generator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(generator)


class CatalogV2Contract(unittest.TestCase):
    def test_active_order_retains_every_column_property(self):
        sql = generator.case_sql()
        self.assertIn('ROW_NUMBER() OVER(PARTITION BY c.object_id ORDER BY c.column_id)', sql)
        for property in ('c.name', 'TYPE_NAME(c.user_type_id)', 'c.max_length', 'c.precision', 'c.scale',
                         'c.is_nullable', 'c.is_identity', 'c.collation_name', 'cc.definition'):
            self.assertIn(property, sql)

    def test_module_variants_are_finite_and_text_is_never_normalized(self):
        sql = generator.case_sql()
        variants = json.loads((BUILD / 'reviewed-case-module-variants-v2.json').read_text())['modules']
        self.assertEqual(6, len(variants))
        for module, reviewed in variants.items():
            self.assertEqual({'authoritative', 'production'}, set(reviewed))
            self.assertEqual(2, len(set(reviewed.values())), module)
            for value in reviewed.values():
                self.assertRegex(value, r'^[0-9a-f]{64}$')
                self.assertIn('0x' + value, sql)
        self.assertNotRegex(sql, r'(?i)(REPLACE|LTRIM|RTRIM|TRIM|LOWER|UPPER)\s*\(\s*m\.definition')
        for property in ('m.uses_ansi_nulls', 'm.uses_quoted_identifier', 'm.is_schema_bound',
                         'm.execute_as_principal_id', 'p.is_output', 'p.is_readonly',
                         'sys.sql_expression_dependencies', 'd.is_caller_dependent', 'd.is_ambiguous'):
            self.assertIn(property, sql)

    def test_v1_artifacts_and_receipts_are_not_relabelled(self):
        v1 = (REPO / 'docs/sql/ai-integration/phase3b7-confinement.sql').read_text()
        v2 = (REPO / 'docs/sql/ai-integration/phase3b8-confinement.sql').read_text()
        self.assertIn('Shale.AiCaseDataV1.Source', v1)
        self.assertNotIn('Shale.AiCaseDataV2', v1)
        self.assertNotIn('Shale.AiCaseDataV1', v2)
        self.assertIn("@Resource=N'AiSchemaV1'", v2)
        self.assertIn('Unreceipted confinement objects.', v2)
        self.assertEqual(13, v2.count('SET @InstallDefinition=CONVERT(nvarchar(max),0x'))

    @unittest.skipUnless(os.getenv('SHALE_V2_PRODUCTION_CATALOG') and os.getenv('SHALE_CASE_VERIFY_SCHEMA'),
                         'Private production/candidate evidence required; skip is not acceptance')
    def test_private_exact_projection_and_only_approved_representation_differences(self):
        candidate = Path(os.environ['SHALE_CASE_VERIFY_SCHEMA']).read_bytes()
        self.assertEqual('4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f', hashlib.sha256(candidate).hexdigest())
        parts = {}
        for line in Path(os.environ['SHALE_V2_PRODUCTION_CATALOG']).read_text().splitlines():
            if not line.strip():
                continue
            name, digest, units, size, encoded = line.split('|', 4)
            raw = base64.b64decode(encoded, validate=True)
            self.assertEqual(digest.lower(), hashlib.sha256(raw).hexdigest())
            self.assertEqual(int(size), len(raw))
            self.assertEqual(int(units)*2, len(raw))
            self.assertNotIn(name, parts)
            parts[name] = raw
        self.assertEqual(['Objects', 'Columns', 'Constraints', 'ForeignKeys', 'Indexes', 'RLS', 'Triggers', 'Functions'], list(parts))
        self.assertEqual('eb39ddbfa59df338dcaa969e440c7d254b1cb27ace1211524375d786b2006ab1', hashlib.sha256(b''.join(parts.values())).hexdigest())
        rows = parts['RLS'].decode('utf-16le').split(';')[:-1]
        for table, function in [('Users', 'fn_FilterByTenant'), ('auditLog', 'fn_FilterByTenant'), ('Statuses', 'fn_FilterByTenantOrGlobal')]:
            self.assertFalse(any(row.startswith(table+':') for row in rows))
            rows.append(f'{table}:([sec].[{function}]([ShaleClientId])):0::1:1')
        rows.sort(key=lambda row: (row.split(':')[0].lower(), row.split(':')[2:]))
        projected = dict(parts)
        projected['RLS'] = (';'.join(rows)+';').encode('utf-16le')
        self.assertEqual('d4aa4b9d029044a69ba0fe10a171dd6b5447374ec3124cbfb6ea6e743e49f0cc', hashlib.sha256(b''.join(projected.values())).hexdigest())
        # The sole column representation rule replaces physical IDs by active positions.
        # Every other field, including identity and literal-looking computed text, remains significant.
        column_rows = [row.split(':') for row in parts['Columns'].decode('utf-16le').split(';') if row]
        def logical_columns(rows):
            positions = {}
            result = []
            for original in rows:
                row = original.copy()
                positions[row[0]] = positions.get(row[0], 0)+1
                row[2] = str(positions[row[0]])
                result.append(':'.join(row)+';')
            return ''.join(result).encode('utf-16le')
        baseline = logical_columns(column_rows)
        for field, value in [(1,'UnexpectedName'),(3,'bigint'),(4,'123'),(5,'123'),(6,'123'),(7,'1'),(8,'0'),(9,'Latin1_General_100_BIN2'),(10,"([email] + N'[ \\r\\n]')")]:
            changed = [row.copy() for row in column_rows]
            index = next(i for i,row in enumerate(changed) if row[0]=='Cases' and row[1]=='Id')
            self.assertNotEqual(changed[index][field], value)
            changed[index][field] = value
            self.assertNotEqual(baseline, logical_columns(changed), 'Changed column field '+str(field))
        changed = [row.copy() for row in column_rows]
        case_indices = [i for i,row in enumerate(changed) if row[0]=='Cases']
        left,right=case_indices[0:2]
        changed[left],changed[right]=changed[right],changed[left]
        self.assertNotEqual(baseline,logical_columns(changed),'Active order cannot be discarded')
        self.assertNotEqual(baseline,logical_columns(column_rows[:-1]),'Missing column cannot pass')
        self.assertNotEqual(baseline,logical_columns(column_rows+[column_rows[-1]]),'Added column cannot pass')
        variants = json.loads((BUILD / 'reviewed-case-module-variants-v2.json').read_text())['modules']
        functions = parts['Functions'].decode('utf-16le')
        for match in re.finditer(r'(dbo:ToBitNV:|sec:fn_FilterByTenant:|sec:fn_FilterByTenantOrGlobal:)(.*?)::dbo;', functions, re.S):
            module = match[1].rstrip(':').replace(':', '.')
            body = match[2]
            self.assertEqual(variants[module]['production'], hashlib.sha256(body.encode('utf-16le')).hexdigest())
            for changed in [body+' ', body.replace('INT', 'BIGINT'), body.replace("N'ShaleClientId'", "N'[ShaleClientId]'"), body.replace("'N/A'", "'N/\r\nA'"), body.replace('TRY_CONVERT', 'CONVERT')]:
                if changed != body:
                    self.assertNotIn(hashlib.sha256(changed.encode('utf-16le')).hexdigest(), variants[module].values())


if __name__ == '__main__':
    unittest.main()
