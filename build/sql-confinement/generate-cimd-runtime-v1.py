#!/usr/bin/env python3
"""Additive read-only catalog gate; never regenerate a predecessor or its receipt."""
from pathlib import Path
import argparse, hashlib, re
ROOT=Path(__file__).resolve().parents[2]
SQL=ROOT/'docs/sql/ai-integration'; RES=ROOT/'shale-data/src/main/resources/ai'; BUILD=Path(__file__).resolve().parent
args=argparse.ArgumentParser(description=__doc__); args.add_argument('--check',action='store_true'); args=args.parse_args()
def emit(path,text):
 if args.check:
  if not path.exists() or path.read_text()!=text: raise SystemExit('Stale artifact: '+str(path.relative_to(ROOT)))
 else: path.write_text(text)
def literal(s): return "N'"+s.replace("'","''")+"'"
# All executable edges must be source-reviewed. Existing verifiers additionally pin the
# installed generation, catalog, execution context and settings. Do not infer trust from a receipt alone.
allowed=set()
for path in SQL.glob('*.sql'):
 if path.name.startswith('universal-pr4c'): continue
 for match in re.finditer(r"EXEC\(N'((?:[^']|'')*)'\);|EXEC sys\.sp_executesql N'((?:[^']|'')*)';",path.read_text(),re.S):
  body=next(x for x in match.groups() if x is not None).replace("''", "'")
  module=re.match(r'(CREATE|ALTER) (?:PROCEDURE|TRIGGER|FUNCTION) (?:dbo|sec)\.(\w+)',body)
  if module:
   body=re.sub(r'^ALTER ', 'CREATE ',body)
   for variant in [body,body.replace('\r\n','\n').replace('\n','\r\n')]:
    allowed.add((module[2],hashlib.sha256(variant.encode('utf-16le')).hexdigest()))
for path in RES.glob('*modules.properties'):
 for line in path.read_text().splitlines():
  if '=' in line:
   name,digest=line.split('=',1)
   if name!='Source': allowed.add((name,digest))
# New proof/OAuth manifests are tab-delimited name/hash records.
for path in [BUILD/'cimd-v1-modules.sha256',BUILD/'cimd-oauth-v1-modules.sha256']:
 for line in path.read_text().splitlines():
  fields=line.replace('=', ' ').split()
  if len(fields)==2:
   digest,name=fields if len(fields[0])==64 else fields[::-1]
   allowed.add((name,digest))
# Canonical authoritative Case policy/trigger representations, already accepted by V2.
# Hashes are embedded in the unchanged V2 verifier with their exact module names.
for name,digest in re.findall(r"\(N'(?:dbo|sec)',N'(\w+)',0x([0-9a-f]{64}),",(RES/'confinement-v2-catalog.sql').read_text()): allowed.add((name,digest))
values=','.join('('+literal(n)+',0x'+h+')' for n,h in sorted(allowed))
graph=(BUILD/'cimd-runtime-v1-graph.sql').read_text().replace('-- EXPECTED_MODULES', 'INSERT @RuntimeModules VALUES '+values+';')
pre=(SQL/'universal-pr4b-oauth-verify.sql').read_text()
body="""CREATE PROCEDURE dbo.AiCimdAttestCatalogV1 WITH EXECUTE AS OWNER
AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
  THROW 59600,'Fresh neutral catalog lease required.',1;
 IF (SELECT COUNT(*) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID('dbo.AiCaseDataInvocation') AND name='Shale.RegistryConfinementV1.Required')<>1
  THROW 59600,'Registry compatibility receipt required.',1;
"""
# Fixed Unicode constant, no parameter, concatenation or caller-selected SQL. Executes all
# predecessors byte-for-byte, including V1/V2 dispatch, #1948 and PR4A/4B source/catalog/before checks.
body+=' EXEC sys.sp_executesql '+literal(pre)+';\n'+graph
body+="""
 IF (SELECT COUNT(*) FROM dbo.AiCaseDataDeployment WHERE Id=1)<>1
 OR (SELECT COUNT(*) FROM dbo.AiCimdProofControlV1 WHERE Id=1)<>1
 OR (SELECT COUNT(*) FROM dbo.AiCimdOAuthControlV1 WHERE Id=1)<>1
  THROW 59600,'Incomplete runtime binding or controls.',1;
 SELECT Issuer,Resource,Contract,JavaTimeZone,SqlTimeZone FROM dbo.AiCaseDataDeployment WHERE Id=1;
END;"""
sha=hashlib.sha256(body.encode('utf-16le')).hexdigest()
install="""/* PR 4C candidate. No identity, secret, historical receipt or activation is installed. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
IF USER_NAME()<>'dbo' OR @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 59600,'Fresh unscoped dbo lease required.',1;
BEGIN TRY BEGIN TRANSACTION;
DECLARE @lock int; EXEC @lock=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
IF @lock<0 OR NOT EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Id=1 AND Enabled=0)
 OR NOT EXISTS(SELECT 1 FROM dbo.AiCimdOAuthControlV1 WHERE Id=1 AND Enabled=0)
 OR NOT EXISTS(SELECT 1 FROM dbo.AiCimdProofControlV1 WHERE Id=1 AND Enabled=0)
 THROW 59600,'OFF runtime foundation required.',1;
IF OBJECT_ID('dbo.AiCimdAttestCatalogV1') IS NOT NULL OR DATABASE_PRINCIPAL_ID('AiCimdCatalogV1') IS NOT NULL
 THROW 59600,'Runtime catalog already exists; never repair or reseal.',1;
"""
install+='EXEC sys.sp_executesql '+literal(body)+';\n'
install+='CREATE ROLE AiCimdCatalogV1 AUTHORIZATION dbo;\nGRANT VIEW DEFINITION TO AiCimdCatalogV1;\nGRANT EXECUTE ON dbo.AiCimdAttestCatalogV1 TO AiCimdCatalogV1;\n'
install+='EXEC dbo.AiCimdAttestCatalogV1;\nCOMMIT TRANSACTION;\nEND TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;\n'
# The callable gate requires a fresh lease; installation validates its exact fixed inner batch
# in the installation transaction instead of relaxing the gate's precondition.
install=install.replace('EXEC dbo.AiCimdAttestCatalogV1;\nCOMMIT', 'EXEC sys.sp_executesql '+literal(pre+'\n'+graph)+';\nCOMMIT')
emit(SQL/'universal-pr4c-runtime.sql',install)
emit(RES/'cimd-runtime-v1-catalog.sql',body)
emit(RES/'cimd-runtime-v1-catalog.sha256',sha+'\n')
