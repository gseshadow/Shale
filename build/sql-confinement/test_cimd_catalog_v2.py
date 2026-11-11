import hashlib,json,subprocess,sys,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
class SignedCatalogV2Contract(unittest.TestCase):
 def test_versioned_generation_and_predecessor_outputs_are_current(self):
  for script in ['generate-cimd-catalog-v2.py','generate-cimd-runtime-v1.py']:
   subprocess.run([sys.executable,str(ROOT/'build/sql-confinement'/script),'--check'],check=True)
 def test_fixed_body_retains_unchanged_historical_verification(self):
  body=(ROOT/'shale-data/src/main/resources/ai/cimd-runtime-v2-catalog.sql').read_text()
  prior=(ROOT/'docs/sql/ai-integration/universal-pr4b-oauth-verify.sql').read_text()
  self.assertIn("EXEC sys.sp_executesql N'"+prior.replace("'","''")+"';",body)
  self.assertTrue(body.startswith('CREATE PROCEDURE dbo.AiCimdAttestCatalogV2 WITH EXECUTE AS OWNER'))
  expected=(ROOT/'shale-data/src/main/resources/ai/cimd-runtime-v2-catalog.sha256').read_text().strip()
  self.assertEqual(expected,hashlib.sha256(body.encode('utf-16le')).hexdigest())
  self.assertLess(body.index('Restricted boxed catalog execution context required.'),body.index("EXEC sys.sp_executesql"))
 def test_signing_has_no_owner_provisioning_or_runtime_grants(self):
  install=(ROOT/'docs/sql/ai-integration/universal-pr4d-catalog.sql').read_text()
  self.assertNotIn('TRUSTWORTHY ON',install);self.assertNotIn('GRANT IMPERSONATE',install)
  self.assertNotIn('ALTER AUTHORIZATION ON DATABASE',install)
  self.assertIn('REMOVE PRIVATE KEY',install);self.assertIn('ROLLBACK TRANSACTION',install)
  self.assertIn('REVOKE CONNECT SQL FROM AiCimdCatalogSignerV2',install)
  self.assertIn('GRANT VIEW ANY DEFINITION,VIEW ANY DATABASE,AUTHENTICATE SERVER,CONNECT ANY DATABASE TO AiCimdCatalogSignerV2',install)
  self.assertNotIn('GRANT SELECT',install)
  self.assertNotIn('TO AiCimdOAuthIssuerV1',install)
 def test_system_baseline_is_exact_and_contains_no_runtime_data(self):
  b=json.loads((ROOT/'build/sql-confinement/cimd-catalog-v2-system-baseline.json').read_text())
  self.assertEqual('16.0.4315.2',b['ProductVersion'])
  self.assertEqual(3687,len(b['permissions']));self.assertEqual(1568,len(b['objects']))
  self.assertEqual({'master','model','msdb','tempdb'},{r['DatabaseName'] for r in b['permissions']})
  self.assertEqual(3329,sum(r['Grantee'] in ('guest','public') for r in b['permissions']))
  self.assertEqual(1190,len(b['authority']))
  self.assertEqual(971,sum(r['Kind']=='SIGNATURE' for r in b['authority']))
  self.assertFalse(any(r['type'] in ('PK','UQ') for r in b['objects']))
 def test_system_checks_cover_actual_authority_and_unknown_edges_fail_closed(self):
  body=(ROOT/'build/sql-confinement/cimd-catalog-v2-system.sql').read_text()
  for term in ['#CimdCheckedSids','sys.database_principals','sys.database_role_members','sys.database_permissions','sys.schemas','sys.objects','sys.certificates','sys.asymmetric_keys','sys.assemblies','EXCEPT','ProductVersion','QUOTENAME']:
   self.assertIn(term,body)
  self.assertIn("schema_id=SCHEMA_ID(''guest'') AND is_ms_shipped=0",body)
if __name__=='__main__': unittest.main()
