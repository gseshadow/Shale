#!/usr/bin/env python3
"""Generate an additive registry verification contract from public sources only.
Never regenerate historical SQL or consume/alter a private authoritative fixture.
"""
from pathlib import Path
import argparse, re, hashlib
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("--check",action="store_true",help="Verify generated public artifacts without writing files")
args=parser.parse_args()
def emit(path,text):
 if args.check:
  if not path.exists() or path.read_text()!=text:
   raise SystemExit("Generated artifact differs: "+str(path.relative_to(ROOT)))
 else: path.write_text(text)
ROOT=Path(__file__).resolve().parents[2]; SQL=ROOT/'docs/sql/ai-integration'; RES=ROOT/'shale-data/src/main/resources/ai'
HELPER='AiRegistryConfinementV1'; PROP='Shale.RegistryConfinementV1'
registry=(SQL/'universal-pr3.sql').read_text()
def quoted(s):return "N'"+s.replace("'","''")+"'"
def definitions(text):
 out={}
 for m in re.finditer(r"EXEC\(N'((?:[^']|'')*)'\);",text,re.S):
  body=m.group(1).replace("''", "'"); name=re.match(r'CREATE (?:PROCEDURE|TRIGGER) dbo\.(\w+)',body)
  if name:out[name.group(1)]=body
 return out
all_defs={}
for file in ['phase3b1.sql','phase3b2c2.sql','universal-pr3.sql']:all_defs.update(definitions((SQL/file).read_text()))
names=['AiLockV1','AiActorV1','AiEnabledV1','AiImmutable_AiIntegrationRegistration','AiCascade_Registration','AiImmutable_AiDelegationGrant','AiCascade_Grant','AiImmutable_AiDelegatedSession','AiLifecycle_Session','AiImmutable_AiOAuthCredential','AiLifecycle_Credential','AiImmutable_AiConsentIntent','AiPublishApplicationV1','AiRequestApplicationV1','AiDecideApplicationV1','AiImmutable_ExternalApplication','AiImmutable_ApplicationRevision','AiImmutable_ApplicationApproval']
values=[]; new_names=set(definitions(registry))
for name in names:
 lf=all_defs[name].replace('\r\n','\n'); crlf=lf.replace('\n','\r\n')
 values.append(f"(N'{name}',0x{hashlib.sha256(lf.encode('utf-16le')).hexdigest()},0x{hashlib.sha256(crlf.encode('utf-16le')).hexdigest()},{int(name in new_names)})")
modules='DECLARE @RegistryModules table(Name sysname PRIMARY KEY,HashLF binary(32),HashCRLF binary(32),IsNew bit);\nINSERT @RegistryModules VALUES\n'+',\n'.join(values)+';'
graph=(ROOT/'build/sql-confinement/registry-v1-graph.sql').read_text().replace('-- REGISTRY_EXPECTED_MODULES',modules)
versions={}
for v,prefix in [('V1','confinement'),('V2','confinement-v2')]:
 manifest=dict(line.split('=',1) for line in (RES/(prefix+'-modules.properties')).read_text().splitlines() if '=' in line)
 versions[v]=(manifest['Source'],(SQL/('phase3b7-confinement-verify.sql' if v=='V1' else 'phase3b8-confinement-verify.sql')).read_text())
dispatch="""IF (SELECT COUNT(*) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND name IN(N'Shale.AiCaseDataV1.Source',N'Shale.AiCaseDataV2.Source'))<>1
 THROW 59310,'Exactly one installed confinement version required.',1;
"""
for i,(v,(source,body)) in enumerate(versions.items()):
 dispatch+=('IF' if i==0 else 'ELSE IF')+f" EXISTS(SELECT 1 FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND name=N'Shale.AiCaseData{v}.Source' AND CONVERT(varbinary(32),value)=0x{source})\n EXEC("+quoted(body)+");\n"
dispatch+="ELSE THROW 59310,'Unknown confinement source; no fallback permitted.',1;\n"
legacy_hash="""IF (SELECT COUNT(*) FROM dbo.AiCaseDataDeployment)<>1 THROW 59310,'An existing immutable binding is required; never provision it here.',1;
DECLARE @RegistryLegacyState nvarchar(max)=(SELECT class,major_id,minor_id,name,CONVERT(varchar(max),CONVERT(varbinary(max),value),2) AS ReceiptValue
 FROM sys.extended_properties WHERE name LIKE N'Shale.%' AND name NOT LIKE N'Shale.RegistryConfinementV1.%' AND name NOT LIKE N'Shale.UniversalMcpPR3%'
 ORDER BY class,major_id,minor_id,name FOR JSON PATH);
SET @RegistryLegacyState=@RegistryLegacyState+(SELECT Id,Issuer,Resource,Contract,JavaTimeZone,SqlTimeZone FROM dbo.AiCaseDataDeployment ORDER BY Id FOR JSON PATH);
DECLARE @RegistryLegacyHash binary(32)=HASHBYTES('SHA2_256',@RegistryLegacyState);
"""
legacy_check=legacy_hash+"""IF @RegistryLegacyHash<>ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiRegistryConfinementV1') AND name=N'Shale.RegistryConfinementV1.Before'),0x)
 THROW 59310,'Historical receipt or immutable binding changed.',1;
"""
own=(SQL/'universal-pr3-verify.sql').read_text(); own=own[:own.index('SELECT 0 AS ApplicationCatalogDriftFindingCount;')]
extra_fingerprint="""
DECLARE @RegistryGraph nvarchar(max)=@catalog+CONVERT(nvarchar(64),@RegistryLegacyHash,2);
SET @RegistryGraph=@RegistryGraph+(SELECT CONCAT(OBJECT_NAME(t.parent_id),N':',t.name,N':',t.is_disabled,N':',t.is_instead_of_trigger,N':',e.type,N':',e.is_first,N':',e.is_last,N':',m.execute_as_principal_id,N':',m.uses_ansi_nulls,N':',m.uses_quoted_identifier,N':',USER_NAME(COALESCE(o.principal_id,s.principal_id)),N':',m.definition,N';') AS [text()]
 FROM sys.triggers t JOIN sys.objects o ON o.object_id=t.object_id JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=t.object_id LEFT JOIN sys.trigger_events e ON e.object_id=t.object_id
 WHERE OBJECT_SCHEMA_NAME(t.parent_id)=N'dbo' AND OBJECT_NAME(t.parent_id) IN(SELECT Name FROM @RegistryDmlTables) ORDER BY t.parent_id,t.name,e.type FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @RegistryGraph=@RegistryGraph+(SELECT CONCAT(OBJECT_NAME(p.target_object_id),N':',OBJECT_SCHEMA_NAME(p.object_id),N':',OBJECT_NAME(p.object_id),N':',s.principal_id,N':',p.predicate_definition,N':',p.predicate_type,N':',p.operation,N';') AS [text()]
 FROM sys.security_predicates p JOIN sys.security_policies s ON s.object_id=p.object_id WHERE OBJECT_SCHEMA_NAME(p.target_object_id)=N'dbo' AND OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @RegistryDmlTables) ORDER BY p.target_object_id,p.predicate_type,p.operation FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @RegistryGraph=@RegistryGraph+(SELECT CONCAT(OBJECT_NAME(d.referencing_id),N':',d.referenced_class,N':',d.referenced_schema_name,N':',d.referenced_entity_name,N':',d.referenced_minor_id,N':',d.is_caller_dependent,N':',d.is_ambiguous,N';') AS [text()]
 FROM sys.sql_expression_dependencies d WHERE d.referencing_id IN(SELECT OBJECT_ID(N'dbo.'+Name) FROM @RegistryModules)
 ORDER BY d.referencing_id,d.referenced_class,d.referenced_id,d.referenced_minor_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @RegistryGraph=@RegistryGraph+(SELECT CONCAT(OBJECT_NAME(c.object_id),N':',c.name,N':',c.column_id,N':',c.system_type_id,N':',c.user_type_id,N':',c.max_length,N':',c.precision,N':',c.scale,N':',c.is_nullable,N':',c.is_identity,N':',c.collation_name,N':',cc.definition,N';') AS [text()]
 FROM sys.columns c LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id
 WHERE OBJECT_SCHEMA_NAME(c.object_id)=N'dbo' AND OBJECT_NAME(c.object_id) IN(SELECT Name FROM @RegistryDmlTables) ORDER BY c.object_id,c.column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @RegistryGraph=@RegistryGraph+(SELECT CONCAT(OBJECT_NAME(o.parent_object_id),N':',o.name,N':',o.type,N':',ch.definition,N':',ch.is_disabled,N':',ch.is_not_trusted,N':',dc.definition,N':',fk.referenced_object_id,N':',fk.delete_referential_action,N':',fk.update_referential_action,N':',fk.is_disabled,N':',fk.is_not_trusted,N';') AS [text()]
 FROM sys.objects o LEFT JOIN sys.check_constraints ch ON ch.object_id=o.object_id LEFT JOIN sys.default_constraints dc ON dc.object_id=o.object_id LEFT JOIN sys.foreign_keys fk ON fk.object_id=o.object_id
 WHERE OBJECT_SCHEMA_NAME(o.parent_object_id)=N'dbo' AND OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @RegistryDmlTables) AND o.type IN('C','D','F') ORDER BY o.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @RegistryDigest=HASHBYTES('SHA2_256',@RegistryGraph);
IF @RegistrySealing=0 AND @RegistryDigest<>ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiRegistryConfinementV1') AND name=N'Shale.RegistryConfinementV1.Catalog'),0x)
 THROW 59310,'Registry confinement catalog drift; never refresh receipts.',1;
"""
core=legacy_check+"DECLARE @RegistryDigest binary(32);\n"+graph+"\nIF @RegistryBefore=0 BEGIN\n"+own+extra_fingerprint+"\nEND;\n"
helper="""CREATE PROCEDURE dbo.AiRegistryConfinementV1 @Phase varchar(max)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @Phase IS NULL OR @Phase NOT IN('BEFORE','SEAL','VERIFY') THROW 59310,'Unknown compatibility phase.',1;
 DECLARE @RegistryBefore bit=CASE WHEN @Phase='BEFORE' THEN 1 ELSE 0 END,@RegistrySealing bit=CASE WHEN @Phase='SEAL' THEN 1 ELSE 0 END;
 IF @Phase IN('BEFORE','SEAL') AND (USER_NAME()<>N'dbo' OR @@TRANCOUNT<>1 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 OR ISNULL(APPLOCK_MODE(N'public',N'AiSchemaV1',N'Transaction'),N'NoLock')<>N'Exclusive' OR NOT EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Id=1 AND Enabled=0))
 THROW 59310,'Unscoped locked dbo installation with capability OFF required.',1;
"""+dispatch+core+"""
 IF @Phase='SEAL' BEGIN
  IF EXISTS(SELECT 1 FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiRegistryConfinementV1') AND name=N'Shale.RegistryConfinementV1.Catalog')
  THROW 59310,'Compatibility catalog already sealed; never refresh it.',1;
  EXEC sys.sp_addextendedproperty @name=N'Shale.RegistryConfinementV1.Catalog',@value=@RegistryDigest,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'PROCEDURE',@level1name=N'AiRegistryConfinementV1';
 END;
END;"""
source=hashlib.sha256(helper.encode('utf-16le')).hexdigest()
pin=f"""IF NOT EXISTS(SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=o.object_id
 JOIN sys.extended_properties p ON p.class=1 AND p.major_id=o.object_id AND p.minor_id=0 AND p.name=N'{PROP}.Source'
 WHERE s.name=N'dbo' AND o.name=N'{HELPER}' AND o.type='P' AND COALESCE(o.principal_id,s.principal_id)=1 AND m.execute_as_principal_id IS NULL AND m.uses_ansi_nulls=1 AND m.uses_quoted_identifier=1
 AND HASHBYTES('SHA2_256',m.definition)=0x{source} AND CONVERT(varbinary(32),p.value)=0x{source}
 AND EXISTS(SELECT 1 FROM sys.extended_properties r WHERE r.class=1 AND r.major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND r.name=N'{PROP}.Required' AND CONVERT(varbinary(32),r.value)=0x{source}))
 THROW 59310,'Reviewed registry confinement prerequisite missing or changed.',1;
IF EXISTS(SELECT 1 FROM sys.database_permissions WHERE class=1 AND major_id=OBJECT_ID(N'dbo.{HELPER}') AND NOT(grantee_principal_id=DATABASE_PRINCIPAL_ID(N'public') AND minor_id=0 AND permission_name='EXECUTE' AND state='D' AND grantor_principal_id=1))
 OR NOT EXISTS(SELECT 1 FROM sys.database_permissions WHERE class=1 AND major_id=OBJECT_ID(N'dbo.{HELPER}') AND grantee_principal_id=DATABASE_PRINCIPAL_ID(N'public') AND permission_name='EXECUTE' AND state='D')
 OR EXISTS(SELECT 1 FROM sys.crypt_properties WHERE major_id=OBJECT_ID(N'dbo.{HELPER}'))
 THROW 59310,'Private compatibility module permission/signature drift.',1;
"""
presence="""IF NOT EXISTS(SELECT 1 FROM sys.objects WHERE schema_id=SCHEMA_ID(N'dbo') AND name IN(N'AiExternalApplication',N'AiExternalApplicationRevision',N'AiTenantApplicationApproval',N'AiPublishApplicationV1',N'AiRequestApplicationV1',N'AiDecideApplicationV1',N'AiRegistryConfinementV1'))
 AND NOT EXISTS(SELECT 1 FROM sys.database_principals WHERE name IN(N'AiApplicationPublisher',N'AiApplicationTenant'))
 AND NOT EXISTS(SELECT 1 FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND name=N'Shale.RegistryConfinementV1.Required') RETURN;
"""
extension="/* Generated read-only registry confinement V1 extension; existing Case verification must run first. */\nSET NOCOUNT ON;\n"+presence+pin+"DECLARE @RegistryBefore bit=0,@RegistrySealing bit=0;\n"+core+"SELECT 0 AS RegistryConfinementFindingCount;\n"
emit(RES/'registry-confinement-v1-catalog.sql',extension)
emit(SQL/'universal-pr3-confinement-verify.sql','/* Generated read-only combined verification. No fallback and no receipt writes. */\nSET NOCOUNT ON;\n'+pin+dispatch+'DECLARE @RegistryBefore bit=0,@RegistrySealing bit=0;\n'+core+'SELECT 0 AS RegistryConfinementFindingCount;\n')
prepare="""/* Generated registry/confinement V1 PREPARATION CANDIDATE. Separately approved manual use only.
   Historical SQL/receipts/binding are read, never changed. No login, activation or application EXECUTE. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
IF USER_NAME()<>N'dbo' OR @@TRANCOUNT<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 59310,'Fresh unscoped dbo session required.',1;
BEGIN TRY BEGIN TRANSACTION;
DECLARE @RegistryLock int;
EXEC @RegistryLock=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
IF @RegistryLock<0 THROW 59310,'Compatibility schema lock unavailable.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Id=1 AND Enabled=0) THROW 59310,'Keep product capability OFF.',1;
"""+dispatch+"\nIF OBJECT_ID(N'dbo.AiRegistryConfinementV1') IS NOT NULL BEGIN\n"+pin+"""
 IF OBJECT_ID(N'dbo.AiExternalApplication') IS NULL EXEC dbo.AiRegistryConfinementV1 @Phase='BEFORE';
 ELSE EXEC dbo.AiRegistryConfinementV1 @Phase='VERIFY';
 COMMIT TRANSACTION; RETURN;
END;
"""+legacy_hash+"\nDECLARE @RegistryDefinition nvarchar(max)=CONVERT(nvarchar(max),0x"+helper.encode('utf-16le').hex()+");\nEXEC(@RegistryDefinition);\nDENY EXECUTE ON dbo.AiRegistryConfinementV1 TO public;\nDECLARE @RegistrySource binary(32)=0x"+source+";\nEXEC sys.sp_addextendedproperty @name=N'Shale.RegistryConfinementV1.Source',@value=@RegistrySource,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'PROCEDURE',@level1name=N'AiRegistryConfinementV1';\nEXEC sys.sp_addextendedproperty @name=N'Shale.RegistryConfinementV1.Before',@value=@RegistryLegacyHash,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'PROCEDURE',@level1name=N'AiRegistryConfinementV1';\nEXEC sys.sp_addextendedproperty @name=N'Shale.RegistryConfinementV1.Required',@value=@RegistrySource,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'AiCaseDataInvocation';\nEXEC dbo.AiRegistryConfinementV1 @Phase='BEFORE';\nCOMMIT TRANSACTION;\nEND TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;\n"
emit(SQL/'universal-pr3-confinement-prepare.sql',prepare)
# Update only explicit new guard regions. No historical file or registry procedure body changes.
guard="""/* Registry confinement guard V1: an existing deployment still requires exact reviewed prerequisites. */
IF OBJECT_ID(N'dbo.AiCaseDataDeployment') IS NOT NULL BEGIN
"""+pin.replace("THROW 59310,'Reviewed registry confinement prerequisite missing or changed.',1;", "THROW 59300,'Confinement successor review required before installing PR 3 alongside Case confinement.',1;")+"END;\n/* End registry confinement guard V1. */"
original="IF OBJECT_ID(N'dbo.AiCaseDataDeployment') IS NOT NULL\n THROW 59300,'Confinement successor review required before installing PR 3 alongside Case confinement.',1;"
if original in registry:registry=registry.replace(original,guard,1)
else:registry=re.sub(r'/\* Registry confinement guard V1:.*?/\* End registry confinement guard V1\. \*/',lambda m:guard,registry,count=1,flags=re.S)
locked="""/* Registry confinement locked precheck V1. */
IF OBJECT_ID(N'dbo.AiCaseDataDeployment') IS NOT NULL BEGIN
"""+pin+"EXEC dbo.AiRegistryConfinementV1 @Phase='BEFORE';\nEND;\n/* End registry confinement locked precheck V1. */"
if '/* Registry confinement locked precheck V1. */' in registry:registry=re.sub(r'/\* Registry confinement locked precheck V1\. \*/.*?/\* End registry confinement locked precheck V1\. \*/',lambda m:locked,registry,count=1,flags=re.S)
else:registry=registry.replace("IF @rc<0 THROW 59300,'Schema lock unavailable.',1;", "IF @rc<0 THROW 59300,'Schema lock unavailable.',1;\n"+locked,1)
seal="IF OBJECT_ID(N'dbo.AiCaseDataDeployment') IS NOT NULL EXEC dbo.AiRegistryConfinementV1 @Phase='SEAL';"
if seal not in registry:registry=registry.replace('COMMIT TRANSACTION;\nEND TRY BEGIN CATCH',seal+'\nCOMMIT TRANSACTION;\nEND TRY BEGIN CATCH',1)
emit(SQL/'universal-pr3.sql',registry)
print('Generated registry supplement V1 source',source)
