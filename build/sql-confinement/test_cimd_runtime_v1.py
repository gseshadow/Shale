import hashlib
from pathlib import Path
import re, subprocess, sys, unittest
ROOT=Path(__file__).resolve().parents[2]
class RuntimeCatalogContract(unittest.TestCase):
 def test_generated_outputs_are_current(self):
  subprocess.run([sys.executable,str(ROOT/'build/sql-confinement/generate-cimd-runtime-v1.py'),'--check'],check=True)
 def test_gate_is_fixed_read_only_and_source_pinned(self):
  body=(ROOT/'shale-data/src/main/resources/ai/cimd-runtime-v1-catalog.sql').read_text()
  digest=(ROOT/'shale-data/src/main/resources/ai/cimd-runtime-v1-catalog.sha256').read_text().strip()
  self.assertEqual(digest,hashlib.sha256(body.encode('utf-16le')).hexdigest())
  self.assertTrue(body.startswith('CREATE PROCEDURE dbo.AiCimdAttestCatalogV1 WITH EXECUTE AS OWNER\nAS BEGIN'))
  predecessor=(ROOT/'docs/sql/ai-integration/universal-pr4b-oauth-verify.sql').read_text()
  self.assertIn("EXEC sys.sp_executesql N'"+predecessor.replace("'","''")+"';",body)
  for manifest in ['cimd-v1-modules.sha256','cimd-oauth-v1-modules.sha256']:
   for line in (ROOT/'build/sql-confinement'/manifest).read_text().splitlines():
    name,digest=line.split('='); self.assertIn("(N'"+name+"',0x"+digest+")",body)
  # Outside the fixed predecessor literal, no business DML or caller-controlled SQL exists.
  outer=body.replace("N'"+predecessor.replace("'","''")+"'", "N'<fixed predecessor>'")
  self.assertNotRegex(outer,r'(?i)\b(?:INSERT\s+(?:INTO\s+)?dbo\.|UPDATE\s+dbo\.|DELETE\s+(?:FROM\s+)?dbo\.|MERGE\s+dbo\.)')
 def test_installer_is_additive_off_and_transactional(self):
  sql=(ROOT/'docs/sql/ai-integration/universal-pr4c-runtime.sql').read_text()
  self.assertIn('never repair or reseal',sql); self.assertIn('ROLLBACK TRANSACTION',sql)
  self.assertIn('AiCimdOAuthControlV1 WHERE Id=1 AND Enabled=0',sql)
  self.assertIn('AiCimdProofControlV1 WHERE Id=1 AND Enabled=0',sql)
  self.assertNotRegex(sql,r'(?i)CREATE LOGIN|PASSWORD=|ALTER ROLE .*ADD MEMBER')
  self.assertNotIn('GRANT SELECT',sql)
 def test_complete_graph_has_terminal_and_indirect_edges(self):
  graph=(ROOT/'build/sql-confinement/cimd-runtime-v1-graph.sql').read_text()
  for term in ['sys.triggers','sys.sql_expression_dependencies','sys.security_predicates','sys.foreign_keys','sys.crypt_properties','sys.external_tables','sys.synonyms','is_caller_dependent','is_ambiguous','is_db_chaining_on','is_trustworthy_on']:
   self.assertIn(term,graph)
if __name__=='__main__': unittest.main()
