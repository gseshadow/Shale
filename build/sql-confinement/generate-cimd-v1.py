#!/usr/bin/env python3
"""PR 4A additive proof generation from frozen public SQL. Never writes historical inputs."""
from pathlib import Path
import argparse,hashlib,re
ROOT=Path(__file__).resolve().parents[2]; BUILD=Path(__file__).resolve().parent; SQL=ROOT/'docs/sql/ai-integration'
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
def emit(path,text):
 if args.check:
  if not path.exists() or path.read_text()!=text:raise SystemExit('Stale generated artifact: '+str(path.relative_to(ROOT)))
 else:path.write_text(text)
def defs(text):
 result={}
 for m in re.finditer(r"EXEC\(N'((?:[^']|'')*)'\);",text,re.S):
  body=m.group(1).replace("''","'");match=re.match(r'CREATE (?:PROCEDURE|TRIGGER) dbo\.(\w+)',body)
  if match:result[match.group(1)]=body
 return result
def hexsql(text):return 'CONVERT(nvarchar(max),0x'+text.encode('utf-16le').hex()+')'
def execute(text):return 'EXEC sys.sp_executesql '+"N'"+text.replace("'","''")+"';\n"
old=(SQL/'phase3b7-confinement.sql').read_text();foundation=(SQL/'phase3b1.sql').read_text()
original=defs(old);base=defs(foundation);base.update(defs((SQL/'phase3b2c2.sql').read_text()))
modules={}
rename={name:name.replace('AiData','AiCimdData',1) for name in original if name.startswith('AiData') and name not in ['AiDataNormalizeV1','AiDataImmutableDeployment']}
# Only Case proof modules and their invocation trigger are versioned; normalization remains pure/shared.
rename.update(AiOAuthCredential='AiCimdCredentialV1',AiCaseDataInvocation='AiCimdInvocationV1',AiAuthorizationTransaction='AiCimdFamilyV1')
def transform(text):
 for before,after in sorted(rename.items(),key=lambda p:-len(p[0])):text=re.sub(r'\b'+before+r'\b',after,text)
 return text
for name,body in original.items():
 if name not in rename:continue
 text=transform(body)
 if name in ['AiDataLocateAccessOwnerV1','AiDataLoadAuthorityV1']:
  text=text.replace('a.TransactionId=s.TransactionId','a.SessionId=s.SessionId AND s.TransactionId IS NULL')
 if name=='AiDataLoadAuthorityV1':
  text=text.replace("i.Status='ACTIVE'","i.Status='SUSPENDED' AND i.Revision=1")
  text=text.replace('SELECT @web=WebSessionId FROM dbo.AiCimdFamilyV1 WITH(HOLDLOCK) WHERE ShaleClientId=@t AND TransactionId=@transaction;', 'SELECT @web=WebSessionId FROM dbo.AiCimdFamilyV1 WITH(HOLDLOCK) WHERE ShaleClientId=@t AND SessionId=@SessionId;')
  marker=' -- Acquire every row explicitly in fixed order, BEFORE the validation join and UTC sample.'
  assert marker in text;text=text.replace(marker,(BUILD/'cimd-v1-authority.sql').read_text()+'\n'+marker)
  marker=' DECLARE @now datetime2(7)=SYSUTCDATETIME();'
  guard="""
 -- Same durable exact application/approval tuple is checked at admission, read and final handoff.
 DECLARE @approvalDeadline datetime2(7),@approvalMask tinyint;
 SELECT @approvalDeadline=p.ExpiresAt,@approvalMask=p.ScopeMask
 FROM dbo.AiCimdFamilyV1 f
 JOIN dbo.AiExternalApplication e ON e.ApplicationId=f.ApplicationId
 JOIN dbo.AiTenantApplicationApproval p ON p.ShaleClientId=f.ShaleClientId AND p.ApprovalId=f.ApprovalId
 WHERE f.ShaleClientId=@t AND f.SessionId=@SessionId AND f.UserId=@u AND f.IntegrationId=@IntegrationId AND f.GrantId=@GrantId
 AND p.ApplicationId=f.ApplicationId AND p.ApplicationRevision=f.ApplicationRevision AND p.IntegrationId=f.IntegrationId
 AND p.RowVer=f.ApprovalVersion AND p.State='APPROVED' AND p.ClosedAt IS NULL
 AND e.Revision=f.ApplicationRevision AND e.MetadataDigest=f.MetadataDigest AND e.Disabled=0 AND e.Source='CIMD'
 AND e.ExternalClientId=f.ExternalClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.ExternalClientId)=DATALENGTH(f.ExternalClientId)
 AND e.Issuer=f.Issuer AND DATALENGTH(e.Issuer)=DATALENGTH(f.Issuer) AND e.Resource=f.Resource AND DATALENGTH(e.Resource)=DATALENGTH(f.Resource)
 AND ((f.AuthMethod='none' AND f.CredentialId IS NULL AND (e.MethodMask & 1)=1) OR
      (f.AuthMethod='private_key_jwt' AND f.CredentialId=@KeyId AND (e.MethodMask & 2)=2))
 AND EXISTS(SELECT 1 FROM dbo.AiCimdProofControlV1 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1);
 IF @approvalDeadline IS NULL OR @approvalMask IS NULL OR (@mask & @approvalMask)<>@mask
 THROW 59101,'Integration authentication failed.',1;
 IF @approvalDeadline<@deadline SET @deadline=@approvalDeadline;
"""
  assert text.count(marker)==1;text=text.replace(marker,guard+marker)
 modules[rename[name]]=text
# Separate immutable family receipt: future issuer must prove PKCE/consent before INSERT.
modules['AiCimdImmutableFamilyV1']="""CREATE TRIGGER dbo.AiCimdImmutableFamilyV1 ON dbo.AiCimdFamilyV1 AFTER INSERT,UPDATE,DELETE AS BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 59401,'CIMD family history is immutable.',1;
 IF EXISTS(SELECT 1 FROM inserted f LEFT JOIN dbo.AiDelegatedSession s ON s.ShaleClientId=f.ShaleClientId AND s.SessionId=f.SessionId
 LEFT JOIN dbo.AiTenantApplicationApproval p ON p.ShaleClientId=f.ShaleClientId AND p.ApprovalId=f.ApprovalId
 WHERE s.TransactionId IS NOT NULL OR s.SessionId IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.UserSessions w WHERE w.SessionId=f.WebSessionId AND w.ShaleClientId=f.ShaleClientId AND w.UserId=f.UserId AND w.ClientType='WEB') OR p.ApplicationId<>f.ApplicationId OR p.ApplicationRevision<>f.ApplicationRevision
 OR p.IntegrationId IS NULL OR p.IntegrationId<>f.IntegrationId OR s.ClientId<>f.ClientId OR s.Issuer<>f.Issuer OR s.Resource<>f.Resource)
 THROW 59401,'CIMD family ownership unavailable.',1;
END;"""
for name in ['AiImmutable_AiOAuthCredential','AiLifecycle_Credential']:
 new='AiCimd'+name.removeprefix('Ai').replace('_AiOAuthCredential','Credential')+'V1'
 modules[new]=transform(base[name]).replace('dbo.'+name,'dbo.'+new,1)
# Reuse the audited session lifecycle with a new constrained entry point, not a legacy role grant.
revoke=base['AiRevokeSessionV1'].replace('dbo.AiRevokeSessionV1','dbo.AiCimdRevokeFamilyV1',1).replace('@ExpectedRowVer varbinary(8)','@ExpectedRowVer varbinary(max)')
revoke=revoke.replace(" IF @CorrelationId IS NULL", " IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59401,'Fresh CIMD revocation lease required.',1;\n IF @CorrelationId IS NULL")
revoke=revoke.replace(' IF @CorrelationId IS NULL', " IF @ExpectedRowVer IS NULL OR DATALENGTH(@ExpectedRowVer)<>8 THROW 59401,'Exact RowVer required.',1;\n IF @CorrelationId IS NULL",1)
revoke=revoke.replace(' IF @id IS NULL', " IF NOT EXISTS(SELECT 1 FROM dbo.AiCimdFamilyV1 WHERE ShaleClientId=@t AND SessionId=@SessionId) THROW 59401,'CIMD family unavailable.',1;\n IF @id IS NULL",1)
revoke=revoke.replace('  INSERT dbo.EntityActionAuditLog',"  UPDATE dbo.AiCimdCredentialV1 SET RevokedAt=@now WHERE ShaleClientId=@t AND SessionId=@SessionId AND RevokedAt IS NULL;\n  INSERT dbo.EntityActionAuditLog",1)
modules['AiCimdRevokeFamilyV1']=revoke
# No historical object/constraint/index is changed by these copies.
def table(text,name):return re.search(r'CREATE TABLE dbo\.'+name+r'\(.*?\n\);',text,re.S).group(0)
def newddl(text):
 text=transform(text)
 text=re.sub(r'\b(CONSTRAINT|INDEX) (\w+)',lambda m:m[1]+' C4a_'+m[2],text)
 return text
schema=(BUILD/'cimd-v1-schema.sql').read_text()
credential=newddl(table(foundation,'AiOAuthCredential')).replace('REFERENCES dbo.AiDelegatedSession','REFERENCES dbo.AiCimdFamilyV1')
schema+='\n'+credential+"\nCREATE UNIQUE INDEX C4a_CredentialOwner ON dbo.AiCimdCredentialV1(ShaleClientId,Id,SessionId,GrantId,IntegrationId,UserId);\n"
schema+=newddl(table(old,'AiCaseDataInvocation'))+'\n'
schema+="CREATE INDEX C4a_InvocationOwner ON dbo.AiCimdInvocationV1(ShaleClientId,UserId,AdmittedAt);\n"
schema+="CREATE TABLE dbo.AiCimdProofReceiptV1(Id tinyint NOT NULL PRIMARY KEY CHECK(Id=1),SourceHash binary(32) NOT NULL,BeforeHash binary(32) NOT NULL,CatalogHash binary(32) NOT NULL);\n"
modules['AiCimdImmutableReceiptV1']="CREATE TRIGGER dbo.AiCimdImmutableReceiptV1 ON dbo.AiCimdProofReceiptV1 AFTER UPDATE,DELETE AS THROW 59400,'CIMD proof receipt is immutable.',1;"
newtables=['AiCimdFamilyV1','AiCimdCredentialV1','AiCimdInvocationV1','AiCimdProofControlV1','AiCimdProofReceiptV1']
for table_name in newtables[:3]:
 schema+=execute('ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.'+table_name+', ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.'+table_name+' AFTER INSERT, ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.'+table_name+' AFTER UPDATE;')
roles=['AiCimdCaseRuntimeV1','AiCimdLifecycleV1']
roots=['AiCimdData'+suffix+'V1' for suffix in ['Authenticate','SearchCases','GetCaseOverview','FinishCaseQuota','AuthorizeCaseHandoff','RecordCaseFailure']]
schema+='\n'.join('CREATE ROLE '+r+' AUTHORIZATION dbo;' for r in roles)+'\n'
module_sql=''.join('DECLARE @C4aModule'+str(n)+' nvarchar(max)='+hexsql(body)+'; EXEC sys.sp_executesql @C4aModule'+str(n)+';\n' for n,body in enumerate(modules.values()))
permissions='\n'.join('GRANT EXECUTE ON dbo.'+p+' TO AiCimdCaseRuntimeV1;' for p in roots)+'\nGRANT EXECUTE ON dbo.AiCimdRevokeFamilyV1 TO AiCimdLifecycleV1;\n'
for r in roles:
 for t in newtables:permissions+='DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON dbo.'+t+' TO '+r+';\n'
# Private helpers have no grants; public cannot gain executable authority through a broad grant.
for n in modules:
 if n not in roots+['AiCimdRevokeFamilyV1'] and not n.startswith(('AiCimdImmutable','AiCimdLifecycle','AiCimdDataImmutable')):
  permissions+='DENY EXECUTE ON dbo.'+n+' TO public;\n'
source=schema+module_sql+permissions
source_hash=hashlib.sha256(source.encode('utf-16le')).hexdigest()
# Old dispatch/registry checks are embedded byte-for-byte, with no historical refresh or writes.
legacy=[(SQL/'verify.sql').read_text(),(SQL/'universal-pr3-confinement-verify.sql').read_text()]
pre=''.join('DECLARE @C4aPredecessors'+str(i)+' nvarchar(max)='+hexsql(text)+'; EXEC sys.sp_executesql @C4aPredecessors'+str(i)+';\n' for i,text in enumerate(legacy))
owned=newtables+list(modules)
owned_sql="DECLARE @C4aObjects table(Name sysname PRIMARY KEY); INSERT @C4aObjects VALUES "+','.join("(N'"+n+"')" for n in owned)+';\n'
# Fingerprint both new catalog and all explicit permissions on new objects. Old roots are covered by unchanged predecessors.
catalog="""
DECLARE @C4aCatalog nvarchar(max)=N'';
SET @C4aCatalog=@C4aCatalog+(SELECT CONCAT(o.name,N':',o.type,N':',o.principal_id,N':',m.execute_as_principal_id,N':',m.uses_ansi_nulls,N':',m.uses_quoted_identifier,N':',m.definition,N';') AS [text()] FROM sys.objects o LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.schema_id=SCHEMA_ID('dbo') AND o.name IN(SELECT Name FROM @C4aObjects) ORDER BY o.name FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
SET @C4aCatalog=@C4aCatalog+(SELECT CONCAT(OBJECT_NAME(c.object_id),N':',c.column_id,N':',c.name,N':',c.user_type_id,N':',c.max_length,N':',c.is_nullable,N':',c.is_identity,N':',c.collation_name,N';') AS [text()] FROM sys.columns c WHERE OBJECT_NAME(c.object_id) IN(SELECT Name FROM @C4aObjects) ORDER BY c.object_id,c.column_id FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
SET @C4aCatalog=@C4aCatalog+(SELECT CONCAT(OBJECT_NAME(o.parent_object_id),N':',o.name,N':',o.type,N':',ch.definition,N':',ch.is_disabled,N':',ch.is_not_trusted,N':',dc.definition,N':',fk.referenced_object_id,N':',fk.is_disabled,N':',fk.is_not_trusted,N':',fk.delete_referential_action,N':',fk.update_referential_action,N';') AS [text()] FROM sys.objects o LEFT JOIN sys.check_constraints ch ON ch.object_id=o.object_id LEFT JOIN sys.default_constraints dc ON dc.object_id=o.object_id LEFT JOIN sys.foreign_keys fk ON fk.object_id=o.object_id WHERE OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @C4aObjects) ORDER BY o.object_id FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
SET @C4aCatalog=@C4aCatalog+(SELECT CONCAT(f.constraint_object_id,N':',f.constraint_column_id,N':',f.parent_column_id,N':',f.referenced_column_id,N';') AS [text()] FROM sys.foreign_key_columns f WHERE OBJECT_NAME(f.parent_object_id) IN(SELECT Name FROM @C4aObjects) ORDER BY f.constraint_object_id,f.constraint_column_id FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
SET @C4aCatalog=@C4aCatalog+(SELECT CONCAT(i.object_id,N':',i.name,N':',i.is_unique,N':',i.is_disabled,N':',i.filter_definition,N':',c.column_id,N':',c.key_ordinal,N':',c.is_included_column,N';') AS [text()] FROM sys.indexes i JOIN sys.index_columns c ON c.object_id=i.object_id AND c.index_id=i.index_id WHERE OBJECT_NAME(i.object_id) IN(SELECT Name FROM @C4aObjects) ORDER BY i.object_id,i.index_id,c.index_column_id FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
SET @C4aCatalog=@C4aCatalog+(SELECT CONCAT(p.target_object_id,N':',p.object_id,N':',p.predicate_definition,N':',p.predicate_type,N':',p.operation,N';') AS [text()] FROM sys.security_predicates p WHERE OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @C4aObjects) ORDER BY p.target_object_id,p.security_predicate_id FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
SET @C4aCatalog=@C4aCatalog+(SELECT CONCAT(p.class,N':',p.major_id,N':',p.minor_id,N':',p.grantee_principal_id,N':',p.grantor_principal_id,N':',p.permission_name,N':',p.state,N';') AS [text()] FROM sys.database_permissions p WHERE p.class=1 AND OBJECT_NAME(p.major_id) IN(SELECT Name FROM @C4aObjects) OR p.grantee_principal_id IN(DATABASE_PRINCIPAL_ID('AiCimdCaseRuntimeV1'),DATABASE_PRINCIPAL_ID('AiCimdLifecycleV1')) ORDER BY p.class,p.major_id,p.minor_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
SET @C4aCatalog=@C4aCatalog+(SELECT CONCAT(t.name,N':',t.parent_id,N':',t.is_disabled,N':',t.is_instead_of_trigger,N':',e.type,N':',e.is_first,N':',e.is_last,N':',m.definition,N';') AS [text()] FROM sys.triggers t LEFT JOIN sys.trigger_events e ON e.object_id=t.object_id LEFT JOIN sys.sql_modules m ON m.object_id=t.object_id WHERE OBJECT_NAME(t.parent_id) IN(SELECT Name FROM @C4aObjects) ORDER BY t.object_id,e.type FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
DECLARE @C4aFingerprint binary(32)=HASHBYTES('SHA2_256',@C4aCatalog);
"""
oldstate="""DECLARE @C4aOldState nvarchar(max)=(SELECT class,major_id,minor_id,name,CONVERT(varchar(max),CONVERT(varbinary(max),value),2) AS ReceiptValue FROM sys.extended_properties WHERE name LIKE 'Shale.%' AND NOT(class=1 AND OBJECT_NAME(major_id) IN(SELECT Name FROM @C4aObjects)) ORDER BY class,major_id,minor_id,name FOR XML PATH('r'),TYPE).value('.','nvarchar(max)');
SET @C4aOldState=@C4aOldState+(SELECT CONCAT(Id,Issuer,Resource,Contract,JavaTimeZone,SqlTimeZone) AS [text()] FROM dbo.AiCaseDataDeployment ORDER BY Id FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
DECLARE @C4aBefore binary(32)=HASHBYTES('SHA2_256',@C4aOldState);
"""
module_checks="DECLARE @C4aModules table(Name sysname PRIMARY KEY,Hash binary(32),OwnerLookup bit); INSERT @C4aModules VALUES "+','.join("(N'"+n+"',0x"+hashlib.sha256(b.encode('utf-16le')).hexdigest()+","+str(int(n=='AiCimdDataLocateAccessOwnerV1'))+")" for n,b in modules.items())+";\n"
module_checks+="""IF EXISTS(SELECT 1 FROM @C4aModules e WHERE NOT EXISTS(SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE s.name='dbo' AND o.name=e.Name AND COALESCE(o.principal_id,s.principal_id)=1 AND m.uses_ansi_nulls=1 AND m.uses_quoted_identifier=1 AND ((e.OwnerLookup=1 AND m.execute_as_principal_id=-2) OR (e.OwnerLookup=0 AND m.execute_as_principal_id IS NULL)) AND HASHBYTES('SHA2_256',m.definition)=e.Hash)) THROW 59400,'CIMD proof module source/owner/context drift.',1;
"""
shape_checks="""IF EXISTS(SELECT 1 FROM sys.triggers t WHERE OBJECT_NAME(t.parent_id) IN(SELECT Name FROM @C4aObjects) AND t.name NOT IN(SELECT Name FROM @C4aModules))
 OR EXISTS(SELECT 1 FROM sys.sql_expression_dependencies d JOIN sys.objects o ON o.object_id=d.referencing_id WHERE (o.name IN(SELECT Name FROM @C4aObjects) AND o.type='U' OR o.type IN('C','D') AND OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @C4aObjects)) AND (d.referenced_server_name IS NOT NULL OR d.referenced_database_name IS NOT NULL OR EXISTS(SELECT 1 FROM sys.objects f WHERE f.object_id=d.referenced_id AND f.type IN('FN','IF','TF','FS','FT','P','PC','SN'))))
 THROW 59400,'Unreviewed CIMD trigger/table-expression edge.',1;
IF (SELECT COUNT(*) FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id WHERE OBJECT_NAME(p.target_object_id) IN('AiCimdFamilyV1','AiCimdCredentialV1','AiCimdInvocationV1') AND sp.object_id=OBJECT_ID('sec.TenantFilter') AND sp.is_enabled=1 AND sp.is_schema_bound=1 AND p.predicate_definition COLLATE Latin1_General_100_BIN2=N'([sec].[fn_FilterByTenant]([ShaleClientId]))')<>9
 OR (SELECT COUNT(*) FROM sys.security_predicates WHERE OBJECT_NAME(target_object_id) IN('AiCimdFamilyV1','AiCimdCredentialV1','AiCimdInvocationV1'))<>9
 THROW 59400,'CIMD strict RLS drift.',1;
"""
verify=pre+owned_sql+oldstate+module_checks+shape_checks+catalog+f"""
IF ISNULL((SELECT SourceHash FROM dbo.AiCimdProofReceiptV1 WHERE Id=1),0x)<>0x{source_hash}
 OR ISNULL((SELECT CatalogHash FROM dbo.AiCimdProofReceiptV1 WHERE Id=1),0x)<>@C4aFingerprint
 OR ISNULL((SELECT BeforeHash FROM dbo.AiCimdProofReceiptV1 WHERE Id=1),0x)<>@C4aBefore
 THROW 59400,'CIMD proof catalog/source or predecessor drift; never refresh receipts.',1;
"""
# Require exact role/member boundaries as well as the immutable catalog. No membership is installed here.
verify+="""
IF (SELECT COUNT(*) FROM sys.database_principals WHERE name IN('AiCimdCaseRuntimeV1','AiCimdLifecycleV1') AND type='R' AND owning_principal_id=1)<>2
 OR EXISTS(SELECT 1 FROM sys.database_role_members WHERE member_principal_id IN(DATABASE_PRINCIPAL_ID('AiCimdCaseRuntimeV1'),DATABASE_PRINCIPAL_ID('AiCimdLifecycleV1')))
 THROW 59400,'CIMD proof role owner/inheritance drift.',1;
DECLARE @C4aMembers table(Id int PRIMARY KEY,RoleId int);
INSERT @C4aMembers SELECT member_principal_id,role_principal_id FROM sys.database_role_members WHERE role_principal_id IN(DATABASE_PRINCIPAL_ID('AiCimdCaseRuntimeV1'),DATABASE_PRINCIPAL_ID('AiCimdLifecycleV1'));
IF EXISTS(SELECT 1 FROM @C4aMembers x JOIN sys.database_principals p ON p.principal_id=x.Id WHERE p.type<>'S' OR p.principal_id<=4)
 OR EXISTS(SELECT 1 FROM @C4aMembers x JOIN sys.database_role_members r ON r.member_principal_id=x.Id WHERE r.role_principal_id<>x.RoleId)
 OR EXISTS(SELECT 1 FROM @C4aMembers x JOIN sys.database_permissions p ON p.grantee_principal_id=x.Id WHERE NOT(p.class=0 AND p.permission_name='CONNECT' AND p.state='G'))
 OR EXISTS(SELECT 1 FROM sys.schemas WHERE principal_id IN(SELECT Id FROM @C4aMembers))
 OR EXISTS(SELECT 1 FROM sys.objects WHERE principal_id IN(SELECT Id FROM @C4aMembers))
 THROW 59400,'CIMD proof member authority drift.',1;
IF EXISTS(SELECT 1 FROM sys.crypt_properties WHERE major_id IN(SELECT OBJECT_ID('dbo.'+Name) FROM @C4aObjects))
 THROW 59400,'CIMD proof signature drift.',1;
"""
install="""/* PR 4A CANDIDATE: disposable verification only; no automatic installation/activation. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;
IF USER_NAME()<>'dbo' OR @@TRANCOUNT<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 59400,'Fresh unscoped dbo lease required.',1;
BEGIN TRY BEGIN TRANSACTION;
DECLARE @C4aLock int;
EXEC @C4aLock=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
IF @C4aLock<0 OR NOT EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Id=1 AND Enabled=0)
 THROW 59400,'Exclusive schema lock and capability OFF required.',1;
"""+pre+owned_sql
install+="IF EXISTS(SELECT 1 FROM sys.objects WHERE schema_id=SCHEMA_ID('dbo') AND name IN(SELECT Name FROM @C4aObjects)) OR EXISTS(SELECT 1 FROM sys.database_principals WHERE name IN('AiCimdCaseRuntimeV1','AiCimdLifecycleV1')) THROW 59400,'CIMD proof objects already present; never overwrite.',1;\n"
install+=oldstate+'DECLARE @C4aSaved binary(32)=@C4aBefore;\n'+source+module_checks+shape_checks+pre.replace('@C4aPredecessors','@C4aAfterPredecessors')+catalog
install+="INSERT dbo.AiCimdProofReceiptV1(Id,SourceHash,BeforeHash,CatalogHash) VALUES(1,0x"+source_hash+",@C4aSaved,@C4aFingerprint);\n"
install+='COMMIT TRANSACTION; END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;\n'
verify+="""
DECLARE @C4aTokens table(Id int PRIMARY KEY);
INSERT @C4aTokens SELECT Id FROM @C4aMembers;
INSERT @C4aTokens VALUES(DATABASE_PRINCIPAL_ID('AiCimdCaseRuntimeV1')),(DATABASE_PRINCIPAL_ID('AiCimdLifecycleV1'));
IF EXISTS(SELECT 1 FROM sys.schemas WHERE principal_id IN(SELECT Id FROM @C4aTokens))
 OR EXISTS(SELECT 1 FROM sys.objects WHERE principal_id IN(SELECT Id FROM @C4aTokens))
 OR EXISTS(SELECT 1 FROM sys.database_principals WHERE owning_principal_id IN(SELECT Id FROM @C4aTokens))
 OR EXISTS(SELECT 1 FROM sys.types WHERE is_user_defined=1 AND principal_id IN(SELECT Id FROM @C4aTokens))
 OR EXISTS(SELECT 1 FROM sys.assemblies WHERE principal_id IN(SELECT Id FROM @C4aTokens))
 OR EXISTS(SELECT 1 FROM sys.certificates WHERE principal_id IN(SELECT Id FROM @C4aTokens))
 OR EXISTS(SELECT 1 FROM sys.asymmetric_keys WHERE principal_id IN(SELECT Id FROM @C4aTokens))
 THROW 59400,'CIMD proof securable ownership drift.',1;
DECLARE @C4aEdition int=CONVERT(int,SERVERPROPERTY('EngineEdition'));
IF @C4aEdition IS NULL OR @C4aEdition NOT IN(2,3,4,5,8) THROW 59400,'Unknown CIMD SQL platform.',1;
IF EXISTS(SELECT 1 FROM sys.database_principals p JOIN @C4aMembers a ON a.Id=p.principal_id WHERE p.authentication_type<>CASE WHEN @C4aEdition=5 THEN 2 ELSE 1 END)
 THROW 59400,'CIMD SQL authentication type drift.',1;
"""
server_check="""IF EXISTS(SELECT 1 FROM sys.database_role_members r JOIN sys.database_principals u ON u.principal_id=r.member_principal_id LEFT JOIN sys.server_principals s ON s.sid=u.sid WHERE r.role_principal_id IN(DATABASE_PRINCIPAL_ID('AiCimdCaseRuntimeV1'),DATABASE_PRINCIPAL_ID('AiCimdLifecycleV1')) AND (s.principal_id IS NULL OR s.type<>'S' OR s.is_disabled=1))
 OR EXISTS(SELECT 1 FROM sys.server_role_members r JOIN sys.server_principals s ON s.principal_id=r.member_principal_id JOIN sys.database_principals u ON u.sid=s.sid JOIN sys.database_role_members d ON d.member_principal_id=u.principal_id WHERE d.role_principal_id IN(DATABASE_PRINCIPAL_ID('AiCimdCaseRuntimeV1'),DATABASE_PRINCIPAL_ID('AiCimdLifecycleV1')))
 OR EXISTS(SELECT 1 FROM sys.server_permissions p JOIN sys.server_principals s ON s.principal_id=p.grantee_principal_id JOIN sys.database_principals u ON u.sid=s.sid JOIN sys.database_role_members d ON d.member_principal_id=u.principal_id WHERE d.role_principal_id IN(DATABASE_PRINCIPAL_ID('AiCimdCaseRuntimeV1'),DATABASE_PRINCIPAL_ID('AiCimdLifecycleV1')) AND p.state IN('G','W') AND NOT(p.class=100 AND p.state='G' AND p.permission_name IN('CONNECT SQL','VIEW ANY DATABASE')))
 THROW 59400,'CIMD SQL login authority drift.',1;
"""
verify+="IF @C4aEdition<>5 BEGIN DECLARE @C4aServerCheck nvarchar(max)="+hexsql(server_check)+"; EXEC sys.sp_executesql @C4aServerCheck; END;\n"
emit(SQL/'universal-pr4a-proof.sql',install);emit(SQL/'universal-pr4a-proof-verify.sql',verify)
emit(BUILD/'cimd-v1-modules.sha256',''.join(n+'='+hashlib.sha256(b.encode('utf-16le')).hexdigest()+'\n' for n,b in modules.items()))
print('CIMD proof source '+source_hash+'; modules='+str(len(modules)))
