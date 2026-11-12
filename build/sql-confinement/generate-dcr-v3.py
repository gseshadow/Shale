#!/usr/bin/env python3
"""Narrow C2 successor over immutable C1 artifacts. Never writes predecessor receipts."""
import hashlib,re,argparse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; BUILD=ROOT/'build/sql-confinement'; RES=ROOT/'shale-data/src/main/resources/ai'; SQL=ROOT/'docs/sql/ai-integration'
check=argparse.ArgumentParser();check.add_argument('--check',action='store_true');check=check.parse_args().check
q=lambda s:"N'"+s.replace("'","''")+"'"
h=lambda s:hashlib.sha256(s.encode('utf-16le')).hexdigest()
def emit(p,s):
 if check:
  if p.read_text()!=s:raise SystemExit('Stale C2 artifact '+str(p))
 else:p.write_text(s)
batch_index=0
def batch(s):
 global batch_index
 batch_index+=1
 return f'DECLARE @DcrBatch{batch_index} nvarchar(max)=CONVERT(nvarchar(max),0x{s.encode("utf-16le").hex()}); EXEC sys.sp_executesql @DcrBatch{batch_index};\n'
old=(BUILD/'external-v2-modules.sql').read_text()
old_publication=re.search(r'CREATE TRIGGER dbo.AiCimdImmutablePublicationV1.*?(?=\nGO)',(BUILD/'cimd-oauth-v1-review.sql').read_text(),re.S)[0].strip()
old_trigger=re.search(r'CREATE TRIGGER dbo.AiDcrImmutableMetadataV2.*?(?=\nGO\n)',old,re.S)[0].strip()
register=re.search(r'CREATE PROCEDURE dbo.AiDcrRegisterApplicationV2.*?(?=\nGO\n)',old,re.S)[0].strip()
register=register.replace('AiDcrRegisterApplicationV2','AiDcrRegisterApplicationV3').replace("IS_MEMBER(N'AiDcrRegistrarV2')","IS_MEMBER(N'AiDcrEndpointV3')")
# Count attempted identical validated metadata even if storage/audit later refuses.
marker=' BEGIN TRY BEGIN TRANSACTION;'
pre=""" DECLARE @fingerprint binary(32)=HASHBYTES('SHA2_256',CONVERT(varbinary(max),CONCAT(DATALENGTH(@Name),N':',@Name,DATALENGTH(@Redirects),N':',@Redirects,DATALENGTH(@Keys),N':',@Keys,N':',@Methods,N':',@Scopes)));
 DECLARE @permit bit,@retry int;
 EXEC dbo.AiDcrAdmissionV3 @Source=NULL,@Fingerprint=@fingerprint,@Allowed=@permit OUTPUT,@RetryAfter=@retry OUTPUT,@ReturnResult=0;
 IF @permit IS NULL OR @permit=0 THROW 59729,'Registration rate exceeded.',1;
"""
register=register.replace(" IF @lock<0 OR NOT EXISTS"," IF @lock<0 OR NOT EXISTS(SELECT 1 FROM dbo.AiDcrControlV3 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1) OR NOT EXISTS",1)
register=register.replace(marker,pre+marker,1).replace(' DECLARE @wire nvarchar(68)', ' EXEC dbo.AiDcrExpirePendingV3;\n DECLARE @wire nvarchar(68)',1)
# Stock localhost callbacks are exact registered redirects; no loopback port alias is added.
register=register.replace("value LIKE N'http://127.0.0.1:%/%'", "value LIKE N'http://localhost:%/%' OR value LIKE N'http://127.0.0.1:%/%'")
raw=(BUILD/'dcr-v3.sql').read_text();sections=raw.split('\nGO\n');schema=sections[0];modules={}
for part in sections[1:]:
 m=re.search(r'CREATE (?:PROCEDURE|TRIGGER) dbo\.(\w+)',part)
 if m:modules[m[1]]=part[m.start():].strip()
modules['AiDcrRegisterApplicationV3']=register
trigger=modules['AiDcrImmutableMetadataV2']
changed_triggers={'AiDcrImmutableMetadataV2':old_trigger,'AiCimdImmutablePublicationV1':old_publication}
newnames=sorted(set(modules)-set(changed_triggers)|{'AiDcrRateV3','AiDcrControlV3'})
permissions='CREATE ROLE AiDcrEndpointV3 AUTHORIZATION dbo;\n'+''.join('GRANT EXECUTE ON dbo.'+n+' TO AiDcrEndpointV3;\n' for n in ('AiDcrAdmissionV3','AiDcrRegisterApplicationV3','AiDcrStatusV3'))
source=h(raw+register+permissions+Path(__file__).read_text())
pins=''.join("IF NOT EXISTS(SELECT 1 FROM sys.sql_modules m JOIN sys.objects o ON o.object_id=m.object_id JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE o.object_id=OBJECT_ID("+q('dbo.'+n)+") AND COALESCE(o.principal_id,s.principal_id)=1 AND m.uses_ansi_nulls=1 AND m.uses_quoted_identifier=1 AND "+('m.execute_as_principal_id=-2' if 'WITH EXECUTE AS OWNER' in b else 'm.execute_as_principal_id IS NULL')+" AND HASHBYTES('SHA2_256',m.definition)=0x"+h(b)+") OR EXISTS(SELECT 1 FROM sys.crypt_properties WHERE major_id=OBJECT_ID("+q('dbo.'+n)+")) THROW 59700,'C2 module drift: "+n+".',1;\n" for n,b in modules.items())
# Reuse the C1 graph projection for the small additive catalog, preserving its complete schema/grant fields.
c1=(RES/'registry-external-v2-catalog.sql').read_text()
graph=c1[c1.index('DECLARE @C1Graph'):c1.index("IF @C1Graph IS NULL OR HASHBYTES")].replace('@C1Graph','@C2Graph').replace('@C1Objects','@C2Objects')
# New admission tables intentionally have no foreign keys; an empty projection is not missing catalog material.
graph='\n'.join(line.replace('@C2Graph+(SELECT','@C2Graph+COALESCE((SELECT').replace(".value('.','nvarchar(max)');",".value('.','nvarchar(max)'),N'');") if 'FROM sys.foreign_key_columns' in line else line for line in graph.splitlines())+'\n'
graph=re.sub(r'p.grantee_principal_id IN\(DATABASE_PRINCIPAL_ID\(.*?\)\) ORDER BY',"p.grantee_principal_id=DATABASE_PRINCIPAL_ID(N'AiDcrEndpointV3') ORDER BY",graph)
objects='DECLARE @C2Objects table(Name sysname PRIMARY KEY); INSERT @C2Objects VALUES '+','.join('('+q(n)+')' for n in newnames)+';\n'
verify="IF NOT EXISTS(SELECT 1 FROM sys.extended_properties WHERE major_id=OBJECT_ID(N'dbo.AiDcrCompatibilityV3') AND name=N'DcrEndpointV3.Source' AND CONVERT(binary(32),value)=0x"+source+") THROW 59700,'Explicit C2 successor required.',1;\n"+pins+objects+graph
verify+="""DECLARE @C2Seal binary(32); SELECT @C2Seal=CONVERT(binary(32),value) FROM sys.extended_properties WHERE major_id=OBJECT_ID(N'dbo.AiDcrCompatibilityV3') AND name=N'DcrEndpointV3.Catalog';
IF @C2Seal IS NULL OR @C2Graph IS NULL OR HASHBYTES('SHA2_256',@C2Graph)<>@C2Seal THROW 59700,'C2 catalog drift; never refresh receipts.',1;
IF NOT EXISTS(SELECT 1 FROM sys.triggers WHERE object_id=OBJECT_ID(N'dbo.AiDcrImmutableMetadataV2') AND is_disabled=0 AND is_instead_of_trigger=0) THROW 59700,'C2 retention trigger disabled.',1;
IF EXISTS(SELECT 1 FROM sys.triggers WHERE OBJECT_NAME(parent_id) IN(N'AiDcrControlV3',N'AiDcrRateV3'))
 OR EXISTS(SELECT 1 FROM sys.security_predicates WHERE OBJECT_NAME(target_object_id) IN(N'AiDcrControlV3',N'AiDcrRateV3')) THROW 59700,'Unexpected C2 table authority.',1;
DECLARE @C2Members table(Id int PRIMARY KEY); INSERT @C2Members SELECT member_principal_id FROM sys.database_role_members WHERE role_principal_id=DATABASE_PRINCIPAL_ID(N'AiDcrEndpointV3');
IF NOT EXISTS(SELECT 1 FROM sys.database_principals WHERE name=N'AiDcrEndpointV3' AND type='R' AND owning_principal_id=1)
 OR EXISTS(SELECT 1 FROM sys.database_role_members WHERE member_principal_id=DATABASE_PRINCIPAL_ID(N'AiDcrEndpointV3'))
 OR EXISTS(SELECT 1 FROM sys.database_role_members WHERE member_principal_id IN(SELECT Id FROM @C2Members) AND role_principal_id<>DATABASE_PRINCIPAL_ID(N'AiDcrEndpointV3'))
 OR EXISTS(SELECT 1 FROM sys.database_principals WHERE principal_id IN(SELECT Id FROM @C2Members) AND (type<>'S' OR principal_id<=4 OR owning_principal_id IS NOT NULL))
 OR EXISTS(SELECT 1 FROM sys.database_permissions WHERE grantee_principal_id IN(SELECT Id FROM @C2Members) AND NOT(class=0 AND permission_name='CONNECT' AND state='G'))
 OR EXISTS(SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE COALESCE(o.principal_id,s.principal_id) IN(SELECT Id FROM @C2Members))
 THROW 59700,'C2 endpoint identity drift.',1;
"""
# Project exactly the reviewed trigger sources only after pinning their replacements. C1 source hashes/receipt values stay fixed.
# Reuse C1's exact platform/identity closure for the new capability, rather than designing another verifier.
identity=c1[c1.index(' DECLARE @C1Members'):c1.index('DECLARE @C1Grants')].replace('@C1','@C2Identity')
identity=re.sub(r"IN\((?:DATABASE_PRINCIPAL_ID\(N'+[A-Za-z0-9_]+'+\),?)+\)",lambda m: "IN(DATABASE_PRINCIPAL_ID(N"+("''AiDcrEndpointV3''" if "N''" in m[0] else "'AiDcrEndpointV3'")+"))",identity)
identity=re.sub(r'INSERT @C2IdentityTokens VALUES[^;]+;',"INSERT @C2IdentityTokens VALUES(DATABASE_PRINCIPAL_ID(N'AiDcrEndpointV3'));",identity)
verify+=identity

def successor(text):
 for name,old_body in changed_triggers.items():text=text.replace('0x'+h(old_body),'0x'+h(modules[name]))
 # C1's locator CASE contains multiline SQL literals. Extend its final branch as a whole, not line by line.
 text=text.replace('ELSE m.definition END',"WHEN o.name=N'AiCimdImmutablePublicationV1' THEN "+q(old_publication)+' ELSE m.definition END')
 lines=[]
 for line in text.splitlines():
  if line.startswith('SET @C1Graph=') and 'FROM sys.objects o' in line:
   line=line.replace('m.definition',"CASE WHEN o.name=N'AiDcrImmutableMetadataV2' THEN "+q(old_trigger)+' ELSE m.definition END')
  if line.startswith('SET @C4bCatalog=') and 'FROM sys.objects o' in line:
   line=line.replace('m.definition',"CASE WHEN o.name=N'AiCimdImmutablePublicationV1' THEN "+q(old_publication)+' ELSE m.definition END')
  if line.startswith('SET @C4bCatalog=') and 'FROM sys.triggers t' in line:
   line=line.replace('m.definition',"CASE WHEN t.name=N'AiCimdImmutablePublicationV1' THEN "+q(old_publication)+' ELSE m.definition END')
  lines.append(line)
 return verify+'\n'+'\n'.join(lines)+'\n'
reg=successor(c1);auth=successor((RES/'external-authorization-v2-catalog.sql').read_text())
emit(RES/'registry-dcr-v3-catalog.sql',reg);emit(RES/'authorization-dcr-v3-catalog.sql',auth)
install="""/* C2 OFF development candidate. C1/historical receipts and immutable bindings are read-only. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
IF USER_NAME()<>N'dbo' OR @@TRANCOUNT<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL THROW 59700,'Fresh neutral owner required.',1;
IF EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Enabled=1) OR EXISTS(SELECT 1 FROM dbo.AiExternalControlV2 WHERE Enabled=1) OR EXISTS(SELECT 1 FROM dbo.AiCimdProofControlV1 WHERE Enabled=1) OR EXISTS(SELECT 1 FROM dbo.AiCimdOAuthControlV1 WHERE Enabled=1) THROW 59700,'Drain and disable all controls first.',1;
BEGIN TRY BEGIN TRANSACTION;
DECLARE @lock int; EXEC @lock=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
IF @lock<0 THROW 59700,'Schema lock unavailable.',1;
"""+batch(c1)+batch((RES/'external-authorization-v2-catalog.sql').read_text())+schema+'\n'
for n,b in modules.items():install+=batch(b.replace('CREATE TRIGGER','ALTER TRIGGER',1) if n in changed_triggers else b)
install+=permissions+"DECLARE @src binary(32)=0x"+source+"; EXEC sys.sp_addextendedproperty @name=N'DcrEndpointV3.Source',@value=@src,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'PROCEDURE',@level1name=N'AiDcrCompatibilityV3';\n"
install+=batch(pins+objects+graph+"IF @C2Graph IS NULL THROW 59700,'Missing C2 catalog.',1; DECLARE @seal binary(32)=HASHBYTES('SHA2_256',@C2Graph); EXEC sys.sp_addextendedproperty @name=N'DcrEndpointV3.Catalog',@value=@seal,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'PROCEDURE',@level1name=N'AiDcrCompatibilityV3';")
install+=batch(reg)+batch(auth)+"COMMIT TRANSACTION; END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;\n"
emit(SQL/'universal-pr-c2-dcr-v3.sql',install)
print('C2 source',source)
