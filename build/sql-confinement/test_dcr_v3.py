"""C2 generation contract and unchanged predecessor artifacts; real SQL is separate."""
import hashlib,subprocess,sys,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];BUILD=ROOT/'build/sql-confinement';SQL=ROOT/'docs/sql/ai-integration';RES=ROOT/'shale-data/src/main/resources/ai'
class DcrV3GenerationTest(unittest.TestCase):
 def test_generator_is_current_and_preserves_c1(self):
  old=[SQL/'universal-pr-c1-external-v2.sql',RES/'registry-external-v2-catalog.sql',RES/'external-authorization-v2-catalog.sql',BUILD/'external-v2-modules.sql']
  before={p:hashlib.sha256(p.read_bytes()).digest() for p in old}
  subprocess.run([sys.executable,str(BUILD/'generate-dcr-v3.py'),'--check'],check=True)
  self.assertEqual(before,{p:hashlib.sha256(p.read_bytes()).digest() for p in old})
 def test_only_retention_trigger_is_altered_and_no_old_receipt_writes(self):
  text=(SQL/'universal-pr-c2-dcr-v3.sql').read_text()
  import re
  text+='\n'+'\n'.join(bytes.fromhex(x).decode('utf-16le') for x in re.findall(r'CONVERT\(nvarchar\(max\),0x([0-9a-f]+)\)',text))
  self.assertEqual(['AiDcrImmutableMetadataV2','AiCimdImmutablePublicationV1'],re.findall(r'ALTER (?:TRIGGER|PROCEDURE) dbo\.(\w+)',text))
  self.assertNotRegex(text,r'(?:UPDATE|INSERT|DELETE) dbo\.(?:AiCaseDataDeployment|AiCimdOAuthReceiptV1|AiCimdProofReceiptV1)\b')
  self.assertNotIn('sp_updateextendedproperty',text)
  self.assertNotIn('CREATE LOGIN',text)
 def test_rate_storage_cleanup_and_source_projection_are_bounded(self):
  text=(BUILD/'dcr-v3.sql').read_text()
  self.assertIn('(0,CONVERT(binary(32),0x00),100),(1,@Source,10)',text)
  self.assertIn('>=1024',text);self.assertIn('SELECT TOP(100)',text)
  self.assertIn('p.IntegrationId IS NOT NULL',text);self.assertIn('WITH(UPDLOCK,HOLDLOCK)',text)
  for name in ('registry-dcr-v3-catalog.sql','authorization-dcr-v3-catalog.sql'):
   verifier=(RES/name).read_text()
   self.assertIn('Explicit C2 successor required',verifier)
   self.assertIn('C2 catalog drift; never refresh receipts',verifier)
   self.assertLess(verifier.index('C2 module drift: AiDcrImmutableMetadataV2'),verifier.index("CASE WHEN o.name=N'AiDcrImmutableMetadataV2'"))
if __name__=='__main__':unittest.main()
