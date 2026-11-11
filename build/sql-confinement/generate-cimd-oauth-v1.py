#!/usr/bin/env python3
"""Additive PR 4B SQL candidate. Never writes a historical module, receipt or binding."""
from pathlib import Path
import argparse, hashlib, re
ROOT=Path(__file__).resolve().parents[2]; BUILD=Path(__file__).resolve().parent; SQL=ROOT/'docs/sql/ai-integration'
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
def emit(path,value):
 if args.check:
  if not path.exists() or path.read_text()!=value:raise SystemExit('Stale artifact: '+str(path.relative_to(ROOT)))
 else:path.write_text(value)
def defs(text):
 result={}
 for m in re.finditer(r"EXEC\(N'((?:[^']|'')*)'\);",text,re.S):
  b=m[1].replace("''", "'"); n=re.match(r'(?:CREATE|ALTER) (?:PROCEDURE|TRIGGER) dbo\.(\w+)',b)
  if n:result[n[1]]=re.sub(r'^ALTER ', 'CREATE ',b)
 return result
legacy={}
for f in ['phase3b1.sql','phase3b2b.sql','phase3b2c1.sql','phase3b2c2.sql','revocation.sql']:legacy.update(defs((SQL/f).read_text()))
def transform(b):
 return b.replace('dbo.AiAuthorizationTransaction','dbo.AiCimdAuthorizationV1').replace('dbo.AiOAuthCredential','dbo.AiCimdCredentialV1').replace('dbo.AiAssertionReplay','dbo.AiCimdAssertionReplayV1')
modules={}
for b in (BUILD/'cimd-oauth-v1-modules.sql').read_text().split('\nGO\n'):
 b=b.strip();match=re.search(r'CREATE (?:PROCEDURE|TRIGGER) dbo\.(\w+)',b)
 if match:modules[match[1]]=b[b.index('CREATE '):]
# Preserve the existing public native-loopback exception over the validated sixteen-callback projection.
original_redirect=legacy['AiBeginOAuthV1']
start=original_redirect.index(' IF NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationRedirect r')
end=original_redirect.index(" IF @type='CONFIDENTIAL'",start)
redirect=original_redirect[start:end]
redirect=redirect.replace('FROM dbo.AiIntegrationRedirect r WHERE r.ShaleClientId=@t AND r.IntegrationId=@i AND','FROM OPENJSON(@redirects) r WHERE')
redirect=redirect.replace('r.RedirectUri','r.value').replace("@type='PUBLIC'","@method='none'")
redirect=redirect.replace('r.value=@RedirectUri OR','(r.value=@RedirectUri AND DATALENGTH(r.value)=DATALENGTH(@RedirectUri)) OR')
redirect=redirect.replace('r.value','(r.value COLLATE Latin1_General_100_BIN2)')
begin=modules['AiCimdBeginOAuthV1']
start=begin.index(' IF NOT EXISTS(SELECT 1 FROM OPENJSON(@redirects)')
end=begin.index(' EXEC dbo.AiActorV1;',start)
modules['AiCimdBeginOAuthV1']=begin[:start]+redirect+begin[end:]

fresh=" IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD lease required.',1;\n"
# Reuse the existing immutable original-WEB/consent algorithms in a separate transaction namespace.
for suffix in ['ApproveOAuth','CompleteOAuth','DenyOAuth','FindOAuthIntent']:
 old='Ai'+suffix+'V1';new='AiCimd'+suffix+'V1';b=transform(legacy[old]).replace('dbo.'+old,'dbo.'+new,1)
 b=b.replace('BEGIN TRY',fresh+'BEGIN TRY',1).replace("Status='ACTIVE'","Status='SUSPENDED' AND Revision=1")
 marker="@now datetime2(7)=SYSUTCDATETIME();"
 guard="\n EXEC dbo.AiCimdValidateTransactionV1 @TransactionId,@WebSessionId;"
 if suffix=='FindOAuthIntent':
  guard="\n DECLARE @TransactionId uniqueidentifier; SELECT @TransactionId=TransactionId FROM dbo.AiCimdAuthorizationV1 WHERE ShaleClientId=@t AND UserId=@u AND IntentHash=@IntentHash AND WebSessionId=@WebSessionId;"+guard
 b=b.replace(marker,marker+guard,1)
 if suffix=='FindOAuthIntent':
  b=b.replace('SELECT * FROM dbo.AiCimdAuthorizationV1 WHERE', 'SELECT *, (SELECT GrantExpiresAt FROM dbo.AiConsentIntent c WHERE c.ShaleClientId=@t AND c.UserId=@u AND c.IntentHash=dbo.AiCimdAuthorizationV1.IntentHash) AS GrantExpiresAt FROM dbo.AiCimdAuthorizationV1 WHERE')
 modules[new]=b
b=transform(legacy['AiImmutable_AiAuthorizationTransaction']).replace('AiImmutable_AiAuthorizationTransaction','AiCimdImmutableAuthorizationV1')
b=b.replace('IF UPDATE(TransactionId)', 'IF UPDATE(ApplicationId) OR UPDATE(ApplicationRevision) OR UPDATE(ApprovalId) OR UPDATE(ApprovalVersion) OR UPDATE(MetadataDigest) OR UPDATE(ExternalClientId) OR UPDATE(AuthMethod) OR UPDATE(TransactionId)')
modules['AiCimdImmutableAuthorizationV1']=b
modules['AiCimdValidateTransactionV1']="""CREATE PROCEDURE dbo.AiCimdValidateTransactionV1 @TransactionId uniqueidentifier,@Web uniqueidentifier
AS BEGIN
 SET NOCOUNT ON;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),
 @a uniqueidentifier,@r bigint,@p uniqueidentifier,@v binary(8),@d binary(32),@e nvarchar(2048),@issuer nvarchar(256),@resource nvarchar(256),
 @i uniqueidentifier,@sc tinyint,@m varchar(16),@k uniqueidentifier,@deadline datetime2(7);
 SELECT @a=ApplicationId,@r=ApplicationRevision,@p=ApprovalId,@v=ApprovalVersion,@d=MetadataDigest,@e=ExternalClientId,@issuer=Issuer,@resource=Resource,
 @i=IntegrationId,@sc=RequestedScopeMask,@m=AuthMethod,@k=CredentialId FROM dbo.AiCimdAuthorizationV1
 WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u AND WebSessionId=@Web AND DeniedAt IS NULL;
 EXEC dbo.AiCimdOAuthAuthorityV1 @a,@r,@p,@v,@d,@e,@issuer,@resource,@i,@Web,@sc,@m,@k,1,@deadline OUTPUT;
END;"""
# Initial issue remains private: only the complete redeem procedure can reach it via ownership chaining.
b=transform(legacy['AiIssueCredentialsV1']).replace('dbo.AiIssueCredentialsV1','dbo.AiCimdIssueV1',1)
b=b.replace("r.Status='ACTIVE'","r.Status='SUSPENDED' AND r.Revision=1")
b=b.replace('a.ClientId=@ClientId AND DATALENGTH(a.ClientId)=DATALENGTH(@ClientId)', 'a.ExternalClientId=@ClientId AND DATALENGTH(a.ExternalClientId)=DATALENGTH(@ClientId)')
b=b.replace('@TransactionId,@ClientId,@Issuer,@Resource,@consent,@revision);','NULL,(SELECT ClientId FROM dbo.AiCimdAuthorizationV1 WHERE TransactionId=@TransactionId),@Issuer,@Resource,@consent,@revision);')
family="""
 INSERT dbo.AiCimdFamilyV1(ShaleClientId,SessionId,IntegrationId,GrantId,UserId,TransactionId,WebSessionId,ApplicationId,ApplicationRevision,ApprovalId,ApprovalVersion,MetadataDigest,ExternalClientId,ClientId,Issuer,Resource,CredentialId,AuthMethod,RedirectUri,Challenge,CodeHash,CodeIssuedAt,CodeExpiresAt,ConsumedAt)
 SELECT ShaleClientId,@s,IntegrationId,GrantId,UserId,TransactionId,WebSessionId,ApplicationId,ApplicationRevision,ApprovalId,ApprovalVersion,MetadataDigest,ExternalClientId,ClientId,Issuer,Resource,CredentialId,AuthMethod,RedirectUri,Challenge,CodeHash,IssuedAt,CodeExpiresAt,ConsumedAt
 FROM dbo.AiCimdAuthorizationV1 WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u;
 IF @@ROWCOUNT<>1 THROW 59501,'CIMD family receipt unavailable.',1;
"""
b=b.replace(' INSERT dbo.AiCimdCredentialV1(',family+' INSERT dbo.AiCimdCredentialV1(',1)
# Clip the session to the current approval, independently of the original grant.
b=b.replace(" IF @i IS NULL OR @deadline<=SYSUTCDATETIME()", " SELECT @deadline=CASE WHEN p.ExpiresAt<@deadline THEN p.ExpiresAt ELSE @deadline END FROM dbo.AiCimdAuthorizationV1 a JOIN dbo.AiTenantApplicationApproval p ON p.ShaleClientId=a.ShaleClientId AND p.ApprovalId=a.ApprovalId WHERE a.TransactionId=@TransactionId AND a.ShaleClientId=@t;\n IF @i IS NULL OR @deadline<=SYSUTCDATETIME()",1)
modules['AiCimdIssueV1']=b
b=transform(legacy['AiExchangeOAuthCodeV1']).replace('dbo.AiExchangeOAuthCodeV1','dbo.AiCimdRedeemV1',1)
b=b.replace('@CorrelationId uniqueidentifier\nAS BEGIN','@CorrelationId uniqueidentifier,@AccessHash varbinary(max),@RefreshHash varbinary(max)\nAS BEGIN',1)
b=b.replace(' DECLARE @owns bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END;\n IF @owns=1 BEGIN TRANSACTION;',fresh+' BEGIN TRANSACTION;')
b=b.replace('DATALENGTH(@ClientId) NOT BETWEEN 1 AND 64','DATALENGTH(@ClientId) NOT BETWEEN 2 AND 4096')
b=b.replace("Status='ACTIVE'","Status='SUSPENDED' AND Revision=1")
b=b.replace('ClientId=@ClientId AND DATALENGTH(ClientId)=DATALENGTH(@ClientId)','ExternalClientId=@ClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(ExternalClientId)=DATALENGTH(@ClientId)')
marker=' DECLARE @i uniqueidentifier,@scopes tinyint'
b=b.replace(marker,' EXEC dbo.AiCimdValidateTransactionV1 @TransactionId,@WebSessionId;\n'+marker,1)
start=b.index(' SELECT @t AS ShaleClientId,');end=b.index(' END TRY BEGIN CATCH',start)
b=b[:start]+" EXEC dbo.AiCimdIssueV1 @TransactionId,@ClientId,@Issuer,@Resource,@grantId,@KeyId,@scopesApproved,@AccessHash,@RefreshHash,@CorrelationId;\n COMMIT TRANSACTION;\n"+b[end:]
# Requested scopes can differ from approved scopes; issuance must use the committed explicit decision.
b=b.replace(' EXEC dbo.AiCimdIssueV1', ' DECLARE @scopesApproved tinyint; SELECT @scopesApproved=ApprovedScopeMask FROM dbo.AiCimdAuthorizationV1 WHERE TransactionId=@TransactionId AND ShaleClientId=@t;\n EXEC dbo.AiCimdIssueV1',1)
b=b.replace('BEGIN TRY\n'+fresh,fresh+'BEGIN TRY\n',1)
modules['AiCimdRedeemV1']=b
b=transform(legacy['AiRefreshCredentialsV1']).replace('dbo.AiRefreshCredentialsV1','dbo.AiCimdRefreshV1',1)
b=b.replace(' BEGIN TRY',fresh+' BEGIN TRY',1).replace('DATALENGTH(@ClientId) NOT BETWEEN 1 AND 64','DATALENGTH(@ClientId) NOT BETWEEN 2 AND 4096')
b=b.replace(' AND s.ClientId=@ClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.ClientId)=DATALENGTH(@ClientId)', ' AND EXISTS(SELECT 1 FROM dbo.AiCimdFamilyV1 f WHERE f.ShaleClientId=s.ShaleClientId AND f.SessionId=s.SessionId AND f.ExternalClientId=@ClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(f.ExternalClientId)=DATALENGTH(@ClientId))')
b=b.replace(' AND ClientId=@ClientId;', ';')
b=b.replace('dbo.AiCimdAuthorizationV1 a WITH(HOLDLOCK) ON a.ShaleClientId=s.ShaleClientId AND a.TransactionId=s.TransactionId', 'dbo.AiCimdFamilyV1 a WITH(HOLDLOCK) ON a.ShaleClientId=s.ShaleClientId AND a.SessionId=s.SessionId AND s.TransactionId IS NULL')
b=b.replace("r.Status='ACTIVE'","r.Status='SUSPENDED' AND r.Revision=1")
# Current application/approval must be checked even on the reuse path, before reservations or mutation.
marker=" IF @type='CONFIDENTIAL' BEGIN"
family_check="""
 DECLARE @fa uniqueidentifier,@fr bigint,@fp uniqueidentifier,@fv binary(8),@fd binary(32),@fe nvarchar(2048),@fw uniqueidentifier,@fm varchar(16),@currentDeadline datetime2(7);
 SELECT @fa=ApplicationId,@fr=ApplicationRevision,@fp=ApprovalId,@fv=ApprovalVersion,@fd=MetadataDigest,@fe=ExternalClientId,@fw=WebSessionId,@fm=AuthMethod
 FROM dbo.AiCimdFamilyV1 WHERE ShaleClientId=@t AND SessionId=@s AND UserId=@u;
 EXEC dbo.AiCimdOAuthAuthorityV1 @fa,@fr,@fp,@fv,@fd,@fe,@Issuer,@Resource,@i,@fw,@scopes,@fm,@key,1,@currentDeadline OUTPUT;
"""
b=b.replace(marker,family_check+marker,1)
b=b.replace(' IF @consumed IS NOT NULL OR', ' IF @consumed IS NOT NULL OR',1)
# Explicit new-namespace revocation; the historical session trigger only cascades legacy credentials.
b=b.replace(' COMMIT TRANSACTION;\n SELECT CAST(1 AS bit)', ' UPDATE dbo.AiCimdCredentialV1 SET RevokedAt=@now WHERE ShaleClientId=@t AND SessionId=@s AND RevokedAt IS NULL;\n COMMIT TRANSACTION;\n SELECT CAST(1 AS bit)',1)
b=b.replace(' SET @now=SYSUTCDATETIME();\n IF @revoked', ' IF @currentDeadline<@liveDeadline SET @liveDeadline=@currentDeadline;\n SET @now=SYSUTCDATETIME();\n IF @revoked',1)
modules['AiCimdRefreshV1']=b
# Bootstrap resolves exact external identity and digest only, never a tenant chosen by the caller.
for suffix,table,condition in [('Code','AiCimdAuthorizationV1','x.CodeHash=@Hash'),('Refresh','AiCimdCredentialV1',"c.CredentialHash=@Hash AND c.Kind='REFRESH'"),('Revocation','AiCimdCredentialV1','c.CredentialHash=@Hash')]:
 name='AiCimdResolve'+suffix+'V1'
 query='FROM dbo.AiCimdAuthorizationV1 x' if suffix=='Code' else 'FROM dbo.AiCimdCredentialV1 c JOIN dbo.AiCimdFamilyV1 x ON x.ShaleClientId=c.ShaleClientId AND x.SessionId=c.SessionId AND x.IntegrationId=c.IntegrationId AND x.UserId=c.UserId AND x.GrantId=c.GrantId'
 modules[name]=f"""CREATE PROCEDURE dbo.{name} @Hash varbinary(max),@External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max)
 WITH EXECUTE AS OWNER
 AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL OR @Hash IS NULL OR DATALENGTH(@Hash)<>32 THROW 59501,'Neutral resolver required.',1;
 SELECT x.ShaleClientId,x.UserId {query} WHERE {condition}
 AND x.ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.ExternalClientId)=DATALENGTH(@External)
 AND x.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.Issuer)=DATALENGTH(@Issuer)
 AND x.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.Resource)=DATALENGTH(@Resource);
 END;"""
# The full external identity is shared by tenants, so tenant-local replay alone is insufficient.
for name,origin in [('AiCimdRedeemV1','transaction'),('AiCimdRefreshV1','family')]:
 b=modules[name]
 if origin=='transaction':
  query="SELECT @ja=ApplicationId,@jr=ApplicationRevision,@ji=IntegrationId FROM dbo.AiCimdAuthorizationV1 WHERE ShaleClientId=@t AND TransactionId=@TransactionId;"
 elif origin=='family':
  query="SELECT @ja=ApplicationId,@jr=ApplicationRevision,@ji=IntegrationId FROM dbo.AiCimdFamilyV1 WHERE ShaleClientId=@t AND SessionId=@s;"
 else:
  query="SELECT @ja=f.ApplicationId,@jr=f.ApplicationRevision,@ji=f.IntegrationId FROM dbo.AiCimdFamilyV1 f JOIN dbo.AiCimdCredentialV1 c ON c.ShaleClientId=f.ShaleClientId AND c.SessionId=f.SessionId WHERE c.ShaleClientId=@t AND c.UserId=@u AND c.CredentialHash=@Hash;"
 reserve=" DECLARE @ja uniqueidentifier,@jr bigint,@ji uniqueidentifier,@jk nvarchar(128); "+query+" SELECT @jk=Kid FROM dbo.AiCimdKeyBindingV1 WHERE ShaleClientId=@t AND IntegrationId=@ji AND ApplicationId=@ja AND ApplicationRevision=@jr AND CredentialId=@KeyId; EXEC dbo.AiCimdReserveAssertionV1 @ja,@jr,@jk,@JtiHash,@AssertionIssuedAt,@AssertionExpiresAt;\n"
 b=b.replace(" IF @type='CONFIDENTIAL' BEGIN", " IF @type='CONFIDENTIAL' BEGIN\n"+reserve,1)
 modules[name]=b

# Wire CIMD IDs are full Unicode SQL strings; internal anchor IDs retain varchar(64).
for name in ['AiCimdRedeemV1','AiCimdIssueV1','AiCimdRefreshV1']:
 modules[name]=modules[name].replace('@ClientId varchar(max)','@ClientId nvarchar(max)')

# New table source derives exact verified FK/expiry/PKCE/nonce constraints from the immutable predecessor.
old=(SQL/'phase3b2b.sql').read_text()
tx=re.search(r'CREATE TABLE dbo.AiAuthorizationTransaction\(.*?\n\);',old,re.S)[0]
tx=transform(tx).replace('AiAuthorizationTransaction','AiCimdAuthorizationV1')
tx=tx.replace(' TransactionId uniqueidentifier', ''' ApplicationId uniqueidentifier NOT NULL,ApplicationRevision bigint NOT NULL,ApprovalId uniqueidentifier NOT NULL,ApprovalVersion binary(8) NOT NULL,
 MetadataDigest binary(32) NOT NULL,ExternalClientId nvarchar(2048) COLLATE Latin1_General_100_BIN2 NOT NULL,AuthMethod varchar(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CONSTRAINT FK_C4b_Approval FOREIGN KEY(ShaleClientId,ApprovalId) REFERENCES dbo.AiTenantApplicationApproval(ShaleClientId,ApprovalId),
 CONSTRAINT FK_C4b_Publication FOREIGN KEY(ApplicationId,ApplicationRevision) REFERENCES dbo.AiCimdPublicationV1(ApplicationId,ApplicationRevision),
 TransactionId uniqueidentifier''',1)
replay=re.search(r'CREATE TABLE dbo.AiAssertionReplay\(.*?\n\);',(SQL/'phase3b2c1.sql').read_text(),re.S)[0]
replay=transform(replay).replace('AiAssertionReplay','AiCimdAssertionReplayV1')
schema=(BUILD/'cimd-oauth-v1-schema.sql').read_text()+tx+'\n'+replay+'\n'
schema+='CREATE UNIQUE INDEX C4b_Code ON dbo.AiCimdAuthorizationV1(CodeHash) WHERE CodeHash IS NOT NULL;\n'
# The global neutral replay table is reserved for authenticated unknown-token revocation in runtime integration.
# Unknown tokens never select an arbitrary tenant. This candidate does not expose that path.
tables=['AiCimdNeutralReplayV1','AiCimdPublicationV1','AiCimdKeyBindingV1','AiCimdOAuthControlV1','AiCimdOAuthReceiptV1','AiCimdAuthorizationV1','AiCimdAssertionReplayV1']
tenant_tables=['AiCimdKeyBindingV1','AiCimdAuthorizationV1','AiCimdAssertionReplayV1']
modules['AiCimdImmutablePublicationV1']="""CREATE TRIGGER dbo.AiCimdImmutablePublicationV1 ON dbo.AiCimdPublicationV1 AFTER UPDATE,DELETE AS BEGIN SET NOCOUNT ON;
 IF NOT EXISTS(SELECT 1 FROM inserted) OR UPDATE(ApplicationId) OR UPDATE(ApplicationRevision) OR UPDATE(MetadataDigest) OR UPDATE(Redirects) OR UPDATE(SigningKeys) THROW 59501,'CIMD publication is immutable.',1; END;"""
modules['AiCimdImmutableKeyBindingV1']="CREATE TRIGGER dbo.AiCimdImmutableKeyBindingV1 ON dbo.AiCimdKeyBindingV1 AFTER UPDATE,DELETE AS THROW 59501,'CIMD key binding is immutable.',1;"
modules['AiCimdImmutableOAuthReceiptV1']="CREATE TRIGGER dbo.AiCimdImmutableOAuthReceiptV1 ON dbo.AiCimdOAuthReceiptV1 AFTER UPDATE,DELETE AS THROW 59501,'CIMD OAuth receipt is immutable.',1;"
# More explicitly authored modules share the same deterministic source/verification closure.
extra=BUILD/'cimd-oauth-v1-extra.sql'
if extra.exists():
 for b in extra.read_text().split('\nGO\n'):
  b=b.strip();match=re.search(r'CREATE (?:PROCEDURE|TRIGGER) dbo\.(\w+)',b)
  if match:modules[match[1]]=b[b.index('CREATE '):]
roles={'AiCimdOAuthNeutralV1':['AiCimdRevokeUnknownV1'], 'AiCimdMetadataPublisherV1':['AiCimdPublishMetadataV1','AiCimdResolvePublicationV1'],
       'AiCimdOAuthBrowserV1':['AiCimdBeginOAuthV1','AiCimdFindOAuthIntentV1','AiCimdApproveOAuthV1','AiCimdCompleteOAuthV1','AiCimdDenyOAuthV1','AiCimdLoadClientV1'],
       'AiCimdOAuthIssuerV1':['AiCimdRedeemV1','AiCimdRefreshV1','AiCimdRevokeCredentialV1','AiCimdLoadClientProofV1'],
       'AiCimdOAuthResolverV1':['AiCimdResolveCodeV1','AiCimdResolveRefreshV1','AiCimdResolveRevocationV1']}
for table in tenant_tables:
 schema+=f"EXEC(N'ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.{table}, ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.{table} AFTER INSERT, ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.{table} AFTER UPDATE;');\n"
for role in roles:schema+=f'CREATE ROLE {role} AUTHORIZATION dbo;\n'
def literal(s):return "N'"+s.replace("'","''")+"'"
def execute(s):return 'EXEC sys.sp_executesql '+literal(s)+';\n'
module_sql=''.join(execute(b) for b in modules.values())
permissions='';expected=[]
for role,roots in roles.items():
 for name in roots:
  if name not in modules:raise SystemExit('Missing module '+name)
  permissions+=f'GRANT EXECUTE ON dbo.{name} TO {role};\n';expected.append((role,name,'EXECUTE','G'))
 for table in tables:
  permissions+=f'DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON dbo.{table} TO {role};\n'
  for perm in ['SELECT','INSERT','UPDATE','DELETE','ALTER','CONTROL']:expected.append((role,table,perm,'D'))
public_roots={n for roots in roles.values() for n in roots}
for name,b in modules.items():
 if name not in public_roots and b.startswith('CREATE PROCEDURE'):permissions+=f'DENY EXECUTE ON dbo.{name} TO public;\n'
source=schema+module_sql+permissions
source_hash=hashlib.sha256(source.encode('utf-16le')).hexdigest()
# Independent before/catalog receipt; unchanged predecessor verification is nested, never resealed.
pre=execute((SQL/'universal-pr4a-proof-verify.sql').read_text())
owned=tables+list(modules)
owned_sql="DECLARE @C4bObjects table(Name sysname PRIMARY KEY); INSERT @C4bObjects VALUES "+','.join('('+literal(n)+')' for n in owned)+';\n'
oldstate="""DECLARE @C4bOldState nvarchar(max)=(SELECT class,major_id,minor_id,name,CONVERT(varchar(max),CONVERT(varbinary(max),value),2) AS ReceiptValue FROM sys.extended_properties WHERE name LIKE 'Shale.%' ORDER BY class,major_id,minor_id,name FOR XML PATH('r'),TYPE).value('.','nvarchar(max)');
SET @C4bOldState=@C4bOldState+(SELECT CONCAT(Id,Issuer,Resource,Contract,JavaTimeZone,SqlTimeZone) AS [text()] FROM dbo.AiCaseDataDeployment ORDER BY Id FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
SET @C4bOldState=@C4bOldState+(SELECT CONCAT(Id,CONVERT(varchar(64),SourceHash,2),CONVERT(varchar(64),BeforeHash,2),CONVERT(varchar(64),CatalogHash,2)) AS [text()] FROM dbo.AiCimdProofReceiptV1 ORDER BY Id FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
DECLARE @C4bBefore binary(32)=HASHBYTES('SHA2_256',@C4bOldState);
"""
# Reuse reviewed catalog serialization, with only this new ownership set. No historical file is changed.
generator=(BUILD/'generate-cimd-v1.py').read_text()
catalog=re.search(r'catalog="""(.*?)"""',generator,re.S)[1].replace('C4a','C4b')
catalog=catalog.replace("DATABASE_PRINCIPAL_ID('AiCimdCaseRuntimeV1'),DATABASE_PRINCIPAL_ID('AiCimdLifecycleV1')",','.join("DATABASE_PRINCIPAL_ID('"+r+"')" for r in roles))
verify=pre+owned_sql+oldstate
verify+="DECLARE @C4bModules table(Name sysname,Hash binary(32),OwnerLookup bit); INSERT @C4bModules VALUES "+','.join('('+literal(n)+',0x'+hashlib.sha256(b.encode('utf-16le')).hexdigest()+','+str(int('WITH EXECUTE AS OWNER' in b))+')' for n,b in modules.items())+';\n'
verify+="""IF EXISTS(SELECT 1 FROM @C4bModules e WHERE NOT EXISTS(SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE s.name='dbo' AND o.name=e.Name AND COALESCE(o.principal_id,s.principal_id)=1 AND m.uses_ansi_nulls=1 AND m.uses_quoted_identifier=1 AND ((e.OwnerLookup=1 AND m.execute_as_principal_id=-2) OR (e.OwnerLookup=0 AND m.execute_as_principal_id IS NULL)) AND HASHBYTES('SHA2_256',m.definition)=e.Hash)) THROW 59500,'CIMD OAuth module drift.',1;
"""
verify+="DECLARE @C4bPermissions table(RoleName sysname COLLATE Latin1_General_100_BIN2,ObjectName sysname COLLATE Latin1_General_100_BIN2,PermissionName sysname COLLATE Latin1_General_100_BIN2,State char(1) COLLATE Latin1_General_100_BIN2); INSERT @C4bPermissions VALUES "+','.join('('+','.join(literal(x) for x in row)+')' for row in expected)+';\n'
role_ids=','.join("DATABASE_PRINCIPAL_ID('"+r+"')" for r in roles)
verify+=f"""IF EXISTS(SELECT USER_NAME(p.grantee_principal_id) COLLATE Latin1_General_100_BIN2,OBJECT_NAME(p.major_id) COLLATE Latin1_General_100_BIN2,p.permission_name COLLATE Latin1_General_100_BIN2,p.state COLLATE Latin1_General_100_BIN2 FROM sys.database_permissions p WHERE p.grantee_principal_id IN({role_ids}) EXCEPT SELECT RoleName,ObjectName,PermissionName,State FROM @C4bPermissions)
 OR EXISTS(SELECT RoleName,ObjectName,PermissionName,State FROM @C4bPermissions EXCEPT SELECT USER_NAME(p.grantee_principal_id) COLLATE Latin1_General_100_BIN2,OBJECT_NAME(p.major_id) COLLATE Latin1_General_100_BIN2,p.permission_name COLLATE Latin1_General_100_BIN2,p.state COLLATE Latin1_General_100_BIN2 FROM sys.database_permissions p WHERE p.grantee_principal_id IN({role_ids}))
 OR EXISTS(SELECT 1 FROM sys.database_permissions WHERE grantee_principal_id IN({role_ids}) AND (class<>1 OR minor_id<>0 OR grantor_principal_id<>1)) THROW 59500,'CIMD OAuth permission drift.',1;
IF EXISTS(SELECT 1 FROM sys.database_principals WHERE principal_id IN({role_ids}) AND (type<>'R' OR owning_principal_id<>1)) THROW 59500,'CIMD OAuth role ownership drift.',1;
DECLARE @C4bMembers table(Id int PRIMARY KEY);
INSERT @C4bMembers SELECT member_principal_id FROM sys.database_role_members WHERE role_principal_id IN({role_ids});
IF EXISTS(SELECT 1 FROM sys.database_role_members WHERE member_principal_id IN({role_ids}))
 OR EXISTS(SELECT 1 FROM sys.database_role_members WHERE member_principal_id IN(SELECT Id FROM @C4bMembers) AND role_principal_id NOT IN({role_ids}))
 OR EXISTS(SELECT member_principal_id FROM sys.database_role_members WHERE role_principal_id IN({role_ids}) GROUP BY member_principal_id HAVING COUNT(*)<>1)
 OR EXISTS(SELECT 1 FROM sys.database_principals p JOIN @C4bMembers m ON m.Id=p.principal_id WHERE p.type<>'S')
 OR EXISTS(SELECT 1 FROM sys.database_permissions WHERE grantee_principal_id IN(SELECT Id FROM @C4bMembers) AND NOT(class=0 AND state='G' AND permission_name='CONNECT'))
 OR EXISTS(SELECT 1 FROM sys.schemas WHERE principal_id IN(SELECT Id FROM @C4bMembers) OR principal_id IN({role_ids}))
 OR EXISTS(SELECT 1 FROM sys.objects WHERE principal_id IN(SELECT Id FROM @C4bMembers) OR principal_id IN({role_ids}))
 OR EXISTS(SELECT 1 FROM sys.crypt_properties WHERE major_id IN(SELECT OBJECT_ID('dbo.'+Name) FROM @C4bObjects))
 THROW 59500,'CIMD OAuth member authority drift.',1;
"""
verify+=f"""DECLARE @C4bEdition int=CONVERT(int,SERVERPROPERTY('EngineEdition'));
IF @C4bEdition IS NULL OR @C4bEdition NOT IN(2,3,4,5,8) THROW 59500,'Unknown OAuth SQL platform.',1;
IF EXISTS(SELECT 1 FROM sys.database_principals p JOIN @C4bMembers a ON a.Id=p.principal_id WHERE p.authentication_type<>CASE WHEN @C4bEdition=5 THEN 2 ELSE 1 END) THROW 59500,'CIMD authentication type drift.',1;
IF EXISTS(SELECT 1 FROM sys.database_principals WHERE owning_principal_id IN(SELECT Id FROM @C4bMembers) OR owning_principal_id IN({role_ids}))
 OR EXISTS(SELECT 1 FROM sys.types WHERE is_user_defined=1 AND (principal_id IN(SELECT Id FROM @C4bMembers) OR principal_id IN({role_ids})))
 OR EXISTS(SELECT 1 FROM sys.certificates WHERE principal_id IN(SELECT Id FROM @C4bMembers) OR principal_id IN({role_ids}))
 OR EXISTS(SELECT 1 FROM sys.asymmetric_keys WHERE principal_id IN(SELECT Id FROM @C4bMembers) OR principal_id IN({role_ids}))
 OR EXISTS(SELECT 1 FROM sys.assemblies WHERE principal_id IN(SELECT Id FROM @C4bMembers) OR principal_id IN({role_ids})) THROW 59500,'CIMD securable ownership drift.',1;
IF (SELECT COUNT(*) FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id WHERE OBJECT_NAME(p.target_object_id) IN('AiCimdAuthorizationV1','AiCimdKeyBindingV1','AiCimdAssertionReplayV1') AND sp.object_id=OBJECT_ID('sec.TenantFilter') AND sp.is_enabled=1 AND sp.is_schema_bound=1 AND p.predicate_definition COLLATE Latin1_General_100_BIN2=N'([sec].[fn_FilterByTenant]([ShaleClientId]))')<>9
 OR (SELECT COUNT(*) FROM sys.security_predicates WHERE OBJECT_NAME(target_object_id) IN('AiCimdAuthorizationV1','AiCimdKeyBindingV1','AiCimdAssertionReplayV1'))<>9 THROW 59500,'CIMD OAuth RLS drift.',1;
IF EXISTS(SELECT 1 FROM sys.triggers t WHERE OBJECT_NAME(t.parent_id) IN(SELECT Name FROM @C4bObjects) AND t.name NOT IN(SELECT Name FROM @C4bModules)) THROW 59500,'Unreviewed CIMD OAuth trigger.',1;
"""
server=f"""IF EXISTS(SELECT 1 FROM sys.database_principals u JOIN @C4bMembers a ON a.Id=u.principal_id LEFT JOIN sys.server_principals s ON s.sid=u.sid WHERE s.principal_id IS NULL OR s.type<>'S' OR s.is_disabled=1)
 OR EXISTS(SELECT 1 FROM sys.server_role_members r JOIN sys.server_principals s ON s.principal_id=r.member_principal_id JOIN sys.database_principals u ON u.sid=s.sid JOIN @C4bMembers a ON a.Id=u.principal_id)
 OR EXISTS(SELECT 1 FROM sys.server_permissions p JOIN sys.server_principals s ON s.principal_id=p.grantee_principal_id JOIN sys.database_principals u ON u.sid=s.sid JOIN @C4bMembers a ON a.Id=u.principal_id WHERE p.state IN('G','W') AND NOT(p.class=100 AND p.state='G' AND p.permission_name IN('CONNECT SQL','VIEW ANY DATABASE'))) THROW 59500,'CIMD server identity drift.',1;"""
# Dynamic batch cannot see the local table variable; derive the same membership set directly.
server=server.replace('JOIN @C4bMembers a ON a.Id=u.principal_id',f'JOIN sys.database_role_members a ON a.member_principal_id=u.principal_id AND a.role_principal_id IN({role_ids})')
verify+="IF @C4bEdition<>5 BEGIN DECLARE @C4bServerCheck nvarchar(max)=CONVERT(nvarchar(max),0x"+server.encode('utf-16le').hex()+"); EXEC sys.sp_executesql @C4bServerCheck; END;\n"
verify+=catalog+f"IF ISNULL((SELECT SourceHash FROM dbo.AiCimdOAuthReceiptV1 WHERE Id=1),0x)<>0x{source_hash} OR ISNULL((SELECT BeforeHash FROM dbo.AiCimdOAuthReceiptV1 WHERE Id=1),0x)<>@C4bBefore OR ISNULL((SELECT CatalogHash FROM dbo.AiCimdOAuthReceiptV1 WHERE Id=1),0x)<>@C4bFingerprint THROW 59500,'CIMD OAuth receipt drift.',1;\n"
install="""/* Generated PR 4B OFF candidate. Disposable acceptance is not production authorization. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR USER_NAME()<>'dbo' OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL THROW 59500,'Fresh unscoped dbo installation required.',1;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @C4bLock int; EXEC @C4bLock=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
IF @C4bLock<0 OR EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Enabled=1) THROW 59500,'Schema lock and capability OFF required.',1;
"""+pre+owned_sql
install+="IF EXISTS(SELECT 1 FROM @C4bObjects WHERE OBJECT_ID('dbo.'+Name) IS NOT NULL) OR EXISTS(SELECT 1 FROM sys.database_principals WHERE name IN("+','.join(literal(r) for r in roles)+")) THROW 59500,'CIMD OAuth objects already exist.',1;\n"
install+=oldstate+'DECLARE @C4bSaved binary(32)=@C4bBefore;\n'+source+pre+catalog
install+=f'INSERT dbo.AiCimdOAuthReceiptV1 VALUES(1,0x{source_hash},@C4bSaved,@C4bFingerprint);\n'+'/* C4b final independent seal verification */\n'+execute(verify)+'COMMIT TRANSACTION; END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;\n'
emit(SQL/'universal-pr4b-oauth.sql',install);emit(SQL/'universal-pr4b-oauth-verify.sql',verify)
emit(BUILD/'cimd-oauth-v1-modules.sha256',''.join(n+'='+hashlib.sha256(b.encode('utf-16le')).hexdigest()+'\n' for n,b in modules.items()))
emit(BUILD/'cimd-oauth-v1-review.sql','\nGO\n'.join(modules.values())+'\n')
print('CIMD OAuth source '+source_hash+'; modules='+str(len(modules)))
