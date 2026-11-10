"""Reproducible additive CIMD SQL and namespace/authority contracts."""
import hashlib,re,subprocess,sys,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; SQL=ROOT/'docs/sql/ai-integration'
class CimdProofGenerationTest(unittest.TestCase):
 def test_public_generation_is_current_without_writing_historical_inputs(self):
  paths=[SQL/name for name in ['phase3b1.sql','phase3b2b.sql','phase3b2c1.sql','phase3b2c2.sql','phase3b7-confinement.sql','phase3b8-confinement.sql','universal-pr3.sql','universal-pr3-confinement-verify.sql']]
  before={p:hashlib.sha256(p.read_bytes()).digest() for p in paths}
  subprocess.run([sys.executable,str(ROOT/'build/sql-confinement/generate-cimd-v1.py'),'--check'],check=True)
  self.assertEqual(before,{p:hashlib.sha256(p.read_bytes()).digest() for p in paths})
 def modules(self):
  text=(SQL/'universal-pr4a-proof.sql').read_text()
  result={}
  for encoded in re.findall(r'DECLARE @C4aModule\d+ nvarchar\(max\)=CONVERT\(nvarchar\(max\),0x([0-9a-f]+)\)',text):
   body=bytes.fromhex(encoded).decode('utf-16le');name=re.match(r'CREATE (?:PROCEDURE|TRIGGER) dbo\.(\w+)',body).group(1);result[name]=body
  self.assertEqual(16,len(result));return result
 def test_all_proof_roots_use_new_namespace_and_live_loader(self):
  modules=self.modules()
  for name,body in modules.items():
   if name.startswith('AiCimdData'):
    self.assertNotRegex(body,r'\bdbo\.(AiOAuthCredential|AiAuthorizationTransaction|AiCaseDataInvocation)\b',name)
  load=modules['AiCimdDataLoadAuthorityV1']
  for predicate in ['p.RowVer=f.ApprovalVersion','e.Revision=f.ApplicationRevision','e.MetadataDigest=f.MetadataDigest','e.Disabled=0','DATALENGTH(e.ExternalClientId)=DATALENGTH(f.ExternalClientId)','s.TransactionId IS NULL','@approvalDeadline<@deadline']:
   self.assertIn(predicate,load)
  for name in ['AiCimdDataAuthenticateV1','AiCimdDataSearchCasesV1','AiCimdDataGetCaseOverviewV1','AiCimdDataAuthorizeCaseHandoffV1']:
   self.assertIn('EXEC dbo.AiCimdDataLoadAuthorityV1',modules[name],name)
  self.assertIn('WITH EXECUTE AS OWNER',modules['AiCimdDataLocateAccessOwnerV1'])
  self.assertEqual(1,sum('WITH EXECUTE AS OWNER' in b for b in modules.values()))
 def test_new_permissions_are_separate_and_no_receipt_refresh_or_issuer_exists(self):
  text=(SQL/'universal-pr4a-proof.sql').read_text()
  grants=re.findall(r'GRANT EXECUTE ON dbo\.(\w+) TO (\w+);',text)
  self.assertEqual(7,len(grants))
  self.assertEqual({'AiCimdCaseRuntimeV1','AiCimdLifecycleV1'},{role for _,role in grants})
  self.assertNotIn('sp_updateextendedproperty',text.lower())
  self.assertNotRegex(text,r'(?i)ALTER\s+(PROCEDURE|TRIGGER|TABLE)\s+dbo\.(AiCaseDataDeployment|AiOAuthCredential|AiDelegatedSession|AiAuthorizationTransaction)\b')
  self.assertIn('INSERT dbo.AiCimdProofControlV1(Id,Enabled) VALUES(1,0)',text)
  self.assertNotIn('CREATE LOGIN',text)
  self.assertNotIn('CREATE USER',text)
if __name__=='__main__':unittest.main()
