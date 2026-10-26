from pathlib import Path
import re,hashlib,subprocess,tempfile
BUILD=Path(__file__).resolve().parent
REPO=BUILD.parent.parent
import os
os.chdir(REPO)
root=Path('docs/sql/ai-integration')
old=(root/'phase3b3.sql').read_text()
modules={}
def module(name, body): modules[name]='\n'.join(line.rstrip() for line in body.strip().splitlines())+'\n'
def extract(text,name):
 a=text.index("EXEC(N'CREATE PROCEDURE dbo."+name)
 b=text.index("\nEND;');",a)+len("\nEND;")
 return text[a+len("EXEC(N'"):b].replace("''", "'")
# The established validation predicate is retained; the new lock prefix/suffix and proof binding are independent.
legacy=extract(old,'AiGateCaseReadV1')
tuplecheck=legacy[legacy.index(' SELECT @mask='):legacy.index(' DECLARE @leaseExpiry')]
tuplecheck=tuplecheck.replace('WHERE c.Id=@AccessCredentialId', 'WHERE c.CredentialHash=@fixedDigest AND c.Id=@AccessCredentialId')
params='''@t int OUTPUT,@u int OUTPUT,@IntegrationId uniqueidentifier OUTPUT,@GrantId uniqueidentifier OUTPUT,@SessionId uniqueidentifier OUTPUT,
 @AccessCredentialId bigint OUTPUT,@ClientId varchar(64) OUTPUT,@KeyId uniqueidentifier OUTPUT,@Issuer nvarchar(256) OUTPUT,@Resource nvarchar(256) OUTPUT,
 @mask tinyint OUTPUT,@deadline datetime2(7) OUTPUT,@revision bigint OUTPUT,@policy bigint OUTPUT,@registration bigint OUTPUT,@grantRevision bigint OUTPUT'''
args='@t OUTPUT,@u OUTPUT,@IntegrationId OUTPUT,@GrantId OUTPUT,@SessionId OUTPUT,@AccessCredentialId OUTPUT,@ClientId OUTPUT,@KeyId OUTPUT,@Issuer OUTPUT,@Resource OUTPUT,@mask OUTPUT,@deadline OUTPUT,@revision OUTPUT,@policy OUTPUT,@registration OUTPUT,@grantRevision OUTPUT'
locals='''DECLARE @t int,@u int,@IntegrationId uniqueidentifier,@GrantId uniqueidentifier,@SessionId uniqueidentifier,@AccessCredentialId bigint,@ClientId varchar(64),@KeyId uniqueidentifier,
 @Issuer nvarchar(256),@Resource nvarchar(256),@mask tinyint,@deadline datetime2(7),@revision bigint,@policy bigint,@registration bigint,@grantRevision bigint;'''
module('AiDataLocateAccessOwnerV1', '''CREATE PROCEDURE dbo.AiDataLocateAccessOwnerV1 @ProofDigest varbinary(max),
 @t int OUTPUT,@u int OUTPUT,@IntegrationId uniqueidentifier OUTPUT,@GrantId uniqueidentifier OUTPUT,@SessionId uniqueidentifier OUTPUT,
 @AccessCredentialId bigint OUTPUT,@ClientId varchar(64) OUTPUT,@KeyId uniqueidentifier OUTPUT,@Issuer nvarchar(256) OUTPUT,@Resource nvarchar(256) OUTPUT
 WITH EXECUTE AS OWNER AS BEGIN
 SET NOCOUNT ON;
 IF USER_NAME()<>N'dbo' OR @ProofDigest IS NULL OR DATALENGTH(@ProofDigest)<>32 THROW 59101,'Integration authentication failed.',1;
 DECLARE @fixedDigest binary(32)=CONVERT(binary(32),@ProofDigest);
 SET @t=NULL; SET @u=NULL; SET @AccessCredentialId=NULL;
 SELECT @t=c.ShaleClientId,@u=c.UserId,@IntegrationId=c.IntegrationId,@GrantId=c.GrantId,@SessionId=c.SessionId,@AccessCredentialId=c.Id,
 @ClientId=s.ClientId,@KeyId=s.CredentialId,@Issuer=d.Issuer,@Resource=d.Resource
 FROM dbo.AiOAuthCredential c JOIN dbo.AiDelegatedSession s ON s.ShaleClientId=c.ShaleClientId AND s.UserId=c.UserId AND s.IntegrationId=c.IntegrationId AND s.GrantId=c.GrantId AND s.SessionId=c.SessionId
 JOIN dbo.AiAuthorizationTransaction a ON a.ShaleClientId=s.ShaleClientId AND a.TransactionId=s.TransactionId AND a.UserId=s.UserId AND a.IntegrationId=s.IntegrationId AND a.GrantId=s.GrantId
 JOIN dbo.AiIntegrationRegistration i ON i.ShaleClientId=s.ShaleClientId AND i.IntegrationId=s.IntegrationId
 JOIN dbo.AiCaseDataDeployment d ON d.Id=1 AND d.Contract='case-read-v1'
 WHERE c.CredentialHash=@fixedDigest AND c.Kind='ACCESS'
 AND s.ClientId=i.ClientId AND DATALENGTH(s.ClientId)=DATALENGTH(i.ClientId) AND s.ClientId=a.ClientId AND DATALENGTH(s.ClientId)=DATALENGTH(a.ClientId)
 AND s.Issuer=d.Issuer AND DATALENGTH(s.Issuer)=DATALENGTH(d.Issuer) AND i.Issuer=d.Issuer AND DATALENGTH(i.Issuer)=DATALENGTH(d.Issuer) AND a.Issuer=d.Issuer AND DATALENGTH(a.Issuer)=DATALENGTH(d.Issuer)
 AND s.Resource=d.Resource AND DATALENGTH(s.Resource)=DATALENGTH(d.Resource) AND i.Resource=d.Resource AND DATALENGTH(i.Resource)=DATALENGTH(d.Resource) AND a.Resource=d.Resource AND DATALENGTH(a.Resource)=DATALENGTH(d.Resource);
 IF @AccessCredentialId IS NULL OR @t IS NULL OR @u IS NULL THROW 59101,'Integration authentication failed.',1;
END;''')
locate='EXEC dbo.AiDataLocateAccessOwnerV1 @ProofDigest,@t OUTPUT,@u OUTPUT,@IntegrationId OUTPUT,@GrantId OUTPUT,@SessionId OUTPUT,@AccessCredentialId OUTPUT,@ClientId OUTPUT,@KeyId OUTPUT,@Issuer OUTPUT,@Resource OUTPUT;'
module('AiDataLoadAuthorityV1',f'''CREATE PROCEDURE dbo.AiDataLoadAuthorityV1 @ProofDigest varbinary(max),@InvocationId uniqueidentifier,@CorrelationId uniqueidentifier,@Live bit,@RequireInvocation bit,@RequiredScope tinyint,
 {params}
AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>1 OR @ProofDigest IS NULL OR DATALENGTH(@ProofDigest)<>32 OR @InvocationId IS NULL OR @CorrelationId IS NULL THROW 59101,'Integration authentication failed.',1;
 DECLARE @fixedDigest binary(32)=CONVERT(binary(32),@ProofDigest);
 {locate}
 EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=@t;
 EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=@u;
 IF @t<=0 OR @u<=0 OR ISNULL(TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),0)<>@t OR ISNULL(TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),0)<>@u THROW 59101,'Integration authentication failed.',1;
 DECLARE @rc int,@tenantLock nvarchar(255)=CONCAT(N'AiTenantV1:',@t),@held bigint,@transaction uniqueidentifier,@web uniqueidentifier;
 EXEC @rc=sys.sp_getapplock @Resource=N'AiCapabilityV1',@LockMode='Shared',@LockOwner='Transaction',@LockTimeout=3000;
 IF @rc<0 THROW 59101,'Integration authentication failed.',1;
 EXEC @rc=sys.sp_getapplock @Resource=@tenantLock,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
 IF @rc<0 THROW 59101,'Integration authentication failed.',1;
 -- Acquire every row explicitly in fixed order, BEFORE the validation join and UTC sample.
 SELECT @revision=Revision FROM dbo.AiProductCapability WITH(HOLDLOCK) WHERE Id=1;
 SELECT @policy=Revision FROM dbo.TenantAiIntegrationPolicy WITH(HOLDLOCK) WHERE ShaleClientId=@t;
 SELECT @registration=Revision FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@IntegrationId;
 SELECT @grantRevision=Revision FROM dbo.AiDelegationGrant WITH(HOLDLOCK) WHERE ShaleClientId=@t AND GrantId=@GrantId;
 SELECT @held=Id,@transaction=TransactionId FROM dbo.AiDelegatedSession WITH(HOLDLOCK) WHERE ShaleClientId=@t AND SessionId=@SessionId;
 SELECT @held=id FROM dbo.Users WITH(HOLDLOCK) WHERE ShaleClientId=@t AND id=@u;
 SELECT @held=Id FROM dbo.ShaleClients WITH(HOLDLOCK) WHERE Id=@t;
 SELECT @web=WebSessionId FROM dbo.AiAuthorizationTransaction WITH(HOLDLOCK) WHERE ShaleClientId=@t AND TransactionId=@transaction;
 SELECT @held=UserId FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND SessionId=@web;
 IF @KeyId IS NOT NULL SELECT @held=Id FROM dbo.AiIntegrationClientKey WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@IntegrationId AND CredentialId=@KeyId;
 SELECT @held=Id FROM dbo.AiOAuthCredential WITH(HOLDLOCK) WHERE Id=@AccessCredentialId;
 SELECT @held=AccessCredentialId FROM dbo.AiCaseDataInvocation WITH(UPDLOCK,HOLDLOCK) WHERE InvocationId=@InvocationId;
 SELECT @held=Id FROM dbo.AiQuotaReservation WITH(UPDLOCK,HOLDLOCK) WHERE InvocationId=@InvocationId;
 -- A legacy reservation with the same UUID is never ownership evidence for another lineage.
 IF EXISTS(SELECT 1 FROM dbo.AiQuotaReservation WHERE InvocationId=@InvocationId AND (ShaleClientId<>@t OR UserId<>@u OR IntegrationId<>@IntegrationId OR GrantId<>@GrantId OR SessionId<>@SessionId)) THROW 59101,'Integration authentication failed.',1;
 -- Repeat the exact proof/immutable mapping under the held authority locks.
 IF NOT EXISTS(SELECT 1 FROM dbo.AiOAuthCredential WHERE Id=@AccessCredentialId AND CredentialHash=@fixedDigest AND Kind='ACCESS' AND ShaleClientId=@t AND UserId=@u AND IntegrationId=@IntegrationId AND GrantId=@GrantId AND SessionId=@SessionId) THROW 59101,'Integration authentication failed.',1;
 IF EXISTS(SELECT 1 FROM dbo.AiCaseDataInvocation WHERE InvocationId=@InvocationId AND (ShaleClientId<>@t OR UserId<>@u OR AccessCredentialId<>@AccessCredentialId OR CorrelationId<>@CorrelationId OR IntegrationId<>@IntegrationId OR GrantId<>@GrantId OR SessionId<>@SessionId OR ClientId<>@ClientId OR DATALENGTH(ClientId)<>DATALENGTH(@ClientId) OR Issuer<>@Issuer OR DATALENGTH(Issuer)<>DATALENGTH(@Issuer) OR Resource<>@Resource OR DATALENGTH(Resource)<>DATALENGTH(@Resource))) THROW 59101,'Integration authentication failed.',1;
 IF @RequireInvocation=1 AND NOT EXISTS(SELECT 1 FROM dbo.AiCaseDataInvocation WHERE InvocationId=@InvocationId AND ShaleClientId=@t AND AccessCredentialId=@AccessCredentialId AND CorrelationId=@CorrelationId) THROW 59101,'Integration authentication failed.',1;
 DECLARE @PriorScopes tinyint=3,@keyStart datetime2(7);
 SELECT @PriorScopes=ScopeMask FROM dbo.AiCaseDataInvocation WHERE InvocationId=@InvocationId;
 SET @mask=NULL; SET @deadline=NULL;
 IF @Live=1 BEGIN
 {tuplecheck}
 DECLARE @now datetime2(7)=SYSUTCDATETIME();
 IF @mask IS NULL OR @mask=0 OR @deadline IS NULL OR @deadline<=@now OR (@keyStart IS NOT NULL AND @keyStart>@now)
 OR NOT EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Id=1 AND Enabled=1)
 OR NOT EXISTS(SELECT 1 FROM dbo.TenantAiIntegrationPolicy WHERE ShaleClientId=@t AND Enabled=1)
 OR (@RequiredScope<>0 AND (@mask & @RequiredScope)<>@RequiredScope)
 OR EXISTS(SELECT 1 FROM dbo.AiCaseDataInvocation WHERE InvocationId=@InvocationId AND (HistoricalOnly=1 OR ScopeMask<>@mask OR CapabilityRevision<>@revision OR PolicyRevision<>@policy OR IntegrationRevision<>@registration OR GrantRevision<>@grantRevision OR DeadlineUtc<=@now)) THROW 59101,'Integration authentication failed.',1;
 SELECT @deadline=CASE WHEN DeadlineUtc<@deadline THEN DeadlineUtc ELSE @deadline END FROM dbo.AiCaseDataInvocation WHERE InvocationId=@InvocationId;
 END ELSE BEGIN
  SELECT @mask=ScopeMask,@deadline=ExpiresAt FROM dbo.AiOAuthCredential WHERE Id=@AccessCredentialId;
 END;
END;''')
# Public transaction/input/context wrapper. Every public input validates binary length BEFORE conversion.
def public(name,extra,body):
 module(name,f'''CREATE PROCEDURE dbo.{name} @ProofDigest varbinary(max),@InvocationId uniqueidentifier,@CorrelationId uniqueidentifier{extra}
 AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 3000;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR @ProofDigest IS NULL OR DATALENGTH(@ProofDigest)<>32 OR @InvocationId IS NULL OR @CorrelationId IS NULL THROW 59101,'Integration authentication failed.',1;
 DECLARE @fixedDigest binary(32)=CONVERT(binary(32),@ProofDigest);
 DECLARE @FailureCode int=59101;
 DECLARE @oldTenant sql_variant=SESSION_CONTEXT(N'ShaleClientId'),@oldUser sql_variant=SESSION_CONTEXT(N'PrincipalUserId');
 {locals}
 BEGIN TRY
 {body}
 EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=@oldTenant;
 EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=@oldUser;
 END TRY BEGIN CATCH
  DECLARE @ErrorCode int=ERROR_NUMBER();
  IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
  EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=@oldTenant;
  EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=@oldUser;
  IF @ErrorCode BETWEEN 59101 AND 59109 OR @ErrorCode IN(1222,1205) THROW;
  THROW @FailureCode,'Protected operation unavailable.',1;
 END CATCH;
 END;''')
def load(live=1,req=1,scope=0): return f'EXEC dbo.AiDataLoadAuthorityV1 @ProofDigest,@InvocationId,@CorrelationId,{live},{req},{scope},{args};'
insertinv='''INSERT dbo.AiCaseDataInvocation(InvocationId,CorrelationId,ShaleClientId,UserId,IntegrationId,GrantId,SessionId,AccessCredentialId,ClientId,KeyId,Issuer,Resource,ScopeMask,CapabilityRevision,PolicyRevision,IntegrationRevision,GrantRevision,AdmittedAt,DeadlineUtc,HistoricalOnly)
 VALUES(@InvocationId,@CorrelationId,@t,@u,@IntegrationId,@GrantId,@SessionId,@AccessCredentialId,@ClientId,@KeyId,@Issuer,@Resource,@mask,COALESCE(@revision,0),COALESCE(@policy,0),COALESCE(@registration,0),COALESCE(@grantRevision,0),@now,@deadline,@historical);'''
public('AiDataAuthenticateV1',',@RequestDeadlineUtc datetime2(7)',f'''
 BEGIN TRANSACTION;
 {load(req=0)}
 DECLARE @now datetime2(7)=SYSUTCDATETIME(),@historical bit=0;
 IF @RequestDeadlineUtc IS NULL OR @RequestDeadlineUtc<=@now THROW 59101,'Integration authentication failed.',1;
 SELECT @deadline=MIN(v) FROM(VALUES(@deadline),(@RequestDeadlineUtc),(DATEADD(second,10,@now)))d(v);
 IF NOT EXISTS(SELECT 1 FROM dbo.AiCaseDataInvocation WHERE InvocationId=@InvocationId) BEGIN {insertinv} END
 ELSE UPDATE dbo.AiCaseDataInvocation SET DeadlineUtc=CASE WHEN @deadline<DeadlineUtc THEN @deadline ELSE DeadlineUtc END WHERE InvocationId=@InvocationId;
 COMMIT TRANSACTION;
 -- Only bounded identity metadata; no digest, key bytes, Case or inventory.
 SELECT @t ShaleClientId,@u UserId,@IntegrationId IntegrationId,@GrantId GrantId,@SessionId SessionId,@AccessCredentialId AccessCredentialId,@ClientId ClientId,@KeyId KeyId,
 @Issuer Issuer,@Resource Resource,@mask ScopeMask,@revision CapabilityRevision,@policy PolicyRevision,@registration IntegrationRevision,@grantRevision GrantRevision,@deadline Deadline,@InvocationId InvocationId,@CorrelationId CorrelationId;
''')
# Save working definitions; final generator appends data/audit/accounting modules and complete receipts.

module('AiDataAppendAccessV1', '''CREATE PROCEDURE dbo.AiDataAppendAccessV1
 @InvocationId uniqueidentifier,@Tool varchar(20),@Outcome varchar(14),@Reason varchar(20),@Page int,@Requested int,@Ids dbo.AiCaseDataIds READONLY,@EventId bigint OUTPUT
AS BEGIN
 SET NOCOUNT ON;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@stage varchar(8)='READ',@count int=(SELECT COUNT(*) FROM @Ids);
 IF @@TRANCOUNT<>1 OR @count>10 OR (@Outcome<>'ALLOWED_READ' AND @count<>0) OR (@Requested IS NOT NULL AND (@Tool<>'get_case_overview' OR @Outcome='ALLOWED_READ')) THROW 59103,'Required audit unavailable.',1;
 IF @Outcome<>'ALLOWED_READ' AND EXISTS(SELECT 1 FROM dbo.IntegrationAccessAuditLog WHERE InvocationId=@InvocationId AND Outcome='ALLOWED_READ' AND Stage='READ') SET @stage='TERMINAL';
 IF EXISTS(SELECT 1 FROM dbo.IntegrationAccessAuditLog WHERE InvocationId=@InvocationId AND Stage=@stage) BEGIN
  SELECT @EventId=Id FROM dbo.IntegrationAccessAuditLog WHERE InvocationId=@InvocationId AND ShaleClientId=@t AND UserId=@u AND Stage=@stage AND Tool=@Tool AND Outcome=@Outcome AND Reason=@Reason AND ReturnedCount=@count AND (SearchPage=@Page OR SearchPage IS NULL AND @Page IS NULL);
  IF @EventId IS NULL OR EXISTS(SELECT CaseId FROM dbo.IntegrationAccessAuditResource WHERE EventId=@EventId AND Relation='REQUESTED' EXCEPT SELECT @Requested WHERE @Requested IS NOT NULL)
   OR EXISTS(SELECT @Requested WHERE @Requested IS NOT NULL EXCEPT SELECT CaseId FROM dbo.IntegrationAccessAuditResource WHERE EventId=@EventId AND Relation='REQUESTED') THROW 59103,'Required audit unavailable.',1;
  RETURN;
 END;
 INSERT dbo.IntegrationAccessAuditLog(ShaleClientId,InvocationId,CorrelationId,UserId,IntegrationId,GrantId,SessionId,ClientId,Source,Contract,Tool,Operation,Outcome,Reason,Stage,ReturnedCount,SearchPage,SearchSize)
 SELECT ShaleClientId,InvocationId,CorrelationId,UserId,IntegrationId,GrantId,SessionId,ClientId,'MCP','case-read-v1',@Tool,
 CASE @Tool WHEN 'search_cases' THEN 'SEARCH_MINIMIZED_CASES' ELSE 'READ_MINIMIZED_CASE_OVERVIEW' END,@Outcome,@Reason,@stage,@count,@Page,CASE WHEN @Tool='search_cases' THEN 10 END
 FROM dbo.AiCaseDataInvocation WHERE InvocationId=@InvocationId AND ShaleClientId=@t AND UserId=@u;
 IF @@ROWCOUNT<>1 THROW 59103,'Required audit unavailable.',1;
 SET @EventId=SCOPE_IDENTITY();
 IF NOT EXISTS(SELECT 1 FROM dbo.IntegrationAccessAuditLog WHERE Id=@EventId AND InvocationId=@InvocationId AND Stage=@stage) THROW 59103,'Required audit unavailable.',1;
 INSERT dbo.IntegrationAccessAuditResource(ShaleClientId,EventId,CaseId,Relation)
 SELECT @t,@EventId,CaseId,'RETURNED' FROM @Ids UNION ALL SELECT @t,@EventId,@Requested,'REQUESTED' WHERE @Requested IS NOT NULL;
 IF (SELECT COUNT(*) FROM dbo.IntegrationAccessAuditResource WHERE ShaleClientId=@t AND EventId=@EventId)<>@count+CASE WHEN @Requested IS NULL THEN 0 ELSE 1 END THROW 59103,'Required audit unavailable.',1;
END;''')
# Quota math is the established aggregate model, now reachable only after digest validation.
quota=extract((root/'phase3b1.sql').read_text(),'AiReserveQuotaV1')
quota=quota[quota.index(' DECLARE @cost int='):quota.index(' SELECT @admit Admitted')]
module('AiDataAdmitV1',f'''CREATE PROCEDURE dbo.AiDataAdmitV1 @ProofDigest varbinary(max),@InvocationId uniqueidentifier,@CorrelationId uniqueidentifier,@Operation tinyint,@Page int,@CaseId int,@Admitted bit OUTPUT
AS BEGIN
 SET NOCOUNT ON;
 {locals}
 {load(scope='@Operation')}
 IF EXISTS(SELECT 1 FROM dbo.AiCaseDataInvocation WHERE InvocationId=@InvocationId AND (DispatchClaimed=1 OR ReadFailed=1 OR ReadEventId IS NOT NULL OR HandoffConsumed=1)) THROW 59101,'Integration authentication failed.',1;
 UPDATE dbo.AiCaseDataInvocation SET Operation=@Operation,SearchPage=@Page,RequestedCaseId=@CaseId,DispatchClaimed=1 WHERE InvocationId=@InvocationId;
 DECLARE @RequiredScope tinyint=@Operation,@integration uniqueidentifier=@IntegrationId,@grant uniqueidentifier=@GrantId,@now datetime2(7)=SYSUTCDATETIME();
 {quota.replace('@integration','@IntegrationId').replace('@grant','@GrantId')}
 SET @Admitted=@admit;
 {load(scope='@Operation')}
END;'''.replace('@integration uniqueidentifier=@IntegrationId,@grant uniqueidentifier=@GrantId,',''))
# Fixed projection retains existing status/assignment semantics. Ordinary DAO remains independently owned.
status=Path('shale-data/src/main/java/com/shale/data/dao/CaseSummaryDao.java').read_text()
status=status[status.index('private static String statusApplySql()'):]
status=status[status.index('"""')+3:status.index('""";')].strip()
assignment='''OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.ShaleClientId=c.ShaleClientId AND cu.RoleId=4 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) attorney
 OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName FROM dbo.Users u WHERE u.id=attorney.UserId AND u.ShaleClientId=c.ShaleClientId) attorney_user
 OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.ShaleClientId=c.ShaleClientId AND cu.RoleId=11 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) assistant
 OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName FROM dbo.Users u WHERE u.id=assistant.UserId AND u.ShaleClientId=c.ShaleClientId) assistant_user'''
fields='Id,CaseNumber,Name,StatusId,StatusName,StatusColor,PracticeAreaId,PracticeAreaName,ResponsibleAttorneyId,ResponsibleAttorneyName,PrimaryLegalAssistantId,PrimaryLegalAssistantName,UpdatedAt'
rowtable='''DECLARE @rows table(Ordinal int,Id int,CaseNumber nvarchar(max),Name nvarchar(max),StatusId int,StatusName nvarchar(max),StatusColor nvarchar(max),PracticeAreaId int,PracticeAreaName nvarchar(max),ResponsibleAttorneyId int,ResponsibleAttorneyName nvarchar(max),PrimaryLegalAssistantId int,PrimaryLegalAssistantName nvarchar(max),UpdatedAt datetime2(7));'''
projection=f'''SELECT ROW_NUMBER() OVER(ORDER BY c.Name,c.Id),c.Id,c.CaseNumber,COALESCE(c.Name,N''),status_row.StatusId,status_row.StatusName,status_row.StatusColor,pa.Id,pa.Name,attorney_user.id,attorney_user.DisplayName,assistant_user.id,assistant_user.DisplayName,c.UpdatedAt
 FROM dbo.Cases c {status}
 LEFT JOIN dbo.PracticeAreas pa ON pa.Id=c.PracticeAreaId AND (pa.ShaleClientId=c.ShaleClientId OR pa.ShaleClientId IS NULL)
 {assignment}
 WHERE c.ShaleClientId=@t AND ISNULL(c.IsDeleted,0)=0'''
# ROW_NUMBER before OFFSET yields global ordinal, subtract offset in returned-row selection.
for overview in (False,True):
 name='AiDataGetCaseOverviewV1' if overview else 'AiDataSearchCasesV1'
 scope=2 if overview else 1
 inputs=',@CaseId bigint' if overview else ',@NormalizedQuery nvarchar(max),@Page int'
 validate="IF @CaseId IS NULL OR @CaseId NOT BETWEEN 1 AND 2147483647 THROW 59102,'Invalid input.',1;" if overview else "DECLARE @pattern nvarchar(max)=dbo.AiDataNormalizeV1(@NormalizedQuery); IF @pattern IS NULL OR @Page IS NULL OR @Page NOT BETWEEN 0 AND 9 THROW 59102,'Invalid input.',1;"
 admit=f"EXEC dbo.AiDataAdmitV1 @ProofDigest,@InvocationId,@CorrelationId,{scope},{'NULL,@CaseId' if overview else '@Page,NULL'},@admitted OUTPUT;"
 query=projection+(" AND c.Id=@CaseId ORDER BY c.Name,c.Id OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY;" if overview else " AND LOWER(COALESCE(c.Name,N'')) LIKE @pattern ORDER BY c.Name,c.Id OFFSET (@Page*10) ROWS FETCH NEXT 11 ROWS ONLY;")
 phi=''
 if overview:
  phi='''IF NOT EXISTS(SELECT 1 FROM @rows) THROW 59104,'Case unavailable.',1;
 DECLARE @before bigint=(SELECT COUNT_BIG(*) FROM dbo.auditLog WITH(UPDLOCK,HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND ObjectTypeId=1 AND ObjectId=@CaseId AND FieldName='Case.Overview.Read' AND TRY_CONVERT(int,FieldCode)=4 AND StringValue='action=READ;screen=Case.Overview');
 DECLARE @zone sysname=(SELECT SqlTimeZone FROM dbo.AiCaseDataDeployment WHERE Id=1);
 INSERT dbo.auditLog(ShaleClientId,userId,objectTypeId,objectId,fieldName,fieldCode,stringValue,dateValue,booleanValue,intValue,entryDate)
 VALUES(@t,@u,1,@CaseId,'Case.Overview.Read',4,'action=READ;screen=Case.Overview',NULL,NULL,NULL,CONVERT(varchar(50),CONVERT(datetime2(7),(SYSUTCDATETIME() AT TIME ZONE 'UTC') AT TIME ZONE @zone),121));
 IF (SELECT COUNT_BIG(*) FROM dbo.auditLog WHERE ShaleClientId=@t AND UserId=@u AND ObjectTypeId=1 AND ObjectId=@CaseId AND FieldName='Case.Overview.Read' AND TRY_CONVERT(int,FieldCode)=4 AND StringValue='action=READ;screen=Case.Overview')<>@before+1 THROW 59103,'Required audit unavailable.',1;'''
 body=f'''DECLARE @admitted bit;
 BEGIN TRANSACTION;
 {load(scope=scope)}
 {validate}
 {admit}
 COMMIT TRANSACTION;
 IF @admitted<>1 THROW 59105,'Quota unavailable.',1;
 BEGIN TRANSACTION;
 {load(scope=scope)}
 DECLARE @lease datetime2(7);
 SELECT @lease=LeaseExpiresAt FROM dbo.AiQuotaReservation WHERE InvocationId=@InvocationId AND ShaleClientId=@t AND UserId=@u AND Admitted=1 AND ReleasedAt IS NULL AND RowCost={1 if overview else 10};
 IF @lease IS NULL OR @lease<=SYSUTCDATETIME() OR EXISTS(SELECT 1 FROM dbo.AiCaseDataInvocation WHERE InvocationId=@InvocationId AND (ReadFailed=1 OR ReadEventId IS NOT NULL OR HandoffConsumed=1 OR Operation<>{scope})) THROW 59101,'Integration authentication failed.',1;
 {rowtable}
 SET @FailureCode=59107;
 INSERT @rows {query}
 {'DELETE FROM @rows WHERE Ordinal>@Page*10+10;' if False else ''}
 -- Validate source widths before audits/COMMIT; never truncate names or transfer an oversized source.
 IF EXISTS(SELECT 1 FROM @rows WHERE DATALENGTH(CaseNumber)>400 OR DATALENGTH(Name)>510 OR DATALENGTH(StatusName)>510 OR DATALENGTH(StatusColor)>40 OR DATALENGTH(PracticeAreaName)>510 OR DATALENGTH(ResponsibleAttorneyName)>510 OR DATALENGTH(PrimaryLegalAssistantName)>510) THROW 59106,'Projection unavailable.',1;
 DECLARE @hasMore bit=CASE WHEN (SELECT COUNT(*) FROM @rows)>10 THEN 1 ELSE 0 END;
 {'DELETE FROM @rows WHERE Ordinal>@Page*10+10;' if not overview else ''}
 SET @FailureCode=59103;
 {phi}
 DECLARE @ids dbo.AiCaseDataIds,@event bigint;
 INSERT @ids SELECT Id FROM @rows;
 EXEC dbo.AiDataAppendAccessV1 @InvocationId,'{'get_case_overview' if overview else 'search_cases'}','ALLOWED_READ','NONE',{'NULL' if overview else '@Page'},NULL,@ids,@event OUTPUT;
 UPDATE dbo.AiCaseDataInvocation SET ReadEventId=@event WHERE InvocationId=@InvocationId;
 {load(scope=scope)}
 SET @FailureCode=59108;
 IF @lease<=SYSUTCDATETIME() THROW 59101,'Integration authentication failed.',1;
 COMMIT TRANSACTION;
 SELECT {fields},@InvocationId InvocationId,@CorrelationId CorrelationId,@event ReadEventId,@deadline Deadline,{'0' if overview else '@Page'} Page,10 Size,@hasMore HasMore,CONVERT(bit,CASE WHEN {'0' if overview else '@Page'}=9 AND @hasMore=1 THEN 1 ELSE 0 END) WindowExhausted FROM @rows ORDER BY Ordinal;
 -- Empty search still returns a separate bounded receipt, never a total or probe row.
 SELECT @InvocationId InvocationId,@CorrelationId CorrelationId,@event ReadEventId,@deadline Deadline,{'0' if overview else '@Page'} Page,10 Size,@hasMore HasMore,CONVERT(bit,CASE WHEN {'0' if overview else '@Page'}=9 AND @hasMore=1 THEN 1 ELSE 0 END) WindowExhausted;
'''
 public(name,inputs,body)
public('AiDataFinishCaseQuotaV1','',f'''
 BEGIN TRANSACTION;
 {load(live=0)}
 UPDATE dbo.AiQuotaReservation SET ReleasedAt=SYSUTCDATETIME() WHERE InvocationId=@InvocationId AND ShaleClientId=@t AND UserId=@u AND IntegrationId=@IntegrationId AND GrantId=@GrantId AND SessionId=@SessionId AND ReleasedAt IS NULL;
 COMMIT TRANSACTION;
 SELECT CONVERT(bit,1) Finished;
''')
public('AiDataAuthorizeCaseHandoffV1','',f'''
 BEGIN TRANSACTION;
 {load()}
 DECLARE @operation tinyint,@event bigint,@lease datetime2(7);
 SELECT @operation=Operation,@event=ReadEventId FROM dbo.AiCaseDataInvocation WHERE InvocationId=@InvocationId AND DispatchClaimed=1 AND ReadFailed=0 AND HandoffConsumed=0;
 SELECT @lease=LeaseExpiresAt FROM dbo.AiQuotaReservation WHERE InvocationId=@InvocationId AND ShaleClientId=@t AND UserId=@u AND Admitted=1 AND ReleasedAt IS NULL AND RowCost=CASE @operation WHEN 1 THEN 10 WHEN 2 THEN 1 ELSE -1 END;
 IF @operation IS NULL OR @event IS NULL OR @lease IS NULL OR (@mask & @operation)<>@operation
 OR NOT EXISTS(SELECT 1 FROM dbo.IntegrationAccessAuditLog WHERE Id=@event AND InvocationId=@InvocationId AND CorrelationId=@CorrelationId AND Stage='READ' AND Outcome='ALLOWED_READ') THROW 59101,'Integration authentication failed.',1;
 UPDATE dbo.AiQuotaReservation SET ReleasedAt=SYSUTCDATETIME() WHERE InvocationId=@InvocationId AND ShaleClientId=@t AND UserId=@u AND IntegrationId=@IntegrationId AND GrantId=@GrantId AND SessionId=@SessionId AND ReleasedAt IS NULL;
 IF @@ROWCOUNT<>1 THROW 59101,'Integration authentication failed.',1;
 UPDATE dbo.AiCaseDataInvocation SET HandoffConsumed=1 WHERE InvocationId=@InvocationId AND HandoffConsumed=0;
 IF @@ROWCOUNT<>1 THROW 59101,'Integration authentication failed.',1;
 -- Reauthenticate after every housekeeping/authority lock; no stale pre-wait clock can authorize.
 {load()}
 DECLARE @sample datetime2(7)=SYSUTCDATETIME(),@remaining bigint;
 IF @lease<@deadline SET @deadline=@lease;
 SET @remaining=DATEDIFF_BIG(millisecond,@sample,@deadline)-1;
 IF @remaining<1 THROW 59101,'Integration authentication failed.',1;
 COMMIT TRANSACTION;
 SELECT @InvocationId InvocationId,@CorrelationId CorrelationId,@mask ScopeMask,@sample DatabaseUtc,@deadline Deadline,@remaining RemainingMillis;
''')
public('AiDataRecordCaseFailureV1',',@Tool varchar(max),@Outcome varchar(max),@Reason varchar(max),@Page int=NULL,@RequestedCaseId bigint=NULL',f'''
 BEGIN TRANSACTION;
 {load(live=0,req=0)}
 IF @Tool IS NULL OR DATALENGTH(@Tool) NOT IN(12,17) OR DATALENGTH(@Tool)<>LEN(@Tool) OR @Tool COLLATE Latin1_General_100_BIN2 NOT IN('search_cases','get_case_overview')
 OR @Outcome IS NULL OR DATALENGTH(@Outcome)<>LEN(@Outcome) OR @Outcome COLLATE Latin1_General_100_BIN2 NOT IN('DENIED','INVALID_INPUT','UNAVAILABLE','THROTTLED','CANCELLED')
 OR @Reason IS NULL OR DATALENGTH(@Reason)<>LEN(@Reason) OR @Reason COLLATE Latin1_General_100_BIN2 NOT IN('AUTHORITY','INPUT','READ','AUDIT','COMMIT','CLOSE','SERIALIZATION','OVERSIZED','DEADLINE','QUOTA','CANCELLED','CASE_UNAVAILABLE')
 OR (@Page IS NOT NULL AND (@Tool<>'search_cases' OR @Page NOT BETWEEN 0 AND 9))
 OR (@RequestedCaseId IS NOT NULL AND (@Tool<>'get_case_overview' OR @RequestedCaseId NOT BETWEEN 1 AND 2147483647)) THROW 59102,'Invalid input.',1;
 DECLARE @now datetime2(7)=SYSUTCDATETIME(),@historical bit=1,@operation tinyint=CASE @Tool WHEN 'search_cases' THEN 1 ELSE 2 END;
 IF NOT EXISTS(SELECT 1 FROM dbo.AiCaseDataInvocation WHERE InvocationId=@InvocationId) BEGIN SET @deadline=@now; {insertinv} END;
 IF EXISTS(SELECT 1 FROM dbo.AiCaseDataInvocation WHERE InvocationId=@InvocationId AND Operation IS NOT NULL AND (Operation<>@operation OR ISNULL(SearchPage,-1)<>ISNULL(@Page,-1) OR ISNULL(RequestedCaseId,-1)<>ISNULL(@RequestedCaseId,-1))) THROW 59101,'Integration authentication failed.',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.AiQuotaReservation WHERE InvocationId=@InvocationId)
 INSERT dbo.AiQuotaReservation(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,InvocationId,Operation,RowCost,Admitted,AdmittedAt,LeaseExpiresAt)
 VALUES(@t,@IntegrationId,@u,@GrantId,@SessionId,@InvocationId,CASE @operation WHEN 1 THEN 'SEARCH' ELSE 'OVERVIEW' END,0,0,@now,DATEADD(second,15,@now));
 DECLARE @ids dbo.AiCaseDataIds,@event bigint;
 EXEC dbo.AiDataAppendAccessV1 @InvocationId,@Tool,@Outcome,@Reason,@Page,@RequestedCaseId,@ids,@event OUTPUT;
 UPDATE dbo.AiCaseDataInvocation SET ReadFailed=1,Operation=COALESCE(Operation,@operation),SearchPage=COALESCE(SearchPage,@Page),RequestedCaseId=COALESCE(RequestedCaseId,CONVERT(int,@RequestedCaseId)) WHERE InvocationId=@InvocationId;
 COMMIT TRANSACTION;
 SELECT @event FailureEventId;
'''.replace('DATALENGTH(@Tool) NOT IN(12,17)','DATALENGTH(@Tool) NOT IN(12,17)'))
# Canonical cumulative catalog expression, extending the prior receipt with new dependencies.
catalog=old[old.index("SET @catalog=@catalog+"):old.index('DECLARE @receipt')]
owned=re.search(r'DECLARE @Owned table.*?INSERT @Owned VALUES \(N\'ResolveAiAccessV1\'\),\(N\'AiValidateAccessV1\'\);',old,re.S).group(0)
owned+='\n'+re.search(r"INSERT @Owned VALUES \(N'IntegrationAccessAuditLog'\).*?;",old,re.S).group(0)
newnames=list(modules)+['AiCaseDataInvocation','AiCaseDataDeployment','AiDataUnicodeRange','AiDataUnicodeLower','AiDataWordTransition','AiDataNormalizeV1','AiDataImmutableInvocation','AiDataImmutableDeployment']
newowned='INSERT @Owned VALUES '+','.join("(N'%s')"%n for n in newnames)+';\n'
# Supplement the historical receipt grammar: column grants, execution identity, schema ownership, dependencies and server routes.
extra_catalog='''
SET @catalog=@catalog+(SELECT CONCAT(p.class,N':',p.major_id,N':',p.minor_id,N':',p.grantee_principal_id,N':',p.grantor_principal_id,N':',p.permission_name,N':',p.state,N';') AS [text()] FROM sys.database_permissions p WHERE p.grantee_principal_id IN(DATABASE_PRINCIPAL_ID(N'AiCaseDataRuntime'),DATABASE_PRINCIPAL_ID(N'AiCaseDataCatalogVerifier')) OR p.class=1 AND OBJECT_NAME(p.major_id) IN(SELECT Name FROM @Owned) ORDER BY p.class,p.major_id,p.minor_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(o.name,N':',m.execute_as_principal_id,N':',s.principal_id,N';') AS [text()] FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.name IN(SELECT Name FROM @Owned) ORDER BY o.name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(referencing_id),N':',referenced_server_name,N':',referenced_database_name,N':',referenced_schema_name,N':',referenced_entity_name,N';') AS [text()] FROM sys.sql_expression_dependencies WHERE OBJECT_NAME(referencing_id) IN(SELECT Name FROM @Owned) ORDER BY referencing_id,referenced_entity_name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N':',owning_principal_id) FROM sys.database_principals WHERE name=N'AiCaseDataRuntime'),N'MISSING');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N':',owning_principal_id) FROM sys.database_principals WHERE name=N'AiCaseDataCatalogVerifier'),N'MISSING');
SET @catalog=@catalog+(SELECT CONCAT(is_trustworthy_on,N':',is_db_chaining_on) FROM sys.databases WHERE database_id=DB_ID());
SET @catalog=@catalog+(SELECT CONCAT(StartCodepoint,N':',EndCodepoint,N':',Category,N':',Whitespace,N':',LetterDigit,N':',Cased,N';') AS [text()] FROM dbo.AiDataUnicodeRange ORDER BY StartCodepoint FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(Codepoint,N':',CONVERT(varchar(16),CONVERT(varbinary(8),LowerText),2),N';') AS [text()] FROM dbo.AiDataUnicodeLower ORDER BY Codepoint FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(StateId,N':',Category,N':',NextState,N':',IsEnd,N':',Lookahead,N';') AS [text()] FROM dbo.AiDataWordTransition ORDER BY StateId,Category FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
'''
# Generate a verifier used both at install/repeat and under separately governed catalog authority.
verify='''SET NOCOUNT ON;
/* Read-only live confinement catalog attestation. Requires complete VIEW DEFINITION. */
IF HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION')<>1 THROW 59100,'Complete catalog visibility required.',1;
'''+owned+'\n'+newowned+"DECLARE @catalog nvarchar(max)=N'';\n"+catalog+extra_catalog+'''
IF HASHBYTES('SHA2_256',@catalog)<>ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND minor_id=0 AND name=N'Shale.AiCaseDataV1'),0x)
 THROW 59100,'Confinement catalog drift or missing receipt.',1;
SELECT CAST(0 AS int) ConfinementCatalogDriftFindingCount;
'''

header='''/* Phase 3B.7 SQL confinement. Manual additive candidate, no login provisioning or enablement.
   Rollback: handlers/transport OFF, revoke AI connection/EXECUTEs, drain pools; retain history.
   Never restore shared-runtime fallback. Original source bytes and historical migrations are unchanged. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;
IF USER_NAME()<>N'dbo' OR @@TRANCOUNT<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL THROW 59100,'Fresh dbo migration session required.',1;
BEGIN TRY BEGIN TRANSACTION;
DECLARE @sourceHash varbinary(32)=0x'''+64*'0'+''';
DECLARE @rc int;
EXEC @rc=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
IF @rc<0 THROW 59100,'Schema lock unavailable.',1;
IF EXISTS(SELECT 1 FROM sys.databases WHERE database_id=DB_ID() AND (is_trustworthy_on=1 OR is_db_chaining_on=1)) THROW 59100,'Unsafe database ownership route.',1;
'''+owned+'''
DECLARE @catalog nvarchar(max)=N'';
'''
header+=catalog+'''
IF OBJECT_ID(N'dbo.AiCaseDataInvocation') IS NOT NULL BEGIN
'''+newowned+"SET @catalog=N'';\n"+catalog+extra_catalog+'''
 IF HASHBYTES('SHA2_256',@catalog)<>ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND minor_id=0 AND name=N'Shale.AiCaseDataV1'),0x)
 OR @sourceHash<>ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND minor_id=0 AND name=N'Shale.AiCaseDataV1.Source'),0x) THROW 59100,'Confinement catalog/source drift.',1;
 COMMIT TRANSACTION; RETURN;
END;
IF HASHBYTES('SHA2_256',@catalog)<>ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiDisclosureV1'),0x) THROW 59100,'Reviewed disclosure catalog required.',1;
IF ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiDisclosureV1.Source'),0x)<>0x6923b77be9c25417848a8b2ab48ff5d953626a2f52eba0245a17ba343c670474 THROW 59100,'Reviewed disclosure source required.',1;
IF DATABASE_PRINCIPAL_ID(N'AiCaseDataCatalogVerifier') IS NOT NULL OR DATABASE_PRINCIPAL_ID(N'AiCaseDataRuntime') IS NOT NULL OR EXISTS(SELECT 1 FROM sys.objects WHERE name LIKE N'AiData%' OR name LIKE N'AiCaseData%') OR TYPE_ID(N'dbo.AiCaseDataIds') IS NOT NULL THROW 59100,'Unreceipted confinement objects.',1;
IF EXISTS(SELECT 1 FROM (VALUES(N'Cases'),(N'Users'),(N'auditLog'),(N'CaseUsers'),(N'CaseStatuses'),(N'Statuses'),(N'PracticeAreas'))v(Name) WHERE OBJECT_ID(N'dbo.'+v.Name,N'U') IS NULL) THROW 59100,'Authoritative Case prerequisites missing.',1;
IF EXISTS(SELECT 1 FROM (VALUES(N'Cases'),(N'Users'),(N'auditLog'),(N'Statuses'))v(Name) WHERE NOT EXISTS(SELECT 1 FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id WHERE p.target_object_id=OBJECT_ID(N'dbo.'+v.Name) AND p.predicate_type=0 AND sp.is_enabled=1)) THROW 59100,'Reviewed Case RLS prerequisites missing.',1;
CREATE ROLE AiCaseDataRuntime AUTHORIZATION dbo;
CREATE ROLE AiCaseDataCatalogVerifier AUTHORIZATION dbo;
GRANT VIEW DEFINITION TO AiCaseDataCatalogVerifier;
GRANT SELECT ON OBJECT::sys.sql_expression_dependencies TO AiCaseDataCatalogVerifier;
CREATE TABLE dbo.AiCaseDataDeployment(
 Id tinyint NOT NULL PRIMARY KEY CHECK(Id=1),
 Issuer nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 Resource nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 Contract varchar(12) COLLATE Latin1_General_100_BIN2 NOT NULL CHECK(Contract='case-read-v1' AND DATALENGTH(Contract)=12),
 JavaTimeZone varchar(100) NOT NULL,SqlTimeZone sysname NOT NULL
);
-- Empty until separately governed deployment provisioning; cannot enable a tenant or global capability.
CREATE UNIQUE INDEX UX_AiDataCredentialOwner ON dbo.AiOAuthCredential(ShaleClientId,Id,SessionId,GrantId,IntegrationId,UserId);
CREATE TABLE dbo.AiCaseDataInvocation(
 InvocationId uniqueidentifier NOT NULL PRIMARY KEY, CorrelationId uniqueidentifier NOT NULL,
 ShaleClientId int NOT NULL,UserId int NOT NULL,IntegrationId uniqueidentifier NOT NULL,GrantId uniqueidentifier NOT NULL,SessionId uniqueidentifier NOT NULL,AccessCredentialId bigint NOT NULL,
 ClientId varchar(64) COLLATE Latin1_General_100_BIN2 NOT NULL,KeyId uniqueidentifier NULL,
 Issuer nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL,Resource nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 ScopeMask tinyint NOT NULL CHECK(ScopeMask BETWEEN 1 AND 3),CapabilityRevision bigint NOT NULL,PolicyRevision bigint NOT NULL,IntegrationRevision bigint NOT NULL,GrantRevision bigint NOT NULL,
 AdmittedAt datetime2(7) NOT NULL,DeadlineUtc datetime2(7) NOT NULL,HistoricalOnly bit NOT NULL,
 Operation tinyint NULL CHECK(Operation IN(1,2)),SearchPage int NULL CHECK(SearchPage BETWEEN 0 AND 9),RequestedCaseId int NULL CHECK(RequestedCaseId>0),
 DispatchClaimed bit NOT NULL DEFAULT(0),ReadFailed bit NOT NULL DEFAULT(0),ReadEventId bigint NULL,HandoffConsumed bit NOT NULL DEFAULT(0),
 FOREIGN KEY(ShaleClientId,UserId) REFERENCES dbo.Users(ShaleClientId,id),
 FOREIGN KEY(ShaleClientId,SessionId,GrantId,IntegrationId,UserId) REFERENCES dbo.AiDelegatedSession(ShaleClientId,SessionId,GrantId,IntegrationId,UserId),
 FOREIGN KEY(ShaleClientId,AccessCredentialId,SessionId,GrantId,IntegrationId,UserId) REFERENCES dbo.AiOAuthCredential(ShaleClientId,Id,SessionId,GrantId,IntegrationId,UserId),
 FOREIGN KEY(ShaleClientId,ReadEventId) REFERENCES dbo.IntegrationAccessAuditLog(ShaleClientId,Id)
);
CREATE INDEX IX_AiDataInvocationOwner ON dbo.AiCaseDataInvocation(ShaleClientId,UserId,AdmittedAt);
CREATE TYPE dbo.AiCaseDataIds AS TABLE(CaseId int NOT NULL PRIMARY KEY CHECK(CaseId>0));
CREATE TABLE dbo.AiDataUnicodeRange(StartCodepoint int NOT NULL PRIMARY KEY,EndCodepoint int NOT NULL,Category int NOT NULL,Whitespace bit NOT NULL,LetterDigit bit NOT NULL,Cased bit NOT NULL);
CREATE TABLE dbo.AiDataUnicodeLower(Codepoint int NOT NULL PRIMARY KEY,LowerText nvarchar(4) NOT NULL);
CREATE TABLE dbo.AiDataWordTransition(StateId int NOT NULL,Category int NOT NULL,NextState int NOT NULL,IsEnd bit NOT NULL,Lookahead bit NOT NULL,PRIMARY KEY(StateId,Category));
'''
immutable=['InvocationId','CorrelationId','ShaleClientId','UserId','IntegrationId','GrantId','SessionId','AccessCredentialId','ClientId','KeyId','Issuer','Resource','ScopeMask','CapabilityRevision','PolicyRevision','IntegrationRevision','GrantRevision','AdmittedAt','HistoricalOnly']
immcols=','.join(immutable)
module('AiDataImmutableInvocation',f'''CREATE TRIGGER dbo.AiDataImmutableInvocation ON dbo.AiCaseDataInvocation AFTER UPDATE,DELETE AS BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT {immcols} FROM deleted EXCEPT SELECT {immcols} FROM inserted) OR EXISTS(SELECT 1 FROM deleted d JOIN inserted i ON i.InvocationId=d.InvocationId WHERE i.DeadlineUtc>d.DeadlineUtc OR i.DispatchClaimed<d.DispatchClaimed OR i.ReadFailed<d.ReadFailed OR i.HandoffConsumed<d.HandoffConsumed OR d.ReadEventId IS NOT NULL AND (i.ReadEventId IS NULL OR i.ReadEventId<>d.ReadEventId) OR d.Operation IS NOT NULL AND (i.Operation IS NULL OR i.Operation<>d.Operation) OR d.SearchPage IS NOT NULL AND (i.SearchPage IS NULL OR i.SearchPage<>d.SearchPage) OR d.RequestedCaseId IS NOT NULL AND (i.RequestedCaseId IS NULL OR i.RequestedCaseId<>d.RequestedCaseId)) THROW 59100,'Immutable invocation binding.',1;
END;''')
module('AiDataImmutableDeployment', '''CREATE TRIGGER dbo.AiDataImmutableDeployment ON dbo.AiCaseDataDeployment AFTER UPDATE,DELETE AS BEGIN SET NOCOUNT ON; THROW 59100,'Deployment binding requires governed pool drain and a new migration.',1; END;''')
# Ensure receipts include immutable triggers added above.
normalizer=(BUILD/'normalize.sql').read_text()
module('AiDataNormalizeV1',normalizer)
with tempfile.TemporaryDirectory(prefix='shale-ai-unicode-') as scratch:
 subprocess.run(['javac','-d',scratch,str(BUILD/'GenerateUnicode.java')],check=True)
 unicode=subprocess.check_output(['java','--add-opens','java.base/sun.text=ALL-UNNAMED','--add-opens','java.base/java.lang=ALL-UNNAMED','-cp',scratch,'GenerateUnicode'],text=True)
# Install text is dynamic only for DDL batch isolation; no installed module has dynamic SQL.
create='\n'.join("EXEC(N'"+body.replace("'","''")+"');" for body in [modules['AiDataNormalizeV1']]+[v for k,v in modules.items() if k!='AiDataNormalizeV1'])
alltables=['Cases','Users','CaseUsers','CaseStatuses','Statuses','PracticeAreas','UserSessions','ShaleClients','auditLog','EntityActionAuditLog','GlobalControlPlaneAuditLog','AiProductCapability','TenantAiIntegrationPolicy','AiIntegrationRegistration','AiIntegrationRedirect','AiIntegrationClientKey','AiDelegationGrant','AiConsentIntent','AiDelegatedSession','AiOAuthCredential','AiQuotaReservation','AiAuthorizationTransaction','AiAssertionReplay','IntegrationAccessAuditLog','IntegrationAccessAuditResource','AiCaseDataInvocation','AiCaseDataDeployment','AiDataUnicodeRange','AiDataUnicodeLower','AiDataWordTransition']
fences='\n'.join('DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo.['+t+'] TO AiCaseDataRuntime;' for t in alltables)
legacyprocs=[]
for p in root.glob('*.sql'):
 if p.name in ('phase3b7-confinement.sql','verify.sql'):continue
 legacyprocs+=re.findall(r'GRANT EXECUTE ON OBJECT::dbo\.(\w+) TO (?:AiIntegrationRuntime|AiIntegrationBootstrap|AiCapabilityOperator)',p.read_text())
fences+='\n'+'\n'.join(f'DENY EXECUTE ON OBJECT::dbo.{n} TO AiCaseDataRuntime;' for n in sorted(set(legacyprocs)))
publicnames=['AiDataAuthenticateV1','AiDataSearchCasesV1','AiDataGetCaseOverviewV1','AiDataRecordCaseFailureV1','AiDataFinishCaseQuotaV1','AiDataAuthorizeCaseHandoffV1']
fences+='\n'+'\n'.join(f'GRANT SELECT ON OBJECT::dbo.{t} TO AiCaseDataCatalogVerifier;' for t in ['AiCaseDataDeployment','AiDataUnicodeRange','AiDataUnicodeLower','AiDataWordTransition'])
fences+='\n'+'\n'.join(f'GRANT EXECUTE ON OBJECT::dbo.{n} TO AiCaseDataRuntime;' for n in publicnames)
rls='''EXEC(N'ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiCaseDataInvocation, ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiCaseDataInvocation AFTER INSERT, ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiCaseDataInvocation AFTER UPDATE;');'''
footer='\n'+newowned+"SET @catalog=N'';\n"+catalog+extra_catalog+'''
DECLARE @hash varbinary(32)=HASHBYTES('SHA2_256',@catalog);
EXEC sys.sp_addextendedproperty @name=N'Shale.AiCaseDataV1',@value=@hash,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'AiCaseDataInvocation';
EXEC sys.sp_addextendedproperty @name=N'Shale.AiCaseDataV1.Source',@value=@sourceHash,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'AiCaseDataInvocation';
COMMIT TRANSACTION;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
'''
case_sql=(BUILD/'case-catalog.sql').read_text()
case_expected=(BUILD/'case-catalog.sha256').read_text().strip()
case_sql=case_sql[:case_sql.index("SELECT CONVERT(varchar(64),HASHBYTES")]+"IF HASHBYTES('SHA2_256',@caseCatalog)<>0x"+case_expected+" THROW 59100,'Authoritative Case/RLS catalog prerequisite drift.',1;\n"
header=header.replace("DECLARE @sourceHash",case_sql+"DECLARE @sourceHash",1)
verify="SET NOCOUNT ON;\n"+case_sql+verify
s=header+unicode+'\n'+create+'\n'+rls+'\n'+fences+footer
match=re.search(r'@sourceHash varbinary\(32\)=0x([0-9a-f]{64})',s)
hash=hashlib.sha256(s.encode()).hexdigest();s=s[:match.start(1)]+hash+s[match.end(1):]
(root/'phase3b7-confinement.sql').write_text(s)
verify+="\nIF ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND name=N'Shale.AiCaseDataV1.Source'),0x)<>0x"+hash+" THROW 59100,'Reviewed confinement source receipt missing or changed.',1;\n"
print('Generated migration:',len(s),'bytes;',len(modules),'modules;',len(set(legacyprocs)),'legacy fences')

resources=Path('shale-data/src/main/resources/ai');resources.mkdir(parents=True,exist_ok=True)
(resources/'confinement-catalog.sql').write_text(verify)
manifest={name:hashlib.sha256(body.encode('utf-16le')).hexdigest() for name,body in modules.items()}
manifest['Source']=hash
(resources/'confinement-modules.properties').write_text(''.join(k+'='+v+'\n' for k,v in manifest.items()))

(root/"phase3b7-confinement-verify.sql").write_text(verify)
existing=(root/'verify.sql').read_text()
marker='/* CONFINEMENT DISPATCH END */'
if marker in existing: existing=existing.split(marker,1)[1].lstrip('\n')
dispatch="/* Latest confinement receipt supersedes earlier catalog checks. Read-only, no installation. */\nIF OBJECT_ID(N'dbo.AiCaseDataInvocation') IS NOT NULL BEGIN\n EXEC(N'"+verify.replace("'","''")+"');\n RETURN;\nEND;\n"+marker+"\n"
(root/'verify.sql').write_text(dispatch+existing)
