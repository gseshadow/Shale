"""Reproduce the candidate and ensure it cannot rewrite historical authority or enter the legacy namespace."""
import hashlib,re,subprocess,sys,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; BUILD=ROOT/'build/sql-confinement'; SQL=ROOT/'docs/sql/ai-integration'
class CimdOAuthGenerationTest(unittest.TestCase):
 def test_generation_preserves_all_predecessors(self):
  paths=[p for p in SQL.glob('*.sql') if not p.name.startswith('universal-pr4b-')]
  before={p:hashlib.sha256(p.read_bytes()).digest() for p in paths}
  subprocess.run([sys.executable,str(BUILD/'generate-cimd-oauth-v1.py'),'--check'],check=True)
  self.assertEqual(before,{p:hashlib.sha256(p.read_bytes()).digest() for p in paths})
 def test_transaction_and_credential_namespaces_are_separate(self):
  text=(BUILD/'cimd-oauth-v1-review.sql').read_text()
  self.assertNotRegex(text,r'\bdbo\.(AiAuthorizationTransaction|AiOAuthCredential|AiAssertionReplay)\b')
  self.assertNotRegex(text,r'\b(?:ALTER|DROP)\s+(?:PROCEDURE|TABLE|TRIGGER)\b')
  for name in ['AiCimdBeginOAuthV1','AiCimdValidateTransactionV1','AiCimdRedeemV1','AiCimdRefreshV1','AiCimdRevokeCredentialV1','AiCimdReserveAssertionV1']:
   self.assertIn('CREATE PROCEDURE dbo.'+name,text)
  self.assertIn('INSERT dbo.AiCimdFamilyV1(',text)
  self.assertIn("NULL,(SELECT ClientId FROM dbo.AiCimdAuthorizationV1",text)
  self.assertIn('INSERT dbo.EntityActionAuditLog',text)
  self.assertIn("'AI_CLIENT_KEY'",text)
  self.assertIn('p.RowVer=@ApprovalVersion',text)
  self.assertIn('m.FreshUntil>@now',text)
 def test_permissions_and_default_off_never_expand_old_roles(self):
  text=(SQL/'universal-pr4b-oauth.sql').read_text()
  grants=re.findall(r'GRANT EXECUTE ON dbo\.(\w+) TO (\w+);',text)
  self.assertEqual({'AiCimdMetadataPublisherV1','AiCimdOAuthBrowserV1','AiCimdOAuthIssuerV1','AiCimdOAuthResolverV1','AiCimdOAuthNeutralV1'},{r for _,r in grants})
  self.assertIn('INSERT dbo.AiCimdOAuthControlV1 VALUES(1,0)',text)
  for prohibited in ['CREATE LOGIN','CREATE USER','sp_updateextendedproperty','sp_addextendedproperty']:
   # Embedded unchanged verifiers contain no installation/receipt mutation either.
   self.assertNotIn(prohibited,text)
  self.assertIn('INSERT dbo.AiCimdOAuthReceiptV1',text)
  review=(BUILD/'cimd-oauth-v1-review.sql').read_text()
  self.assertEqual(3,review.count('WITH EXECUTE AS OWNER'))
if __name__=='__main__':unittest.main()
