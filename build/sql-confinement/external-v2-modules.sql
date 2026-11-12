CREATE PROCEDURE dbo.AiExternalDataLocateAccessOwnerV2 @ProofDigest varbinary(max),
 @t int OUTPUT,@u int OUTPUT,@IntegrationId uniqueidentifier OUTPUT,@GrantId uniqueidentifier OUTPUT,@SessionId uniqueidentifier OUTPUT,
 @AccessCredentialId bigint OUTPUT,@ClientId varchar(64) OUTPUT,@KeyId uniqueidentifier OUTPUT,@Issuer nvarchar(256) OUTPUT,@Resource nvarchar(256) OUTPUT
 WITH EXECUTE AS OWNER AS BEGIN
 SET NOCOUNT ON;
 IF USER_NAME()<>N'dbo' OR @ProofDigest IS NULL OR DATALENGTH(@ProofDigest)<>32 THROW 59101,'Integration authentication failed.',1;
 DECLARE @fixedDigest binary(32)=CONVERT(binary(32),@ProofDigest);
 SET @t=NULL; SET @u=NULL; SET @AccessCredentialId=NULL;
 SELECT @t=c.ShaleClientId,@u=c.UserId,@IntegrationId=c.IntegrationId,@GrantId=c.GrantId,@SessionId=c.SessionId,@AccessCredentialId=c.Id,
 @ClientId=s.ClientId,@KeyId=s.CredentialId,@Issuer=d.Issuer,@Resource=d.Resource
 FROM dbo.AiCimdCredentialV1 c JOIN dbo.AiDelegatedSession s ON s.ShaleClientId=c.ShaleClientId AND s.UserId=c.UserId AND s.IntegrationId=c.IntegrationId AND s.GrantId=c.GrantId AND s.SessionId=c.SessionId
 JOIN dbo.AiCimdFamilyV1 a ON a.ShaleClientId=s.ShaleClientId AND a.SessionId=s.SessionId AND s.TransactionId IS NULL AND a.UserId=s.UserId AND a.IntegrationId=s.IntegrationId AND a.GrantId=s.GrantId
 JOIN dbo.AiIntegrationRegistration i ON i.ShaleClientId=s.ShaleClientId AND i.IntegrationId=s.IntegrationId
 JOIN dbo.AiCaseDataDeployment d ON d.Id=1 AND d.Contract='case-read-v1'
 WHERE c.CredentialHash=@fixedDigest AND c.Kind='ACCESS'
 AND s.ClientId=i.ClientId AND DATALENGTH(s.ClientId)=DATALENGTH(i.ClientId) AND s.ClientId=a.ClientId AND DATALENGTH(s.ClientId)=DATALENGTH(a.ClientId)
 AND s.Issuer=d.Issuer AND DATALENGTH(s.Issuer)=DATALENGTH(d.Issuer) AND i.Issuer=d.Issuer AND DATALENGTH(i.Issuer)=DATALENGTH(d.Issuer) AND a.Issuer=d.Issuer AND DATALENGTH(a.Issuer)=DATALENGTH(d.Issuer)
 AND s.Resource=d.Resource AND DATALENGTH(s.Resource)=DATALENGTH(d.Resource) AND i.Resource=d.Resource AND DATALENGTH(i.Resource)=DATALENGTH(d.Resource) AND a.Resource=d.Resource AND DATALENGTH(a.Resource)=DATALENGTH(d.Resource);
 IF @AccessCredentialId IS NULL OR @t IS NULL OR @u IS NULL THROW 59101,'Integration authentication failed.',1;
END;

GO
CREATE PROCEDURE dbo.AiExternalDataLoadAuthorityV2 @ProofDigest varbinary(max),@InvocationId uniqueidentifier,@CorrelationId uniqueidentifier,@Live bit,@RequireInvocation bit,@RequiredScope tinyint,
 @t int OUTPUT,@u int OUTPUT,@IntegrationId uniqueidentifier OUTPUT,@GrantId uniqueidentifier OUTPUT,@SessionId uniqueidentifier OUTPUT,
 @AccessCredentialId bigint OUTPUT,@ClientId varchar(64) OUTPUT,@KeyId uniqueidentifier OUTPUT,@Issuer nvarchar(256) OUTPUT,@Resource nvarchar(256) OUTPUT,
 @mask tinyint OUTPUT,@deadline datetime2(7) OUTPUT,@revision bigint OUTPUT,@policy bigint OUTPUT,@registration bigint OUTPUT,@grantRevision bigint OUTPUT
AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>1 OR @ProofDigest IS NULL OR DATALENGTH(@ProofDigest)<>32 OR @InvocationId IS NULL OR @CorrelationId IS NULL THROW 59101,'Integration authentication failed.',1;
 DECLARE @fixedDigest binary(32)=CONVERT(binary(32),@ProofDigest);
 EXEC dbo.AiExternalDataLocateAccessOwnerV2 @ProofDigest,@t OUTPUT,@u OUTPUT,@IntegrationId OUTPUT,@GrantId OUTPUT,@SessionId OUTPUT,@AccessCredentialId OUTPUT,@ClientId OUTPUT,@KeyId OUTPUT,@Issuer OUTPUT,@Resource OUTPUT;
 EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=@t;
 EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=@u;
 IF @t<=0 OR @u<=0 OR ISNULL(TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),0)<>@t OR ISNULL(TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),0)<>@u THROW 59101,'Integration authentication failed.',1;
 DECLARE @rc int,@tenantLock nvarchar(255)=CONCAT(N'AiTenantV1:',@t),@held bigint,@transaction uniqueidentifier,@web uniqueidentifier;
 EXEC @rc=sys.sp_getapplock @Resource=N'AiCapabilityV1',@LockMode='Shared',@LockOwner='Transaction',@LockTimeout=3000;
 IF @rc<0 THROW 59101,'Integration authentication failed.',1;
 EXEC @rc=sys.sp_getapplock @Resource=@tenantLock,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
 IF @rc<0 THROW 59101,'Integration authentication failed.',1;
/* Inserted into the live loader after capability/tenant locks and before legacy authority rows.
   Publication holds its row through commit; decisions serialize on the existing tenant lock.
   Registry publication never waits for a tenant, so this adds no reverse lock edge. */
DECLARE @application uniqueidentifier,@approval uniqueidentifier,@cimdHeld bigint;
SELECT @application=ApplicationId,@approval=ApprovalId FROM dbo.AiCimdFamilyV1
 WHERE ShaleClientId=@t AND SessionId=@SessionId AND UserId=@u;
SELECT @cimdHeld=Revision FROM dbo.AiExternalApplication WITH(HOLDLOCK) WHERE ApplicationId=@application;
SELECT @cimdHeld=Id FROM dbo.AiTenantApplicationApproval WITH(HOLDLOCK) WHERE ShaleClientId=@t AND ApprovalId=@approval;
SELECT @cimdHeld=UserId FROM dbo.AiCimdFamilyV1 WITH(HOLDLOCK) WHERE ShaleClientId=@t AND SessionId=@SessionId;

 -- Acquire every row explicitly in fixed order, BEFORE the validation join and UTC sample.
 SELECT @revision=Revision FROM dbo.AiProductCapability WITH(HOLDLOCK) WHERE Id=1;
 SELECT @policy=Revision FROM dbo.TenantAiIntegrationPolicy WITH(HOLDLOCK) WHERE ShaleClientId=@t;
 SELECT @registration=Revision FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@IntegrationId;
 SELECT @grantRevision=Revision FROM dbo.AiDelegationGrant WITH(HOLDLOCK) WHERE ShaleClientId=@t AND GrantId=@GrantId;
 SELECT @held=Id,@transaction=TransactionId FROM dbo.AiDelegatedSession WITH(HOLDLOCK) WHERE ShaleClientId=@t AND SessionId=@SessionId;
 SELECT @held=id FROM dbo.Users WITH(HOLDLOCK) WHERE ShaleClientId=@t AND id=@u;
 SELECT @held=Id FROM dbo.ShaleClients WITH(HOLDLOCK) WHERE Id=@t;
 SELECT @web=WebSessionId FROM dbo.AiCimdFamilyV1 WITH(HOLDLOCK) WHERE ShaleClientId=@t AND SessionId=@SessionId;
 SELECT @held=UserId FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND SessionId=@web;
 IF @KeyId IS NOT NULL SELECT @held=Id FROM dbo.AiIntegrationClientKey WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@IntegrationId AND CredentialId=@KeyId;
 SELECT @held=Id FROM dbo.AiCimdCredentialV1 WITH(HOLDLOCK) WHERE Id=@AccessCredentialId;
 SELECT @held=AccessCredentialId FROM dbo.AiCimdInvocationV1 WITH(UPDLOCK,HOLDLOCK) WHERE InvocationId=@InvocationId;
 SELECT @held=Id FROM dbo.AiQuotaReservation WITH(UPDLOCK,HOLDLOCK) WHERE InvocationId=@InvocationId;
 -- A legacy reservation with the same UUID is never ownership evidence for another lineage.
 IF EXISTS(SELECT 1 FROM dbo.AiQuotaReservation WHERE InvocationId=@InvocationId AND (ShaleClientId<>@t OR UserId<>@u OR IntegrationId<>@IntegrationId OR GrantId<>@GrantId OR SessionId<>@SessionId)) THROW 59101,'Integration authentication failed.',1;
 -- Repeat the exact proof/immutable mapping under the held authority locks.
 IF NOT EXISTS(SELECT 1 FROM dbo.AiCimdCredentialV1 WHERE Id=@AccessCredentialId AND CredentialHash=@fixedDigest AND Kind='ACCESS' AND ShaleClientId=@t AND UserId=@u AND IntegrationId=@IntegrationId AND GrantId=@GrantId AND SessionId=@SessionId) THROW 59101,'Integration authentication failed.',1;
 IF EXISTS(SELECT 1 FROM dbo.AiCimdInvocationV1 WHERE InvocationId=@InvocationId AND (ShaleClientId<>@t OR UserId<>@u OR AccessCredentialId<>@AccessCredentialId OR CorrelationId<>@CorrelationId OR IntegrationId<>@IntegrationId OR GrantId<>@GrantId OR SessionId<>@SessionId OR ClientId<>@ClientId OR DATALENGTH(ClientId)<>DATALENGTH(@ClientId) OR Issuer<>@Issuer OR DATALENGTH(Issuer)<>DATALENGTH(@Issuer) OR Resource<>@Resource OR DATALENGTH(Resource)<>DATALENGTH(@Resource))) THROW 59101,'Integration authentication failed.',1;
 IF @RequireInvocation=1 AND NOT EXISTS(SELECT 1 FROM dbo.AiCimdInvocationV1 WHERE InvocationId=@InvocationId AND ShaleClientId=@t AND AccessCredentialId=@AccessCredentialId AND CorrelationId=@CorrelationId) THROW 59101,'Integration authentication failed.',1;
 DECLARE @PriorScopes tinyint=3,@keyStart datetime2(7);
 SELECT @PriorScopes=ScopeMask FROM dbo.AiCimdInvocationV1 WHERE InvocationId=@InvocationId;
 SET @mask=NULL; SET @deadline=NULL;
 IF @Live=1 BEGIN
  SELECT @mask=(c.ScopeMask & s.ScopeMask & g.ScopeMask & i.ScopeMask & @PriorScopes & 3),@deadline=d.Deadline,@keyStart=k.ValidFrom
 FROM dbo.AiCimdCredentialV1 c WITH(HOLDLOCK)
 JOIN dbo.AiDelegatedSession s WITH(HOLDLOCK) ON s.ShaleClientId=c.ShaleClientId AND s.UserId=c.UserId AND s.IntegrationId=c.IntegrationId AND s.GrantId=c.GrantId AND s.SessionId=c.SessionId
 JOIN dbo.AiDelegationGrant g WITH(HOLDLOCK) ON g.ShaleClientId=s.ShaleClientId AND g.UserId=s.UserId AND g.IntegrationId=s.IntegrationId AND g.GrantId=s.GrantId
 JOIN dbo.AiIntegrationRegistration i WITH(HOLDLOCK) ON i.ShaleClientId=s.ShaleClientId AND i.IntegrationId=s.IntegrationId
 JOIN dbo.AiCimdFamilyV1 a WITH(HOLDLOCK) ON a.ShaleClientId=s.ShaleClientId AND a.SessionId=s.SessionId AND s.TransactionId IS NULL AND a.UserId=s.UserId AND a.IntegrationId=s.IntegrationId AND a.GrantId=s.GrantId
 JOIN dbo.UserSessions w WITH(HOLDLOCK) ON w.ShaleClientId=s.ShaleClientId AND w.UserId=s.UserId AND w.SessionId=a.WebSessionId
 JOIN dbo.Users u WITH(HOLDLOCK) ON u.ShaleClientId=s.ShaleClientId AND u.id=s.UserId
 JOIN dbo.ShaleClients t WITH(HOLDLOCK) ON t.Id=s.ShaleClientId
 LEFT JOIN dbo.AiIntegrationClientKey k WITH(HOLDLOCK) ON k.ShaleClientId=s.ShaleClientId AND k.IntegrationId=s.IntegrationId AND k.CredentialId=s.CredentialId
 CROSS APPLY(SELECT MIN(v.Expiry) Deadline FROM(VALUES(c.ExpiresAt),(s.ExpiresAt),(s.IdleExpiresAt),(g.ExpiresAt),(i.ExpiresAt),(w.ExpiresAt),(COALESCE(k.ExpiresAt,s.ExpiresAt)))v(Expiry))d
 WHERE c.CredentialHash=@fixedDigest AND c.Id=@AccessCredentialId AND c.ShaleClientId=@t AND c.UserId=@u AND c.IntegrationId=@IntegrationId AND c.GrantId=@GrantId AND c.SessionId=@SessionId
 AND c.Kind='ACCESS' AND c.RevokedAt IS NULL AND c.ConsumedAt IS NULL
 AND s.RevokedAt IS NULL AND s.CapabilityRevision=@revision AND s.ConsentAt=g.ConsentedAt AND c.Generation<=s.RefreshGeneration
 AND g.RevokedAt IS NULL AND i.Status='SUSPENDED' AND i.Revision=1 AND i.RevokedAt IS NULL
 AND COALESCE(u.is_deleted,0)=0 AND COALESCE(u.IsRemoved,0)=0 AND t.IsActive=1
 AND w.ClientType='WEB' AND w.RevokedAt IS NULL AND a.ConsumedAt IS NOT NULL AND a.DeniedAt IS NULL
 AND s.ClientId=@ClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.ClientId)=DATALENGTH(@ClientId)
 AND s.ClientId=i.ClientId AND DATALENGTH(s.ClientId)=DATALENGTH(i.ClientId) AND s.ClientId=a.ClientId AND DATALENGTH(s.ClientId)=DATALENGTH(a.ClientId)
 AND s.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.Issuer)=DATALENGTH(@Issuer)
 AND s.Issuer=i.Issuer AND DATALENGTH(s.Issuer)=DATALENGTH(i.Issuer) AND s.Issuer=a.Issuer AND DATALENGTH(s.Issuer)=DATALENGTH(a.Issuer)
 AND s.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.Resource)=DATALENGTH(@Resource)
 AND s.Resource=i.Resource AND DATALENGTH(s.Resource)=DATALENGTH(i.Resource) AND s.Resource=a.Resource AND DATALENGTH(s.Resource)=DATALENGTH(a.Resource)
 AND ((i.ClientType='PUBLIC' AND s.CredentialId IS NULL AND @KeyId IS NULL AND a.CredentialId IS NULL)
 OR (i.ClientType='CONFIDENTIAL' AND s.CredentialId=@KeyId AND s.CredentialId=a.CredentialId AND k.RevokedAt IS NULL AND k.Algorithm='RS256'));


 -- Same durable exact application/approval tuple is checked at admission, read and final handoff.
 DECLARE @approvalDeadline datetime2(7),@approvalMask tinyint;
 SELECT @approvalDeadline=p.ExpiresAt,@approvalMask=p.ScopeMask
 FROM dbo.AiCimdFamilyV1 f
 JOIN dbo.AiExternalApplication e ON e.ApplicationId=f.ApplicationId
 JOIN dbo.AiTenantApplicationApproval p ON p.ShaleClientId=f.ShaleClientId AND p.ApprovalId=f.ApprovalId
 WHERE f.ShaleClientId=@t AND f.SessionId=@SessionId AND f.UserId=@u AND f.IntegrationId=@IntegrationId AND f.GrantId=@GrantId
 AND p.ApplicationId=f.ApplicationId AND p.ApplicationRevision=f.ApplicationRevision AND p.IntegrationId=f.IntegrationId
 AND p.RowVer=f.ApprovalVersion AND p.State='APPROVED' AND p.ClosedAt IS NULL
 AND e.Revision=f.ApplicationRevision AND e.MetadataDigest=f.MetadataDigest AND e.Disabled=0 AND (e.Source='CIMD' OR (e.Source='DCR' AND EXISTS(SELECT 1 FROM dbo.AiDcrMetadataV2 dm WHERE dm.ApplicationId=e.ApplicationId AND dm.ApplicationRevision=e.Revision AND dm.MetadataDigest=e.MetadataDigest) AND EXISTS(SELECT 1 FROM dbo.AiExternalControlV2 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1)))
 AND e.ExternalClientId=f.ExternalClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.ExternalClientId)=DATALENGTH(f.ExternalClientId)
 AND e.Issuer=f.Issuer AND DATALENGTH(e.Issuer)=DATALENGTH(f.Issuer) AND e.Resource=f.Resource AND DATALENGTH(e.Resource)=DATALENGTH(f.Resource)
 AND ((f.AuthMethod='none' AND f.CredentialId IS NULL AND (e.MethodMask & 1)=1) OR
      (f.AuthMethod='private_key_jwt' AND f.CredentialId=@KeyId AND (e.MethodMask & 2)=2))
 AND EXISTS(SELECT 1 FROM dbo.AiCimdProofControlV1 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1);
 IF EXISTS(SELECT 1 FROM dbo.AiExternalApplication e JOIN dbo.AiDcrMetadataV2 ds ON ds.ApplicationId=e.ApplicationId AND ds.ApplicationRevision=e.Revision WHERE e.ApplicationId=@application AND e.Source='DCR' AND (ds.ScopeMask & @mask)<>@mask) THROW 59101,'DCR scope ceiling exceeded.',1;
 IF @approvalDeadline IS NULL OR @approvalMask IS NULL OR (@mask & @approvalMask)<>@mask
 THROW 59101,'Integration authentication failed.',1;
 IF @approvalDeadline<@deadline SET @deadline=@approvalDeadline;
 DECLARE @now datetime2(7)=SYSUTCDATETIME();
 IF @mask IS NULL OR @mask=0 OR @deadline IS NULL OR @deadline<=@now OR (@keyStart IS NOT NULL AND @keyStart>@now)
 OR NOT EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Id=1 AND Enabled=1)
 OR NOT EXISTS(SELECT 1 FROM dbo.TenantAiIntegrationPolicy WHERE ShaleClientId=@t AND Enabled=1)
 OR (@RequiredScope<>0 AND (@mask & @RequiredScope)<>@RequiredScope)
 OR EXISTS(SELECT 1 FROM dbo.AiCimdInvocationV1 WHERE InvocationId=@InvocationId AND (HistoricalOnly=1 OR ScopeMask<>@mask OR CapabilityRevision<>@revision OR PolicyRevision<>@policy OR IntegrationRevision<>@registration OR GrantRevision<>@grantRevision OR DeadlineUtc<=@now)) THROW 59101,'Integration authentication failed.',1;
 SELECT @deadline=CASE WHEN DeadlineUtc<@deadline THEN DeadlineUtc ELSE @deadline END FROM dbo.AiCimdInvocationV1 WHERE InvocationId=@InvocationId;
 END ELSE BEGIN
  SELECT @mask=ScopeMask,@deadline=ExpiresAt FROM dbo.AiCimdCredentialV1 WHERE Id=@AccessCredentialId;
 END;
END;

GO
CREATE PROCEDURE dbo.AiExternalDataAuthenticateV2 @ProofDigest varbinary(max),@InvocationId uniqueidentifier,@CorrelationId uniqueidentifier,@RequestDeadlineUtc datetime2(7)
 AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 3000;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR @ProofDigest IS NULL OR DATALENGTH(@ProofDigest)<>32 OR @InvocationId IS NULL OR @CorrelationId IS NULL THROW 59101,'Integration authentication failed.',1;
 DECLARE @fixedDigest binary(32)=CONVERT(binary(32),@ProofDigest);
 DECLARE @FailureCode int=59101;
 DECLARE @oldTenant sql_variant=SESSION_CONTEXT(N'ShaleClientId'),@oldUser sql_variant=SESSION_CONTEXT(N'PrincipalUserId');
 DECLARE @t int,@u int,@IntegrationId uniqueidentifier,@GrantId uniqueidentifier,@SessionId uniqueidentifier,@AccessCredentialId bigint,@ClientId varchar(64),@KeyId uniqueidentifier,
 @Issuer nvarchar(256),@Resource nvarchar(256),@mask tinyint,@deadline datetime2(7),@revision bigint,@policy bigint,@registration bigint,@grantRevision bigint;
 BEGIN TRY

 BEGIN TRANSACTION;
 EXEC dbo.AiExternalDataLoadAuthorityV2 @ProofDigest,@InvocationId,@CorrelationId,1,0,0,@t OUTPUT,@u OUTPUT,@IntegrationId OUTPUT,@GrantId OUTPUT,@SessionId OUTPUT,@AccessCredentialId OUTPUT,@ClientId OUTPUT,@KeyId OUTPUT,@Issuer OUTPUT,@Resource OUTPUT,@mask OUTPUT,@deadline OUTPUT,@revision OUTPUT,@policy OUTPUT,@registration OUTPUT,@grantRevision OUTPUT;
 DECLARE @now datetime2(7)=SYSUTCDATETIME(),@historical bit=0;
 IF @RequestDeadlineUtc IS NULL OR @RequestDeadlineUtc<=@now THROW 59101,'Integration authentication failed.',1;
 SELECT @deadline=MIN(v) FROM(VALUES(@deadline),(@RequestDeadlineUtc),(DATEADD(second,10,@now)))d(v);
 IF NOT EXISTS(SELECT 1 FROM dbo.AiCimdInvocationV1 WHERE InvocationId=@InvocationId) BEGIN INSERT dbo.AiCimdInvocationV1(InvocationId,CorrelationId,ShaleClientId,UserId,IntegrationId,GrantId,SessionId,AccessCredentialId,ClientId,KeyId,Issuer,Resource,ScopeMask,CapabilityRevision,PolicyRevision,IntegrationRevision,GrantRevision,AdmittedAt,DeadlineUtc,HistoricalOnly)
 VALUES(@InvocationId,@CorrelationId,@t,@u,@IntegrationId,@GrantId,@SessionId,@AccessCredentialId,@ClientId,@KeyId,@Issuer,@Resource,@mask,COALESCE(@revision,0),COALESCE(@policy,0),COALESCE(@registration,0),COALESCE(@grantRevision,0),@now,@deadline,@historical); END
 ELSE UPDATE dbo.AiCimdInvocationV1 SET DeadlineUtc=CASE WHEN @deadline<DeadlineUtc THEN @deadline ELSE DeadlineUtc END WHERE InvocationId=@InvocationId;
 COMMIT TRANSACTION;
 -- Only bounded identity metadata; no digest, key bytes, Case or inventory.
 SELECT @t ShaleClientId,@u UserId,@IntegrationId IntegrationId,@GrantId GrantId,@SessionId SessionId,@AccessCredentialId AccessCredentialId,@ClientId ClientId,@KeyId KeyId,
 @Issuer Issuer,@Resource Resource,@mask ScopeMask,@revision CapabilityRevision,@policy PolicyRevision,@registration IntegrationRevision,@grantRevision GrantRevision,@deadline Deadline,@InvocationId InvocationId,@CorrelationId CorrelationId;

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
 END;

GO
CREATE PROCEDURE dbo.AiExternalDataAppendAccessV2
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
 FROM dbo.AiCimdInvocationV1 WHERE InvocationId=@InvocationId AND ShaleClientId=@t AND UserId=@u;
 IF @@ROWCOUNT<>1 THROW 59103,'Required audit unavailable.',1;
 SET @EventId=SCOPE_IDENTITY();
 IF NOT EXISTS(SELECT 1 FROM dbo.IntegrationAccessAuditLog WHERE Id=@EventId AND InvocationId=@InvocationId AND Stage=@stage) THROW 59103,'Required audit unavailable.',1;
 INSERT dbo.IntegrationAccessAuditResource(ShaleClientId,EventId,CaseId,Relation)
 SELECT @t,@EventId,CaseId,'RETURNED' FROM @Ids UNION ALL SELECT @t,@EventId,@Requested,'REQUESTED' WHERE @Requested IS NOT NULL;
 IF (SELECT COUNT(*) FROM dbo.IntegrationAccessAuditResource WHERE ShaleClientId=@t AND EventId=@EventId)<>@count+CASE WHEN @Requested IS NULL THEN 0 ELSE 1 END THROW 59103,'Required audit unavailable.',1;
END;

GO
CREATE PROCEDURE dbo.AiExternalDataAdmitV2 @ProofDigest varbinary(max),@InvocationId uniqueidentifier,@CorrelationId uniqueidentifier,@Operation tinyint,@Page int,@CaseId int,@Admitted bit OUTPUT
AS BEGIN
 SET NOCOUNT ON;
 DECLARE @t int,@u int,@IntegrationId uniqueidentifier,@GrantId uniqueidentifier,@SessionId uniqueidentifier,@AccessCredentialId bigint,@ClientId varchar(64),@KeyId uniqueidentifier,
 @Issuer nvarchar(256),@Resource nvarchar(256),@mask tinyint,@deadline datetime2(7),@revision bigint,@policy bigint,@registration bigint,@grantRevision bigint;
 EXEC dbo.AiExternalDataLoadAuthorityV2 @ProofDigest,@InvocationId,@CorrelationId,1,1,@Operation,@t OUTPUT,@u OUTPUT,@IntegrationId OUTPUT,@GrantId OUTPUT,@SessionId OUTPUT,@AccessCredentialId OUTPUT,@ClientId OUTPUT,@KeyId OUTPUT,@Issuer OUTPUT,@Resource OUTPUT,@mask OUTPUT,@deadline OUTPUT,@revision OUTPUT,@policy OUTPUT,@registration OUTPUT,@grantRevision OUTPUT;
 IF EXISTS(SELECT 1 FROM dbo.AiCimdInvocationV1 WHERE InvocationId=@InvocationId AND (DispatchClaimed=1 OR ReadFailed=1 OR ReadEventId IS NOT NULL OR HandoffConsumed=1)) THROW 59101,'Integration authentication failed.',1;
 UPDATE dbo.AiCimdInvocationV1 SET Operation=@Operation,SearchPage=@Page,RequestedCaseId=@CaseId,DispatchClaimed=1 WHERE InvocationId=@InvocationId;
 DECLARE @RequiredScope tinyint=@Operation,@now datetime2(7)=SYSUTCDATETIME();
  DECLARE @cost int=CASE WHEN @RequiredScope=1 THEN 10 ELSE 1 END,@admit bit=1;
 IF EXISTS(SELECT 1 FROM dbo.AiQuotaReservation WHERE InvocationId=@InvocationId) THROW 58407,'AI reservation already accounted.',1;
 SELECT @admit=CASE WHEN
  COALESCE(SUM(CONVERT(int,RowCost)),0)+@cost>1000 OR
  COALESCE(SUM(CASE WHEN UserId=@u THEN CONVERT(int,RowCost) ELSE 0 END),0)+@cost>200 OR
  COALESCE(SUM(CASE WHEN IntegrationId=@IntegrationId THEN CONVERT(int,RowCost) ELSE 0 END),0)+@cost>500 OR
  COUNT(CASE WHEN AdmittedAt>DATEADD(second,-60,@now) THEN 1 END)>=60 OR
  COUNT(CASE WHEN AdmittedAt>DATEADD(second,-60,@now) AND UserId=@u THEN 1 END)>=10 OR
  COUNT(CASE WHEN AdmittedAt>DATEADD(second,-60,@now) AND IntegrationId=@IntegrationId THEN 1 END)>=20 OR
  COUNT(CASE WHEN Admitted=1 AND ReleasedAt IS NULL AND LeaseExpiresAt>@now THEN 1 END)>=5 OR
  COUNT(CASE WHEN Admitted=1 AND ReleasedAt IS NULL AND LeaseExpiresAt>@now AND UserId=@u THEN 1 END)>=1 OR
  COUNT(CASE WHEN Admitted=1 AND ReleasedAt IS NULL AND LeaseExpiresAt>@now AND IntegrationId=@IntegrationId THEN 1 END)>=2
 THEN 0 ELSE 1 END FROM dbo.AiQuotaReservation WHERE ShaleClientId=@t AND AdmittedAt>DATEADD(hour,-24,@now);
 INSERT dbo.AiQuotaReservation(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,InvocationId,Operation,RowCost,Admitted,AdmittedAt,LeaseExpiresAt)
 VALUES(@t,@IntegrationId,@u,@GrantId,@SessionId,@InvocationId,CASE WHEN @RequiredScope=1 THEN 'SEARCH' ELSE 'OVERVIEW' END,
 CASE WHEN @admit=1 THEN @cost ELSE 0 END,@admit,@now,DATEADD(second,15,@now));

 SET @Admitted=@admit;
 EXEC dbo.AiExternalDataLoadAuthorityV2 @ProofDigest,@InvocationId,@CorrelationId,1,1,@Operation,@t OUTPUT,@u OUTPUT,@IntegrationId OUTPUT,@GrantId OUTPUT,@SessionId OUTPUT,@AccessCredentialId OUTPUT,@ClientId OUTPUT,@KeyId OUTPUT,@Issuer OUTPUT,@Resource OUTPUT,@mask OUTPUT,@deadline OUTPUT,@revision OUTPUT,@policy OUTPUT,@registration OUTPUT,@grantRevision OUTPUT;
END;

GO
CREATE PROCEDURE dbo.AiExternalDataSearchCasesV2 @ProofDigest varbinary(max),@InvocationId uniqueidentifier,@CorrelationId uniqueidentifier,@NormalizedQuery nvarchar(max),@Page int
 AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 3000;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR @ProofDigest IS NULL OR DATALENGTH(@ProofDigest)<>32 OR @InvocationId IS NULL OR @CorrelationId IS NULL THROW 59101,'Integration authentication failed.',1;
 DECLARE @fixedDigest binary(32)=CONVERT(binary(32),@ProofDigest);
 DECLARE @FailureCode int=59101;
 DECLARE @oldTenant sql_variant=SESSION_CONTEXT(N'ShaleClientId'),@oldUser sql_variant=SESSION_CONTEXT(N'PrincipalUserId');
 DECLARE @t int,@u int,@IntegrationId uniqueidentifier,@GrantId uniqueidentifier,@SessionId uniqueidentifier,@AccessCredentialId bigint,@ClientId varchar(64),@KeyId uniqueidentifier,
 @Issuer nvarchar(256),@Resource nvarchar(256),@mask tinyint,@deadline datetime2(7),@revision bigint,@policy bigint,@registration bigint,@grantRevision bigint;
 BEGIN TRY
 DECLARE @admitted bit;
 BEGIN TRANSACTION;
 EXEC dbo.AiExternalDataLoadAuthorityV2 @ProofDigest,@InvocationId,@CorrelationId,1,1,1,@t OUTPUT,@u OUTPUT,@IntegrationId OUTPUT,@GrantId OUTPUT,@SessionId OUTPUT,@AccessCredentialId OUTPUT,@ClientId OUTPUT,@KeyId OUTPUT,@Issuer OUTPUT,@Resource OUTPUT,@mask OUTPUT,@deadline OUTPUT,@revision OUTPUT,@policy OUTPUT,@registration OUTPUT,@grantRevision OUTPUT;
 DECLARE @pattern nvarchar(max)=dbo.AiDataNormalizeV1(@NormalizedQuery); IF @pattern IS NULL OR @Page IS NULL OR @Page NOT BETWEEN 0 AND 9 THROW 59102,'Invalid input.',1;
 EXEC dbo.AiExternalDataAdmitV2 @ProofDigest,@InvocationId,@CorrelationId,1,@Page,NULL,@admitted OUTPUT;
 COMMIT TRANSACTION;
 IF @admitted<>1 THROW 59105,'Quota unavailable.',1;
 BEGIN TRANSACTION;
 EXEC dbo.AiExternalDataLoadAuthorityV2 @ProofDigest,@InvocationId,@CorrelationId,1,1,1,@t OUTPUT,@u OUTPUT,@IntegrationId OUTPUT,@GrantId OUTPUT,@SessionId OUTPUT,@AccessCredentialId OUTPUT,@ClientId OUTPUT,@KeyId OUTPUT,@Issuer OUTPUT,@Resource OUTPUT,@mask OUTPUT,@deadline OUTPUT,@revision OUTPUT,@policy OUTPUT,@registration OUTPUT,@grantRevision OUTPUT;
 DECLARE @lease datetime2(7);
 SELECT @lease=LeaseExpiresAt FROM dbo.AiQuotaReservation WHERE InvocationId=@InvocationId AND ShaleClientId=@t AND UserId=@u AND Admitted=1 AND ReleasedAt IS NULL AND RowCost=10;
 IF @lease IS NULL OR @lease<=SYSUTCDATETIME() OR EXISTS(SELECT 1 FROM dbo.AiCimdInvocationV1 WHERE InvocationId=@InvocationId AND (ReadFailed=1 OR ReadEventId IS NOT NULL OR HandoffConsumed=1 OR Operation<>1)) THROW 59101,'Integration authentication failed.',1;
 DECLARE @rows table(Ordinal int,Id int,CaseNumber nvarchar(max),Name nvarchar(max),StatusId int,StatusName nvarchar(max),StatusColor nvarchar(max),PracticeAreaId int,PracticeAreaName nvarchar(max),ResponsibleAttorneyId int,ResponsibleAttorneyName nvarchar(max),PrimaryLegalAssistantId int,PrimaryLegalAssistantName nvarchar(max),UpdatedAt datetime2(7));
 SET @FailureCode=59107;
 INSERT @rows SELECT ROW_NUMBER() OVER(ORDER BY c.Name,c.Id),c.Id,c.CaseNumber,COALESCE(c.Name,N''),status_row.StatusId,status_row.StatusName,status_row.StatusColor,pa.Id,pa.Name,attorney_user.id,attorney_user.DisplayName,assistant_user.id,assistant_user.DisplayName,c.UpdatedAt
 FROM dbo.Cases c OUTER APPLY (
			 SELECT TOP (1) s.Id StatusId, s.SystemKey, s.LifecycleKey, s.Name StatusName, s.Color StatusColor, s.SortOrder StatusSortOrder
			 FROM dbo.CaseStatuses cs JOIN dbo.Statuses s ON s.Id=cs.StatusId
			  AND (s.ShaleClientId=c.ShaleClientId OR s.ShaleClientId IS NULL)
			 WHERE cs.CaseId=c.Id AND cs.EndDate IS NULL
			 ORDER BY cs.IsPrimary DESC, cs.EffectiveDate DESC, cs.UpdatedAt DESC, cs.CreatedAt DESC, cs.Id DESC
			) status_row
 LEFT JOIN dbo.PracticeAreas pa ON pa.Id=c.PracticeAreaId AND (pa.ShaleClientId=c.ShaleClientId OR pa.ShaleClientId IS NULL)
 OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.ShaleClientId=c.ShaleClientId AND cu.RoleId=4 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) attorney
 OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName FROM dbo.Users u WHERE u.id=attorney.UserId AND u.ShaleClientId=c.ShaleClientId) attorney_user
 OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.ShaleClientId=c.ShaleClientId AND cu.RoleId=11 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) assistant
 OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName FROM dbo.Users u WHERE u.id=assistant.UserId AND u.ShaleClientId=c.ShaleClientId) assistant_user
 WHERE c.ShaleClientId=@t AND ISNULL(c.IsDeleted,0)=0 AND LOWER(COALESCE(c.Name,N'')) LIKE @pattern ORDER BY c.Name,c.Id OFFSET (@Page*10) ROWS FETCH NEXT 11 ROWS ONLY;

 -- Validate source widths before audits/COMMIT; never truncate names or transfer an oversized source.
 IF EXISTS(SELECT 1 FROM @rows WHERE DATALENGTH(CaseNumber)>400 OR DATALENGTH(Name)>510 OR DATALENGTH(StatusName)>510 OR DATALENGTH(StatusColor)>40 OR DATALENGTH(PracticeAreaName)>510 OR DATALENGTH(ResponsibleAttorneyName)>510 OR DATALENGTH(PrimaryLegalAssistantName)>510) THROW 59106,'Projection unavailable.',1;
 DECLARE @hasMore bit=CASE WHEN (SELECT COUNT(*) FROM @rows)>10 THEN 1 ELSE 0 END;
 DELETE FROM @rows WHERE Ordinal>@Page*10+10;
 SET @FailureCode=59103;

 DECLARE @ids dbo.AiCaseDataIds,@event bigint;
 INSERT @ids SELECT Id FROM @rows;
 EXEC dbo.AiExternalDataAppendAccessV2 @InvocationId,'search_cases','ALLOWED_READ','NONE',@Page,NULL,@ids,@event OUTPUT;
 UPDATE dbo.AiCimdInvocationV1 SET ReadEventId=@event WHERE InvocationId=@InvocationId;
 EXEC dbo.AiExternalDataLoadAuthorityV2 @ProofDigest,@InvocationId,@CorrelationId,1,1,1,@t OUTPUT,@u OUTPUT,@IntegrationId OUTPUT,@GrantId OUTPUT,@SessionId OUTPUT,@AccessCredentialId OUTPUT,@ClientId OUTPUT,@KeyId OUTPUT,@Issuer OUTPUT,@Resource OUTPUT,@mask OUTPUT,@deadline OUTPUT,@revision OUTPUT,@policy OUTPUT,@registration OUTPUT,@grantRevision OUTPUT;
 SET @FailureCode=59108;
 IF @lease<=SYSUTCDATETIME() THROW 59101,'Integration authentication failed.',1;
 COMMIT TRANSACTION;
 SELECT Id,CaseNumber,Name,StatusId,StatusName,StatusColor,PracticeAreaId,PracticeAreaName,ResponsibleAttorneyId,ResponsibleAttorneyName,PrimaryLegalAssistantId,PrimaryLegalAssistantName,UpdatedAt,@InvocationId InvocationId,@CorrelationId CorrelationId,@event ReadEventId,@deadline Deadline,@Page Page,10 Size,@hasMore HasMore,CONVERT(bit,CASE WHEN @Page=9 AND @hasMore=1 THEN 1 ELSE 0 END) WindowExhausted FROM @rows ORDER BY Ordinal;
 -- Empty search still returns a separate bounded receipt, never a total or probe row.
 SELECT @InvocationId InvocationId,@CorrelationId CorrelationId,@event ReadEventId,@deadline Deadline,@Page Page,10 Size,@hasMore HasMore,CONVERT(bit,CASE WHEN @Page=9 AND @hasMore=1 THEN 1 ELSE 0 END) WindowExhausted;

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
 END;

GO
CREATE PROCEDURE dbo.AiExternalDataGetCaseOverviewV2 @ProofDigest varbinary(max),@InvocationId uniqueidentifier,@CorrelationId uniqueidentifier,@CaseId bigint
 AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 3000;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR @ProofDigest IS NULL OR DATALENGTH(@ProofDigest)<>32 OR @InvocationId IS NULL OR @CorrelationId IS NULL THROW 59101,'Integration authentication failed.',1;
 DECLARE @fixedDigest binary(32)=CONVERT(binary(32),@ProofDigest);
 DECLARE @FailureCode int=59101;
 DECLARE @oldTenant sql_variant=SESSION_CONTEXT(N'ShaleClientId'),@oldUser sql_variant=SESSION_CONTEXT(N'PrincipalUserId');
 DECLARE @t int,@u int,@IntegrationId uniqueidentifier,@GrantId uniqueidentifier,@SessionId uniqueidentifier,@AccessCredentialId bigint,@ClientId varchar(64),@KeyId uniqueidentifier,
 @Issuer nvarchar(256),@Resource nvarchar(256),@mask tinyint,@deadline datetime2(7),@revision bigint,@policy bigint,@registration bigint,@grantRevision bigint;
 BEGIN TRY
 DECLARE @admitted bit;
 BEGIN TRANSACTION;
 EXEC dbo.AiExternalDataLoadAuthorityV2 @ProofDigest,@InvocationId,@CorrelationId,1,1,2,@t OUTPUT,@u OUTPUT,@IntegrationId OUTPUT,@GrantId OUTPUT,@SessionId OUTPUT,@AccessCredentialId OUTPUT,@ClientId OUTPUT,@KeyId OUTPUT,@Issuer OUTPUT,@Resource OUTPUT,@mask OUTPUT,@deadline OUTPUT,@revision OUTPUT,@policy OUTPUT,@registration OUTPUT,@grantRevision OUTPUT;
 IF @CaseId IS NULL OR @CaseId NOT BETWEEN 1 AND 2147483647 THROW 59102,'Invalid input.',1;
 EXEC dbo.AiExternalDataAdmitV2 @ProofDigest,@InvocationId,@CorrelationId,2,NULL,@CaseId,@admitted OUTPUT;
 COMMIT TRANSACTION;
 IF @admitted<>1 THROW 59105,'Quota unavailable.',1;
 BEGIN TRANSACTION;
 EXEC dbo.AiExternalDataLoadAuthorityV2 @ProofDigest,@InvocationId,@CorrelationId,1,1,2,@t OUTPUT,@u OUTPUT,@IntegrationId OUTPUT,@GrantId OUTPUT,@SessionId OUTPUT,@AccessCredentialId OUTPUT,@ClientId OUTPUT,@KeyId OUTPUT,@Issuer OUTPUT,@Resource OUTPUT,@mask OUTPUT,@deadline OUTPUT,@revision OUTPUT,@policy OUTPUT,@registration OUTPUT,@grantRevision OUTPUT;
 DECLARE @lease datetime2(7);
 SELECT @lease=LeaseExpiresAt FROM dbo.AiQuotaReservation WHERE InvocationId=@InvocationId AND ShaleClientId=@t AND UserId=@u AND Admitted=1 AND ReleasedAt IS NULL AND RowCost=1;
 IF @lease IS NULL OR @lease<=SYSUTCDATETIME() OR EXISTS(SELECT 1 FROM dbo.AiCimdInvocationV1 WHERE InvocationId=@InvocationId AND (ReadFailed=1 OR ReadEventId IS NOT NULL OR HandoffConsumed=1 OR Operation<>2)) THROW 59101,'Integration authentication failed.',1;
 DECLARE @rows table(Ordinal int,Id int,CaseNumber nvarchar(max),Name nvarchar(max),StatusId int,StatusName nvarchar(max),StatusColor nvarchar(max),PracticeAreaId int,PracticeAreaName nvarchar(max),ResponsibleAttorneyId int,ResponsibleAttorneyName nvarchar(max),PrimaryLegalAssistantId int,PrimaryLegalAssistantName nvarchar(max),UpdatedAt datetime2(7));
 SET @FailureCode=59107;
 INSERT @rows SELECT ROW_NUMBER() OVER(ORDER BY c.Name,c.Id),c.Id,c.CaseNumber,COALESCE(c.Name,N''),status_row.StatusId,status_row.StatusName,status_row.StatusColor,pa.Id,pa.Name,attorney_user.id,attorney_user.DisplayName,assistant_user.id,assistant_user.DisplayName,c.UpdatedAt
 FROM dbo.Cases c OUTER APPLY (
			 SELECT TOP (1) s.Id StatusId, s.SystemKey, s.LifecycleKey, s.Name StatusName, s.Color StatusColor, s.SortOrder StatusSortOrder
			 FROM dbo.CaseStatuses cs JOIN dbo.Statuses s ON s.Id=cs.StatusId
			  AND (s.ShaleClientId=c.ShaleClientId OR s.ShaleClientId IS NULL)
			 WHERE cs.CaseId=c.Id AND cs.EndDate IS NULL
			 ORDER BY cs.IsPrimary DESC, cs.EffectiveDate DESC, cs.UpdatedAt DESC, cs.CreatedAt DESC, cs.Id DESC
			) status_row
 LEFT JOIN dbo.PracticeAreas pa ON pa.Id=c.PracticeAreaId AND (pa.ShaleClientId=c.ShaleClientId OR pa.ShaleClientId IS NULL)
 OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.ShaleClientId=c.ShaleClientId AND cu.RoleId=4 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) attorney
 OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName FROM dbo.Users u WHERE u.id=attorney.UserId AND u.ShaleClientId=c.ShaleClientId) attorney_user
 OUTER APPLY (SELECT TOP(1) cu.UserId FROM dbo.CaseUsers cu WHERE cu.CaseId=c.Id AND cu.ShaleClientId=c.ShaleClientId AND cu.RoleId=11 ORDER BY cu.IsPrimary DESC,cu.UpdatedAt DESC,cu.CreatedAt DESC,cu.Id DESC) assistant
 OUTER APPLY (SELECT u.id,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName FROM dbo.Users u WHERE u.id=assistant.UserId AND u.ShaleClientId=c.ShaleClientId) assistant_user
 WHERE c.ShaleClientId=@t AND ISNULL(c.IsDeleted,0)=0 AND c.Id=@CaseId ORDER BY c.Name,c.Id OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY;

 -- Validate source widths before audits/COMMIT; never truncate names or transfer an oversized source.
 IF EXISTS(SELECT 1 FROM @rows WHERE DATALENGTH(CaseNumber)>400 OR DATALENGTH(Name)>510 OR DATALENGTH(StatusName)>510 OR DATALENGTH(StatusColor)>40 OR DATALENGTH(PracticeAreaName)>510 OR DATALENGTH(ResponsibleAttorneyName)>510 OR DATALENGTH(PrimaryLegalAssistantName)>510) THROW 59106,'Projection unavailable.',1;
 DECLARE @hasMore bit=CASE WHEN (SELECT COUNT(*) FROM @rows)>10 THEN 1 ELSE 0 END;

 SET @FailureCode=59103;
 IF NOT EXISTS(SELECT 1 FROM @rows) THROW 59104,'Case unavailable.',1;
 DECLARE @before bigint=(SELECT COUNT_BIG(*) FROM dbo.auditLog WITH(UPDLOCK,HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND ObjectTypeId=1 AND ObjectId=@CaseId AND FieldName='Case.Overview.Read' AND TRY_CONVERT(int,FieldCode)=4 AND StringValue='action=READ;screen=Case.Overview');
 DECLARE @zone sysname=(SELECT SqlTimeZone FROM dbo.AiCaseDataDeployment WHERE Id=1);
 INSERT dbo.auditLog(ShaleClientId,userId,objectTypeId,objectId,fieldName,fieldCode,stringValue,dateValue,booleanValue,intValue,entryDate)
 VALUES(@t,@u,1,@CaseId,'Case.Overview.Read',4,'action=READ;screen=Case.Overview',NULL,NULL,NULL,CONVERT(varchar(50),CONVERT(datetime2(7),(SYSUTCDATETIME() AT TIME ZONE 'UTC') AT TIME ZONE @zone),121));
 IF (SELECT COUNT_BIG(*) FROM dbo.auditLog WHERE ShaleClientId=@t AND UserId=@u AND ObjectTypeId=1 AND ObjectId=@CaseId AND FieldName='Case.Overview.Read' AND TRY_CONVERT(int,FieldCode)=4 AND StringValue='action=READ;screen=Case.Overview')<>@before+1 THROW 59103,'Required audit unavailable.',1;
 DECLARE @ids dbo.AiCaseDataIds,@event bigint;
 INSERT @ids SELECT Id FROM @rows;
 EXEC dbo.AiExternalDataAppendAccessV2 @InvocationId,'get_case_overview','ALLOWED_READ','NONE',NULL,NULL,@ids,@event OUTPUT;
 UPDATE dbo.AiCimdInvocationV1 SET ReadEventId=@event WHERE InvocationId=@InvocationId;
 EXEC dbo.AiExternalDataLoadAuthorityV2 @ProofDigest,@InvocationId,@CorrelationId,1,1,2,@t OUTPUT,@u OUTPUT,@IntegrationId OUTPUT,@GrantId OUTPUT,@SessionId OUTPUT,@AccessCredentialId OUTPUT,@ClientId OUTPUT,@KeyId OUTPUT,@Issuer OUTPUT,@Resource OUTPUT,@mask OUTPUT,@deadline OUTPUT,@revision OUTPUT,@policy OUTPUT,@registration OUTPUT,@grantRevision OUTPUT;
 SET @FailureCode=59108;
 IF @lease<=SYSUTCDATETIME() THROW 59101,'Integration authentication failed.',1;
 COMMIT TRANSACTION;
 SELECT Id,CaseNumber,Name,StatusId,StatusName,StatusColor,PracticeAreaId,PracticeAreaName,ResponsibleAttorneyId,ResponsibleAttorneyName,PrimaryLegalAssistantId,PrimaryLegalAssistantName,UpdatedAt,@InvocationId InvocationId,@CorrelationId CorrelationId,@event ReadEventId,@deadline Deadline,0 Page,10 Size,@hasMore HasMore,CONVERT(bit,CASE WHEN 0=9 AND @hasMore=1 THEN 1 ELSE 0 END) WindowExhausted FROM @rows ORDER BY Ordinal;
 -- Empty search still returns a separate bounded receipt, never a total or probe row.
 SELECT @InvocationId InvocationId,@CorrelationId CorrelationId,@event ReadEventId,@deadline Deadline,0 Page,10 Size,@hasMore HasMore,CONVERT(bit,CASE WHEN 0=9 AND @hasMore=1 THEN 1 ELSE 0 END) WindowExhausted;

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
 END;

GO
CREATE PROCEDURE dbo.AiExternalDataFinishCaseQuotaV2 @ProofDigest varbinary(max),@InvocationId uniqueidentifier,@CorrelationId uniqueidentifier
 AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 3000;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR @ProofDigest IS NULL OR DATALENGTH(@ProofDigest)<>32 OR @InvocationId IS NULL OR @CorrelationId IS NULL THROW 59101,'Integration authentication failed.',1;
 DECLARE @fixedDigest binary(32)=CONVERT(binary(32),@ProofDigest);
 DECLARE @FailureCode int=59101;
 DECLARE @oldTenant sql_variant=SESSION_CONTEXT(N'ShaleClientId'),@oldUser sql_variant=SESSION_CONTEXT(N'PrincipalUserId');
 DECLARE @t int,@u int,@IntegrationId uniqueidentifier,@GrantId uniqueidentifier,@SessionId uniqueidentifier,@AccessCredentialId bigint,@ClientId varchar(64),@KeyId uniqueidentifier,
 @Issuer nvarchar(256),@Resource nvarchar(256),@mask tinyint,@deadline datetime2(7),@revision bigint,@policy bigint,@registration bigint,@grantRevision bigint;
 BEGIN TRY

 BEGIN TRANSACTION;
 EXEC dbo.AiExternalDataLoadAuthorityV2 @ProofDigest,@InvocationId,@CorrelationId,0,1,0,@t OUTPUT,@u OUTPUT,@IntegrationId OUTPUT,@GrantId OUTPUT,@SessionId OUTPUT,@AccessCredentialId OUTPUT,@ClientId OUTPUT,@KeyId OUTPUT,@Issuer OUTPUT,@Resource OUTPUT,@mask OUTPUT,@deadline OUTPUT,@revision OUTPUT,@policy OUTPUT,@registration OUTPUT,@grantRevision OUTPUT;
 UPDATE dbo.AiQuotaReservation SET ReleasedAt=SYSUTCDATETIME() WHERE InvocationId=@InvocationId AND ShaleClientId=@t AND UserId=@u AND IntegrationId=@IntegrationId AND GrantId=@GrantId AND SessionId=@SessionId AND ReleasedAt IS NULL;
 COMMIT TRANSACTION;
 SELECT CONVERT(bit,1) Finished;

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
 END;

GO
CREATE PROCEDURE dbo.AiExternalDataAuthorizeCaseHandoffV2 @ProofDigest varbinary(max),@InvocationId uniqueidentifier,@CorrelationId uniqueidentifier
 AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 3000;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR @ProofDigest IS NULL OR DATALENGTH(@ProofDigest)<>32 OR @InvocationId IS NULL OR @CorrelationId IS NULL THROW 59101,'Integration authentication failed.',1;
 DECLARE @fixedDigest binary(32)=CONVERT(binary(32),@ProofDigest);
 DECLARE @FailureCode int=59101;
 DECLARE @oldTenant sql_variant=SESSION_CONTEXT(N'ShaleClientId'),@oldUser sql_variant=SESSION_CONTEXT(N'PrincipalUserId');
 DECLARE @t int,@u int,@IntegrationId uniqueidentifier,@GrantId uniqueidentifier,@SessionId uniqueidentifier,@AccessCredentialId bigint,@ClientId varchar(64),@KeyId uniqueidentifier,
 @Issuer nvarchar(256),@Resource nvarchar(256),@mask tinyint,@deadline datetime2(7),@revision bigint,@policy bigint,@registration bigint,@grantRevision bigint;
 BEGIN TRY

 BEGIN TRANSACTION;
 EXEC dbo.AiExternalDataLoadAuthorityV2 @ProofDigest,@InvocationId,@CorrelationId,1,1,0,@t OUTPUT,@u OUTPUT,@IntegrationId OUTPUT,@GrantId OUTPUT,@SessionId OUTPUT,@AccessCredentialId OUTPUT,@ClientId OUTPUT,@KeyId OUTPUT,@Issuer OUTPUT,@Resource OUTPUT,@mask OUTPUT,@deadline OUTPUT,@revision OUTPUT,@policy OUTPUT,@registration OUTPUT,@grantRevision OUTPUT;
 DECLARE @operation tinyint,@event bigint,@lease datetime2(7);
 SELECT @operation=Operation,@event=ReadEventId FROM dbo.AiCimdInvocationV1 WHERE InvocationId=@InvocationId AND DispatchClaimed=1 AND ReadFailed=0 AND HandoffConsumed=0;
 SELECT @lease=LeaseExpiresAt FROM dbo.AiQuotaReservation WHERE InvocationId=@InvocationId AND ShaleClientId=@t AND UserId=@u AND Admitted=1 AND ReleasedAt IS NULL AND RowCost=CASE @operation WHEN 1 THEN 10 WHEN 2 THEN 1 ELSE -1 END;
 IF @operation IS NULL OR @event IS NULL OR @lease IS NULL OR (@mask & @operation)<>@operation
 OR NOT EXISTS(SELECT 1 FROM dbo.IntegrationAccessAuditLog WHERE Id=@event AND InvocationId=@InvocationId AND CorrelationId=@CorrelationId AND Stage='READ' AND Outcome='ALLOWED_READ') THROW 59101,'Integration authentication failed.',1;
 UPDATE dbo.AiQuotaReservation SET ReleasedAt=SYSUTCDATETIME() WHERE InvocationId=@InvocationId AND ShaleClientId=@t AND UserId=@u AND IntegrationId=@IntegrationId AND GrantId=@GrantId AND SessionId=@SessionId AND ReleasedAt IS NULL;
 IF @@ROWCOUNT<>1 THROW 59101,'Integration authentication failed.',1;
 UPDATE dbo.AiCimdInvocationV1 SET HandoffConsumed=1 WHERE InvocationId=@InvocationId AND HandoffConsumed=0;
 IF @@ROWCOUNT<>1 THROW 59101,'Integration authentication failed.',1;
 -- Reauthenticate after every housekeeping/authority lock; no stale pre-wait clock can authorize.
 EXEC dbo.AiExternalDataLoadAuthorityV2 @ProofDigest,@InvocationId,@CorrelationId,1,1,0,@t OUTPUT,@u OUTPUT,@IntegrationId OUTPUT,@GrantId OUTPUT,@SessionId OUTPUT,@AccessCredentialId OUTPUT,@ClientId OUTPUT,@KeyId OUTPUT,@Issuer OUTPUT,@Resource OUTPUT,@mask OUTPUT,@deadline OUTPUT,@revision OUTPUT,@policy OUTPUT,@registration OUTPUT,@grantRevision OUTPUT;
 DECLARE @sample datetime2(7)=SYSUTCDATETIME(),@remaining bigint;
 IF @lease<@deadline SET @deadline=@lease;
 SET @remaining=DATEDIFF_BIG(millisecond,@sample,@deadline)-1;
 IF @remaining<1 THROW 59101,'Integration authentication failed.',1;
 COMMIT TRANSACTION;
 SELECT @InvocationId InvocationId,@CorrelationId CorrelationId,@mask ScopeMask,@sample DatabaseUtc,@deadline Deadline,@remaining RemainingMillis;

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
 END;

GO
CREATE PROCEDURE dbo.AiExternalDataRecordCaseFailureV2 @ProofDigest varbinary(max),@InvocationId uniqueidentifier,@CorrelationId uniqueidentifier,@Tool varchar(max),@Outcome varchar(max),@Reason varchar(max),@Page int=NULL,@RequestedCaseId bigint=NULL
 AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 3000;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR @ProofDigest IS NULL OR DATALENGTH(@ProofDigest)<>32 OR @InvocationId IS NULL OR @CorrelationId IS NULL THROW 59101,'Integration authentication failed.',1;
 DECLARE @fixedDigest binary(32)=CONVERT(binary(32),@ProofDigest);
 DECLARE @FailureCode int=59101;
 DECLARE @oldTenant sql_variant=SESSION_CONTEXT(N'ShaleClientId'),@oldUser sql_variant=SESSION_CONTEXT(N'PrincipalUserId');
 DECLARE @t int,@u int,@IntegrationId uniqueidentifier,@GrantId uniqueidentifier,@SessionId uniqueidentifier,@AccessCredentialId bigint,@ClientId varchar(64),@KeyId uniqueidentifier,
 @Issuer nvarchar(256),@Resource nvarchar(256),@mask tinyint,@deadline datetime2(7),@revision bigint,@policy bigint,@registration bigint,@grantRevision bigint;
 BEGIN TRY

 BEGIN TRANSACTION;
 EXEC dbo.AiExternalDataLoadAuthorityV2 @ProofDigest,@InvocationId,@CorrelationId,0,0,0,@t OUTPUT,@u OUTPUT,@IntegrationId OUTPUT,@GrantId OUTPUT,@SessionId OUTPUT,@AccessCredentialId OUTPUT,@ClientId OUTPUT,@KeyId OUTPUT,@Issuer OUTPUT,@Resource OUTPUT,@mask OUTPUT,@deadline OUTPUT,@revision OUTPUT,@policy OUTPUT,@registration OUTPUT,@grantRevision OUTPUT;
 IF @Tool IS NULL OR DATALENGTH(@Tool) NOT IN(12,17) OR DATALENGTH(@Tool)<>LEN(@Tool) OR @Tool COLLATE Latin1_General_100_BIN2 NOT IN('search_cases','get_case_overview')
 OR @Outcome IS NULL OR DATALENGTH(@Outcome)<>LEN(@Outcome) OR @Outcome COLLATE Latin1_General_100_BIN2 NOT IN('DENIED','INVALID_INPUT','UNAVAILABLE','THROTTLED','CANCELLED')
 OR @Reason IS NULL OR DATALENGTH(@Reason)<>LEN(@Reason) OR @Reason COLLATE Latin1_General_100_BIN2 NOT IN('AUTHORITY','INPUT','READ','AUDIT','COMMIT','CLOSE','SERIALIZATION','OVERSIZED','DEADLINE','QUOTA','CANCELLED','CASE_UNAVAILABLE')
 OR (@Page IS NOT NULL AND (@Tool<>'search_cases' OR @Page NOT BETWEEN 0 AND 9))
 OR (@RequestedCaseId IS NOT NULL AND (@Tool<>'get_case_overview' OR @RequestedCaseId NOT BETWEEN 1 AND 2147483647)) THROW 59102,'Invalid input.',1;
 DECLARE @now datetime2(7)=SYSUTCDATETIME(),@historical bit=1,@operation tinyint=CASE @Tool WHEN 'search_cases' THEN 1 ELSE 2 END;
 IF NOT EXISTS(SELECT 1 FROM dbo.AiCimdInvocationV1 WHERE InvocationId=@InvocationId) BEGIN SET @deadline=@now; INSERT dbo.AiCimdInvocationV1(InvocationId,CorrelationId,ShaleClientId,UserId,IntegrationId,GrantId,SessionId,AccessCredentialId,ClientId,KeyId,Issuer,Resource,ScopeMask,CapabilityRevision,PolicyRevision,IntegrationRevision,GrantRevision,AdmittedAt,DeadlineUtc,HistoricalOnly)
 VALUES(@InvocationId,@CorrelationId,@t,@u,@IntegrationId,@GrantId,@SessionId,@AccessCredentialId,@ClientId,@KeyId,@Issuer,@Resource,@mask,COALESCE(@revision,0),COALESCE(@policy,0),COALESCE(@registration,0),COALESCE(@grantRevision,0),@now,@deadline,@historical); END;
 IF EXISTS(SELECT 1 FROM dbo.AiCimdInvocationV1 WHERE InvocationId=@InvocationId AND Operation IS NOT NULL AND (Operation<>@operation OR ISNULL(SearchPage,-1)<>ISNULL(@Page,-1) OR ISNULL(RequestedCaseId,-1)<>ISNULL(@RequestedCaseId,-1))) THROW 59101,'Integration authentication failed.',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.AiQuotaReservation WHERE InvocationId=@InvocationId)
 INSERT dbo.AiQuotaReservation(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,InvocationId,Operation,RowCost,Admitted,AdmittedAt,LeaseExpiresAt)
 VALUES(@t,@IntegrationId,@u,@GrantId,@SessionId,@InvocationId,CASE @operation WHEN 1 THEN 'SEARCH' ELSE 'OVERVIEW' END,0,0,@now,DATEADD(second,15,@now));
 DECLARE @ids dbo.AiCaseDataIds,@event bigint;
 EXEC dbo.AiExternalDataAppendAccessV2 @InvocationId,@Tool,@Outcome,@Reason,@Page,@RequestedCaseId,@ids,@event OUTPUT;
 UPDATE dbo.AiCimdInvocationV1 SET ReadFailed=1,Operation=COALESCE(Operation,@operation),SearchPage=COALESCE(SearchPage,@Page),RequestedCaseId=COALESCE(RequestedCaseId,CONVERT(int,@RequestedCaseId)) WHERE InvocationId=@InvocationId;
 COMMIT TRANSACTION;
 SELECT @event FailureEventId;

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
 END;

GO
CREATE PROCEDURE dbo.AiExternalRevokeFamilyV2
@SessionId uniqueidentifier,@ExpectedRowVer varbinary(max), @CorrelationId uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;

 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59401,'Fresh CIMD revocation lease required.',1;
 IF @ExpectedRowVer IS NULL OR DATALENGTH(@ExpectedRowVer)<>8 THROW 59401,'Exact RowVer required.',1;
 IF @CorrelationId IS NULL THROW 58406,'AI correlation required.',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1 @Admin=0;

 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),
 @now datetime2(7)=SYSUTCDATETIME(),@id bigint;

 DECLARE @admin bit=CASE WHEN EXISTS(SELECT 1 FROM dbo.Users WHERE id=@u AND ShaleClientId=@t AND is_admin=1) THEN 1 ELSE 0 END;
 SELECT @id=Id FROM dbo.AiDelegatedSession WHERE ShaleClientId=@t AND SessionId=@SessionId AND (UserId=@u OR @admin=1) AND RowVer=@ExpectedRowVer;
 IF NOT EXISTS(SELECT 1 FROM dbo.AiCimdFamilyV1 WHERE ShaleClientId=@t AND SessionId=@SessionId) THROW 59401,'CIMD family unavailable.',1;
 IF @id IS NULL THROW 58407,'AI concurrency conflict.',1;
 IF EXISTS(SELECT 1 FROM dbo.AiDelegatedSession WHERE Id=@id AND RevokedAt IS NULL) BEGIN
  UPDATE dbo.AiDelegatedSession SET RevokedAt=@now,RevocationReason=CASE WHEN @admin=1 THEN 'ADMIN' ELSE 'SELF' END WHERE Id=@id AND ShaleClientId=@t;
  UPDATE dbo.AiCimdCredentialV1 SET RevokedAt=@now WHERE ShaleClientId=@t AND SessionId=@SessionId AND RevokedAt IS NULL;
  INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,'AI_DELEGATED_SESSION',@id,'DEACTIVATED',@CorrelationId,'SHALE_SERVICE',NULL);
 IF @@ROWCOUNT<>1 THROW 58405,'AI audit unavailable.',1;
 END;
 SELECT RowVer FROM dbo.AiDelegatedSession WHERE Id=@id AND ShaleClientId=@t;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
 END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiExternalPublishMetadataV2
 @ApplicationId uniqueidentifier,@Revision bigint,@Digest varbinary(max),@Redirects nvarchar(max),@Keys nvarchar(max),@FreshUntil datetime2(7)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 59500,'Fresh neutral metadata lease required.',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @now datetime2(7)=SYSUTCDATETIME(),@held bigint;
 SELECT @held=Revision FROM dbo.AiExternalApplication WITH(UPDLOCK,HOLDLOCK) WHERE ApplicationId=@ApplicationId;
 IF @Digest IS NULL OR DATALENGTH(@Digest)<>32 OR @FreshUntil IS NULL OR @FreshUntil<=@now OR @FreshUntil>DATEADD(minute,5,@now)
 OR @Redirects IS NULL OR @Keys IS NULL OR DATALENGTH(@Redirects)>65536 OR DATALENGTH(@Keys)>16384 OR ISJSON(@Redirects)<>1 OR ISJSON(@Keys)<>1
 OR LEFT(@Redirects,1)<>N'[' OR LEFT(@Keys,1)<>N'['
 OR NOT EXISTS(SELECT 1 FROM dbo.AiExternalApplication WHERE ApplicationId=@ApplicationId AND Revision=@Revision AND MetadataDigest=@Digest AND Source='CIMD' AND Disabled=0)
 THROW 59500,'Publication unavailable.',1;
 IF (SELECT COUNT(*) FROM OPENJSON(@Redirects)) NOT BETWEEN 1 AND 16
 OR EXISTS(SELECT 1 FROM OPENJSON(@Redirects) WHERE type<>1 OR DATALENGTH(value) NOT BETWEEN 2 AND 4096 OR CHARINDEX(N'#',value)>0 OR CHARINDEX(N'*',value)>0)
 OR (SELECT COUNT(*) FROM OPENJSON(@Keys))>2
 OR EXISTS(SELECT 1 FROM OPENJSON(@Keys) WITH(kid nvarchar(max),modulus varchar(max),exponent int) WHERE kid IS NULL OR DATALENGTH(kid) NOT BETWEEN 2 AND 256 OR exponent<>65537 OR exponent IS NULL OR TRY_CONVERT(varbinary(max),modulus,2) IS NULL OR DATALENGTH(TRY_CONVERT(varbinary(max),modulus,2)) NOT BETWEEN 256 AND 512)
 THROW 59500,'Metadata profile unavailable.',1;
 IF EXISTS(SELECT 1 FROM dbo.AiCimdPublicationV1 WHERE ApplicationId=@ApplicationId AND ApplicationRevision=@Revision) BEGIN
 IF NOT EXISTS(SELECT 1 FROM dbo.AiCimdPublicationV1 WHERE ApplicationId=@ApplicationId AND ApplicationRevision=@Revision AND MetadataDigest=@Digest
 AND Redirects=@Redirects COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Redirects)=DATALENGTH(@Redirects)
 AND SigningKeys=@Keys COLLATE Latin1_General_100_BIN2 AND DATALENGTH(SigningKeys)=DATALENGTH(@Keys)) THROW 59500,'Publication content is immutable.',1;
 UPDATE dbo.AiCimdPublicationV1 SET FreshUntil=@FreshUntil WHERE ApplicationId=@ApplicationId AND ApplicationRevision=@Revision;
 END ELSE INSERT dbo.AiCimdPublicationV1 VALUES(@ApplicationId,@Revision,@Digest,@Redirects,@Keys,@FreshUntil);
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiExternalOAuthAuthorityV2
 @ApplicationId uniqueidentifier,@Revision bigint,@Approval uniqueidentifier,@ApprovalVersion varbinary(max),@Digest varbinary(max),
 @External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@Integration uniqueidentifier,@Web uniqueidentifier,@Scopes tinyint,
 @Method varchar(max),@Key uniqueidentifier,@RequireFresh bit,@Deadline datetime2(7) OUTPUT
AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>1 THROW 59501,'CIMD authority unavailable.',1;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@held bigint;
 SELECT @held=Revision FROM dbo.AiExternalApplication WITH(HOLDLOCK) WHERE ApplicationId=@ApplicationId;
 SELECT @held=Id FROM dbo.AiTenantApplicationApproval WITH(HOLDLOCK) WHERE ShaleClientId=@t AND ApprovalId=@Approval;
 SELECT @held=Id FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@Integration;
 EXEC dbo.AiActorV1; EXEC dbo.AiEnabledV1;
 SET @Deadline=NULL;
 DECLARE @now datetime2(7)=SYSUTCDATETIME();
 SELECT @Deadline=d.Deadline FROM dbo.AiExternalApplication e
 JOIN dbo.AiTenantApplicationApproval p ON p.ApplicationId=e.ApplicationId AND p.ShaleClientId=@t AND p.ApprovalId=@Approval
 JOIN dbo.AiIntegrationRegistration i ON i.ShaleClientId=@t AND i.IntegrationId=p.IntegrationId
 JOIN dbo.UserSessions w WITH(HOLDLOCK) ON w.ShaleClientId=@t AND w.UserId=@u AND w.SessionId=@Web
 JOIN dbo.AiCimdPublicationV1 m WITH(HOLDLOCK) ON m.ApplicationId=e.ApplicationId AND m.ApplicationRevision=e.Revision
 LEFT JOIN dbo.AiIntegrationClientKey k WITH(HOLDLOCK) ON k.ShaleClientId=@t AND k.IntegrationId=i.IntegrationId AND k.CredentialId=@Key
 CROSS APPLY(SELECT MIN(v.Expiry) Deadline FROM(VALUES(p.ExpiresAt),(i.ExpiresAt),(w.ExpiresAt),(COALESCE(k.ExpiresAt,p.ExpiresAt)))v(Expiry))d
 WHERE e.ApplicationId=@ApplicationId AND e.Revision=@Revision AND e.MetadataDigest=@Digest AND (e.Source='CIMD' OR (e.Source='DCR' AND EXISTS(SELECT 1 FROM dbo.AiDcrMetadataV2 dm WHERE dm.ApplicationId=e.ApplicationId AND dm.ApplicationRevision=e.Revision AND dm.MetadataDigest=e.MetadataDigest) AND EXISTS(SELECT 1 FROM dbo.AiExternalControlV2 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1))) AND e.Disabled=0 AND (e.Source<>'DCR' OR EXISTS(SELECT 1 FROM dbo.AiDcrMetadataV2 ds WHERE ds.ApplicationId=e.ApplicationId AND ds.ApplicationRevision=e.Revision AND (ds.ScopeMask & @Scopes)=@Scopes))
 AND e.ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.ExternalClientId)=DATALENGTH(@External)
 AND e.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.Issuer)=DATALENGTH(@Issuer)
 AND e.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.Resource)=DATALENGTH(@Resource)
 AND p.ApplicationRevision=@Revision AND p.RowVer=@ApprovalVersion AND DATALENGTH(@ApprovalVersion)=8 AND p.State='APPROVED' AND p.ClosedAt IS NULL
 AND p.IntegrationId=@Integration AND @Scopes BETWEEN 1 AND 3 AND (p.ScopeMask & @Scopes)=@Scopes
 AND i.Status='SUSPENDED' AND i.Revision=1 AND i.RevokedAt IS NULL AND (i.ScopeMask & @Scopes)=@Scopes
 AND i.Issuer=e.Issuer AND DATALENGTH(i.Issuer)=DATALENGTH(e.Issuer) AND i.Resource=e.Resource AND DATALENGTH(i.Resource)=DATALENGTH(e.Resource)
 AND w.ClientType='WEB' AND w.RevokedAt IS NULL
 AND (@RequireFresh=0 OR m.FreshUntil>@now)
 AND ((@Method='none' AND DATALENGTH(@Method)=4 AND i.ClientType='PUBLIC' AND @Key IS NULL AND (e.MethodMask & 1)=1)
 OR (@Method='private_key_jwt' AND DATALENGTH(@Method)=15 AND i.ClientType='CONFIDENTIAL' AND (e.MethodMask & 2)=2 AND @Key IS NOT NULL
 AND k.RevokedAt IS NULL AND k.Algorithm='RS256' AND k.ValidFrom<=@now AND k.ExpiresAt>@now
 AND EXISTS(SELECT 1 FROM dbo.AiCimdKeyBindingV1 b WHERE b.ShaleClientId=@t AND b.IntegrationId=@Integration AND b.ApplicationId=@ApplicationId AND b.ApplicationRevision=@Revision AND b.CredentialId=@Key)))
 AND EXISTS(SELECT 1 FROM dbo.AiCimdProofControlV1 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1)
 AND EXISTS(SELECT 1 FROM dbo.AiCimdOAuthControlV1 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1);
 IF @Deadline IS NULL OR @Deadline<=SYSUTCDATETIME() THROW 59501,'CIMD authority unavailable.',1;
END;
GO
CREATE PROCEDURE dbo.AiExternalBeginOAuthV2
 @TransactionId uniqueidentifier,@WebSessionId uniqueidentifier,@External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),
 @RedirectUri nvarchar(max),@Challenge varchar(max),@ClientState nvarchar(max),@ScopeMask tinyint,@IntentHash varbinary(max)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD browser lease required.',1;
 BEGIN TRY
 BEGIN TRANSACTION; EXEC dbo.AiLockV1;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),
 @application uniqueidentifier,@revision bigint,@approval uniqueidentifier,@av binary(8),@digest binary(32),@integration uniqueidentifier,
 @method varchar(16),@key uniqueidentifier,@client varchar(64),@rv binary(8),@deadline datetime2(7),@redirects nvarchar(max),@keys nvarchar(max);
 IF @TransactionId IS NULL OR @IntentHash IS NULL OR DATALENGTH(@IntentHash)<>32 OR @ScopeMask IS NULL OR @ScopeMask NOT BETWEEN 1 AND 3
 OR @ClientState IS NULL OR DATALENGTH(@ClientState) NOT BETWEEN 2 AND 1024 OR @Challenge IS NULL OR DATALENGTH(@Challenge)<>43
 OR @Challenge COLLATE Latin1_General_100_BIN2 LIKE '%[^A-Za-z0-9_-]%'
 OR @External IS NULL OR DATALENGTH(@External) NOT BETWEEN 2 AND 4096 OR @RedirectUri IS NULL OR DATALENGTH(@RedirectUri) NOT BETWEEN 2 AND 4096
 THROW 59501,'CIMD request unavailable.',1;
 SELECT @application=ApplicationId,@revision=Revision,@digest=MetadataDigest FROM dbo.AiExternalApplication e WITH(HOLDLOCK)
 WHERE ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(ExternalClientId)=DATALENGTH(@External)
 AND Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Issuer)=DATALENGTH(@Issuer)
 AND Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Resource)=DATALENGTH(@Resource) AND (e.Source='CIMD' OR (e.Source='DCR' AND EXISTS(SELECT 1 FROM dbo.AiDcrMetadataV2 dm WHERE dm.ApplicationId=e.ApplicationId AND dm.ApplicationRevision=e.Revision AND dm.MetadataDigest=e.MetadataDigest) AND EXISTS(SELECT 1 FROM dbo.AiExternalControlV2 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1))) AND Disabled=0;
 SELECT @approval=ApprovalId,@av=RowVer,@integration=IntegrationId FROM dbo.AiTenantApplicationApproval WITH(HOLDLOCK)
 WHERE ShaleClientId=@t AND ApplicationId=@application AND ApplicationRevision=@revision AND State='APPROVED' AND ClosedAt IS NULL;
 SELECT @client=ClientId,@rv=RowVer,@method=CASE WHEN ClientType='CONFIDENTIAL' THEN 'private_key_jwt' ELSE 'none' END
 FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@integration;
 SELECT @redirects=Redirects,@keys=SigningKeys FROM dbo.AiCimdPublicationV1 WITH(HOLDLOCK) WHERE ApplicationId=@application AND ApplicationRevision=@revision AND FreshUntil>SYSUTCDATETIME();
 -- SQL independently validates exact callback; the Java adapter applies the existing canonical URI policy.
 IF NOT EXISTS(SELECT 1 FROM OPENJSON(@redirects) r WHERE
 (((r.value COLLATE Latin1_General_100_BIN2)=@RedirectUri AND DATALENGTH((r.value COLLATE Latin1_General_100_BIN2))=DATALENGTH(@RedirectUri)) OR (@method='none' AND
 ((LEFT((r.value COLLATE Latin1_General_100_BIN2),17)=N'http://127.0.0.1:' AND LEFT(@RedirectUri,17)=N'http://127.0.0.1:') OR
 (LEFT((r.value COLLATE Latin1_General_100_BIN2),13)=N'http://[::1]:' AND LEFT(@RedirectUri,13)=N'http://[::1]:')) AND
 CHARINDEX(N'/',@RedirectUri,8)>0 AND CHARINDEX(N'/',(r.value COLLATE Latin1_General_100_BIN2),8)>0 AND
 SUBSTRING(@RedirectUri,CHARINDEX(N'/',@RedirectUri,8),2048)=SUBSTRING((r.value COLLATE Latin1_General_100_BIN2),CHARINDEX(N'/',(r.value COLLATE Latin1_General_100_BIN2),8),2048) AND
 TRY_CONVERT(int,SUBSTRING(@RedirectUri,CASE WHEN LEFT(@RedirectUri,17)=N'http://127.0.0.1:' THEN 18 ELSE 14 END,
 CASE WHEN CHARINDEX(N'/',@RedirectUri,8)>CASE WHEN LEFT(@RedirectUri,17)=N'http://127.0.0.1:' THEN 18 ELSE 14 END THEN CHARINDEX(N'/',@RedirectUri,8)-CASE WHEN LEFT(@RedirectUri,17)=N'http://127.0.0.1:' THEN 18 ELSE 14 END ELSE 0 END)) BETWEEN 1 AND 65535)))
 THROW 58502,'OAuth redirect unavailable.',1;
 EXEC dbo.AiActorV1; EXEC dbo.AiEnabledV1;
 DECLARE @now datetime2(7)=SYSUTCDATETIME();
 IF @method='private_key_jwt' BEGIN
 DECLARE @kid nvarchar(128),@modulus varbinary(512),@expiry datetime2(7);
 SELECT TOP(1) @kid=kid,@modulus=CONVERT(varbinary(512),modulus,2) FROM OPENJSON(@keys) WITH(kid nvarchar(128),modulus varchar(1024),exponent int) WHERE exponent=65537 ORDER BY kid COLLATE Latin1_General_100_BIN2;
 IF @kid IS NULL THROW 59501,'CIMD key unavailable.',1;
 SELECT @expiry=ExpiresAt FROM dbo.AiTenantApplicationApproval WHERE ShaleClientId=@t AND ApprovalId=@approval;
 SELECT @key=CredentialId FROM dbo.AiIntegrationClientKey WHERE ShaleClientId=@t AND IntegrationId=@integration AND Modulus=@modulus AND RevokedAt IS NULL AND ExpiresAt>@now;
 IF @key IS NULL BEGIN
 SET @key=NEWID();
 INSERT dbo.AiIntegrationClientKey(ShaleClientId,IntegrationId,CredentialId,Modulus,Exponent,CreatedAt,CreatedByUserId,ValidFrom,ExpiresAt)
 VALUES(@t,@integration,@key,@modulus,65537,@now,@u,@now,@expiry);
 DECLARE @keyRow bigint=SCOPE_IDENTITY();
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,'AI_CLIENT_KEY',@keyRow,'CREATED',NEWID(),'SHALE_SERVICE',NULL);
 IF @@ROWCOUNT<>1 THROW 59501,'CIMD key audit unavailable.',1;
 END;
 IF NOT EXISTS(SELECT 1 FROM dbo.AiCimdKeyBindingV1 WHERE ShaleClientId=@t AND IntegrationId=@integration AND ApplicationRevision=@revision AND Kid=@kid)
 INSERT dbo.AiCimdKeyBindingV1 VALUES(@t,@integration,@application,@revision,@kid,@key);
 END;
 EXEC dbo.AiExternalOAuthAuthorityV2 @application,@revision,@approval,@av,@digest,@External,@Issuer,@Resource,@integration,@WebSessionId,@ScopeMask,@method,@key,1,@deadline OUTPUT;
 IF @deadline<=DATEADD(minute,5,@now) THROW 59501,'CIMD approval deadline too short.',1;
 DECLARE @grantExpiry datetime2(7)=CASE WHEN @deadline<DATEADD(hour,1,@now) THEN @deadline ELSE DATEADD(hour,1,@now) END;
 IF (SELECT COUNT(*) FROM dbo.AiCimdAuthorizationV1 WHERE ShaleClientId=@t AND UserId=@u AND IssuedAt IS NULL AND DeniedAt IS NULL AND IntentExpiresAt>@now)>=8 THROW 59501,'CIMD pending capacity unavailable.',1;
 INSERT dbo.AiConsentIntent(ShaleClientId,IntegrationId,UserId,IntentHash,ScopeMask,CreatedAt,ExpiresAt,GrantExpiresAt)
 VALUES(@t,@integration,@u,@IntentHash,@ScopeMask,@now,DATEADD(minute,5,@now),@grantExpiry);
 INSERT dbo.AiCimdAuthorizationV1(TransactionId,ShaleClientId,IntegrationId,UserId,WebSessionId,IntentHash,ClientId,Issuer,Resource,RedirectUri,Challenge,ClientState,
 RequestedScopeMask,RegistrationVersion,CredentialId,CreatedAt,IntentExpiresAt,ApplicationId,ApplicationRevision,ApprovalId,ApprovalVersion,MetadataDigest,ExternalClientId,AuthMethod)
 VALUES(@TransactionId,@t,@integration,@u,@WebSessionId,@IntentHash,@client,@Issuer,@Resource,@RedirectUri,@Challenge,@ClientState,@ScopeMask,@rv,@key,@now,DATEADD(minute,5,@now),@application,@revision,@approval,@av,@digest,@External,@method);
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiExternalApproveOAuthV2
@TransactionId uniqueidentifier,@WebSessionId uniqueidentifier,@ScopeMask tinyint
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD lease required.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@now datetime2(7)=SYSUTCDATETIME();
 EXEC dbo.AiExternalValidateTransactionV2 @TransactionId,@WebSessionId;

 IF NOT EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@WebSessionId
 AND ClientType='WEB' AND RevokedAt IS NULL AND ExpiresAt>@now) THROW 58502,'WEB session unavailable.',1;

 DECLARE @i uniqueidentifier,@scopes tinyint,@created datetime2(7),@v binary(8),@key uniqueidentifier;
 SELECT @i=IntegrationId,@scopes=RequestedScopeMask,@created=CreatedAt,@v=RegistrationVersion,@key=CredentialId
 FROM dbo.AiCimdAuthorizationV1 WITH(UPDLOCK,HOLDLOCK) WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u
 AND WebSessionId=@WebSessionId AND DeniedAt IS NULL;
 IF @i IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND Status='SUSPENDED' AND Revision=1 AND RevokedAt IS NULL AND ExpiresAt>@now AND RowVer=@v AND (ScopeMask & @scopes)=@scopes)
 OR (@key IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationClientKey WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND CredentialId=@key AND RevokedAt IS NULL AND ValidFrom<=@now AND ExpiresAt>@now))
 THROW 58502,'OAuth authority unavailable.',1;

 IF @ScopeMask IS NULL OR @ScopeMask NOT BETWEEN 1 AND 3 OR (@ScopeMask & @scopes)<>@ScopeMask THROW 58502,'Invalid consent scope.',1;
 UPDATE dbo.AiCimdAuthorizationV1 SET ApprovedScopeMask=@ScopeMask WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u
 AND ApprovedScopeMask IS NULL AND IntentExpiresAt>@now AND IssuedAt IS NULL AND DeniedAt IS NULL;
 IF @@ROWCOUNT<>1 THROW 58503,'Consent already used or expired.',1;

 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiExternalCompleteOAuthV2
@TransactionId uniqueidentifier,@WebSessionId uniqueidentifier,@ScopeMask tinyint,@CodeHash varbinary(32),@CorrelationId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD lease required.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@now datetime2(7)=SYSUTCDATETIME();
 EXEC dbo.AiExternalValidateTransactionV2 @TransactionId,@WebSessionId;

 IF NOT EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@WebSessionId
 AND ClientType='WEB' AND RevokedAt IS NULL AND ExpiresAt>@now) THROW 58502,'WEB session unavailable.',1;

 DECLARE @i uniqueidentifier,@scopes tinyint,@created datetime2(7),@v binary(8),@key uniqueidentifier;
 SELECT @i=IntegrationId,@scopes=RequestedScopeMask,@created=CreatedAt,@v=RegistrationVersion,@key=CredentialId
 FROM dbo.AiCimdAuthorizationV1 WITH(UPDLOCK,HOLDLOCK) WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u
 AND WebSessionId=@WebSessionId AND DeniedAt IS NULL;
 IF @i IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND Status='SUSPENDED' AND Revision=1 AND RevokedAt IS NULL AND ExpiresAt>@now AND RowVer=@v AND (ScopeMask & @scopes)=@scopes)
 OR (@key IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationClientKey WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND CredentialId=@key AND RevokedAt IS NULL AND ValidFrom<=@now AND ExpiresAt>@now))
 THROW 58502,'OAuth authority unavailable.',1;

 DECLARE @intent binary(32),@expiry datetime2(7),@grant uniqueidentifier,@id bigint;
 IF @CodeHash IS NULL OR DATALENGTH(@CodeHash)<>32 OR @CorrelationId IS NULL THROW 58502,'Invalid code binding.',1;
 SELECT @intent=a.IntentHash,@expiry=c.GrantExpiresAt FROM dbo.AiCimdAuthorizationV1 a
 JOIN dbo.AiConsentIntent c ON c.ShaleClientId=a.ShaleClientId AND c.IntentHash=a.IntentHash AND c.UserId=a.UserId
 WHERE a.TransactionId=@TransactionId AND a.ShaleClientId=@t AND a.UserId=@u AND a.ApprovedScopeMask=@ScopeMask
 AND a.IntentExpiresAt>@now AND a.IssuedAt IS NULL AND a.DeniedAt IS NULL AND c.ConsumedAt IS NULL AND c.ExpiresAt>@now;
 IF @intent IS NULL OR @expiry<=DATEADD(second,60,@now)
 OR EXISTS(SELECT 1 FROM dbo.AiDelegationGrant WHERE ShaleClientId=@t AND IntegrationId=@i AND UserId=@u AND RevokedAt>=@created)
 THROW 58503,'Stale or used consent.',1;
 UPDATE dbo.AiConsentIntent SET ConsumedAt=@now WHERE IntentHash=@intent AND ShaleClientId=@t AND UserId=@u AND ConsumedAt IS NULL;
 IF @@ROWCOUNT<>1 THROW 58503,'Consent already used.',1;
 INSERT dbo.AiDelegationGrant(ShaleClientId,IntegrationId,UserId,ScopeMask,ConsentedAt,ExpiresAt)
 VALUES(@t,@i,@u,@ScopeMask,@now,@expiry);
 SET @id=SCOPE_IDENTITY();
 SELECT @grant=GrantId FROM dbo.AiDelegationGrant WHERE Id=@id AND ShaleClientId=@t;
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,'AI_DELEGATION_GRANT',@id,'CREATED',@CorrelationId,'SHALE_SERVICE',CONCAT(N'{"AI_SCOPE_MASK":',@ScopeMask,N'}'));
 IF @@ROWCOUNT<>1 THROW 58504,'Consent audit unavailable.',1;
 UPDATE dbo.AiCimdAuthorizationV1 SET GrantId=@grant,CodeHash=@CodeHash,IssuedAt=@now,CodeExpiresAt=DATEADD(second,60,@now)
 WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u AND IssuedAt IS NULL;
 IF @@ROWCOUNT<>1 THROW 58503,'Code already completed.',1;

 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiExternalDenyOAuthV2
@TransactionId uniqueidentifier,@WebSessionId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD lease required.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@now datetime2(7)=SYSUTCDATETIME();
 EXEC dbo.AiExternalValidateTransactionV2 @TransactionId,@WebSessionId;

 IF NOT EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@WebSessionId
 AND ClientType='WEB' AND RevokedAt IS NULL AND ExpiresAt>@now) THROW 58502,'WEB session unavailable.',1;

 UPDATE dbo.AiConsentIntent SET ConsumedAt=@now WHERE ShaleClientId=@t AND UserId=@u AND ConsumedAt IS NULL
 AND IntentHash IN(SELECT IntentHash FROM dbo.AiCimdAuthorizationV1 WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u AND WebSessionId=@WebSessionId AND IssuedAt IS NULL);
 UPDATE dbo.AiCimdAuthorizationV1 SET DeniedAt=@now WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u AND WebSessionId=@WebSessionId AND IssuedAt IS NULL AND DeniedAt IS NULL;
 IF @@ROWCOUNT<>1 THROW 58503,'Consent unavailable.',1;

 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiExternalFindOAuthIntentV2
@IntentHash varbinary(32),@WebSessionId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD lease required.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@now datetime2(7)=SYSUTCDATETIME();
 DECLARE @TransactionId uniqueidentifier; SELECT @TransactionId=TransactionId FROM dbo.AiCimdAuthorizationV1 WHERE ShaleClientId=@t AND UserId=@u AND IntentHash=@IntentHash AND WebSessionId=@WebSessionId;
 EXEC dbo.AiExternalValidateTransactionV2 @TransactionId,@WebSessionId;

 IF NOT EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@WebSessionId
 AND ClientType='WEB' AND RevokedAt IS NULL AND ExpiresAt>@now) THROW 58502,'WEB session unavailable.',1;

 SELECT *, (SELECT GrantExpiresAt FROM dbo.AiConsentIntent c WHERE c.ShaleClientId=@t AND c.UserId=@u AND c.IntentHash=dbo.AiCimdAuthorizationV1.IntentHash) AS GrantExpiresAt FROM dbo.AiCimdAuthorizationV1 WHERE ShaleClientId=@t AND UserId=@u AND WebSessionId=@WebSessionId
 AND IntentHash=@IntentHash AND IntentExpiresAt>@now AND IssuedAt IS NULL AND DeniedAt IS NULL
 AND EXISTS(SELECT 1 FROM dbo.AiConsentIntent c WHERE c.IntentHash=@IntentHash AND c.ShaleClientId=@t AND c.UserId=@u AND c.ConsumedAt IS NULL);

 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiExternalValidateTransactionV2 @TransactionId uniqueidentifier,@Web uniqueidentifier
AS BEGIN
 SET NOCOUNT ON;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),
 @a uniqueidentifier,@r bigint,@p uniqueidentifier,@v binary(8),@d binary(32),@e nvarchar(2048),@issuer nvarchar(256),@resource nvarchar(256),
 @i uniqueidentifier,@sc tinyint,@m varchar(16),@k uniqueidentifier,@deadline datetime2(7);
 SELECT @a=ApplicationId,@r=ApplicationRevision,@p=ApprovalId,@v=ApprovalVersion,@d=MetadataDigest,@e=ExternalClientId,@issuer=Issuer,@resource=Resource,
 @i=IntegrationId,@sc=RequestedScopeMask,@m=AuthMethod,@k=CredentialId FROM dbo.AiCimdAuthorizationV1
 WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u AND WebSessionId=@Web AND DeniedAt IS NULL;
 EXEC dbo.AiExternalOAuthAuthorityV2 @a,@r,@p,@v,@d,@e,@issuer,@resource,@i,@Web,@sc,@m,@k,1,@deadline OUTPUT;
END;
GO
CREATE PROCEDURE dbo.AiExternalIssueV2
 @TransactionId uniqueidentifier,@ClientId nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@GrantId uniqueidentifier,
 @KeyId uniqueidentifier,@Scopes tinyint,@AccessHash varbinary(max),@RefreshHash varbinary(max),@CorrelationId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 -- This seam is callable only within the validated exchange transaction. It never accepts a caller tenant/user.
 IF @@TRANCOUNT<>1 THROW 58702,'Validated exchange transaction required.',1;
 EXEC dbo.AiLockV1; EXEC dbo.AiActorV1; EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@now datetime2(7)=SYSUTCDATETIME();
 IF @AccessHash IS NULL OR @RefreshHash IS NULL OR DATALENGTH(@AccessHash)<>32 OR DATALENGTH(@RefreshHash)<>32 OR @AccessHash=@RefreshHash
 OR @CorrelationId IS NULL OR @Scopes IS NULL OR @Scopes NOT BETWEEN 1 AND 3 THROW 58702,'Credential binding unavailable.',1;
 DECLARE @i uniqueidentifier,@deadline datetime2(7),@consent datetime2(7),@s uniqueidentifier=NEWID(),@revision bigint;
 SELECT @i=a.IntegrationId,@consent=g.ConsentedAt,@deadline=d.Deadline
 FROM dbo.AiCimdAuthorizationV1 a WITH(UPDLOCK,HOLDLOCK)
 JOIN dbo.AiIntegrationRegistration r WITH(HOLDLOCK) ON r.ShaleClientId=a.ShaleClientId AND r.IntegrationId=a.IntegrationId
 JOIN dbo.AiDelegationGrant g WITH(HOLDLOCK) ON g.ShaleClientId=a.ShaleClientId AND g.GrantId=a.GrantId AND g.IntegrationId=a.IntegrationId AND g.UserId=a.UserId
 JOIN dbo.UserSessions w WITH(HOLDLOCK) ON w.ShaleClientId=a.ShaleClientId AND w.UserId=a.UserId AND w.SessionId=a.WebSessionId
 LEFT JOIN dbo.AiIntegrationClientKey k WITH(HOLDLOCK) ON k.ShaleClientId=a.ShaleClientId AND k.IntegrationId=a.IntegrationId AND k.CredentialId=a.CredentialId
 CROSS APPLY(SELECT MIN(v.Expiry) Deadline FROM(VALUES(g.ExpiresAt),(r.ExpiresAt),(w.ExpiresAt),(DATEADD(hour,24,g.ConsentedAt)),(COALESCE(k.ExpiresAt,g.ExpiresAt)))v(Expiry))d
 WHERE a.TransactionId=@TransactionId AND a.ShaleClientId=@t AND a.UserId=@u AND a.GrantId=@GrantId AND a.ConsumedAt IS NOT NULL
 AND a.ApprovedScopeMask=@Scopes AND a.DeniedAt IS NULL AND r.RowVer=a.RegistrationVersion
 AND a.ExternalClientId=@ClientId AND DATALENGTH(a.ExternalClientId)=DATALENGTH(@ClientId) AND r.ClientId=a.ClientId
 AND a.Issuer=@Issuer AND DATALENGTH(a.Issuer)=DATALENGTH(@Issuer) AND r.Issuer=a.Issuer
 AND a.Resource=@Resource AND DATALENGTH(a.Resource)=DATALENGTH(@Resource) AND r.Resource=a.Resource
 AND r.Status='SUSPENDED' AND r.Revision=1 AND r.RevokedAt IS NULL AND r.ExpiresAt>@now AND (r.ScopeMask & @Scopes)=@Scopes
 AND g.RevokedAt IS NULL AND g.ExpiresAt>@now AND (g.ScopeMask & @Scopes)=@Scopes
 AND w.ClientType='WEB' AND w.RevokedAt IS NULL AND w.ExpiresAt>@now
 AND ((r.ClientType='PUBLIC' AND @KeyId IS NULL AND a.CredentialId IS NULL) OR
 (r.ClientType='CONFIDENTIAL' AND @KeyId=a.CredentialId AND k.RevokedAt IS NULL AND k.ValidFrom<=@now AND k.ExpiresAt>@now));
 SELECT @deadline=CASE WHEN p.ExpiresAt<@deadline THEN p.ExpiresAt ELSE @deadline END FROM dbo.AiCimdAuthorizationV1 a JOIN dbo.AiTenantApplicationApproval p ON p.ShaleClientId=a.ShaleClientId AND p.ApprovalId=a.ApprovalId WHERE a.TransactionId=@TransactionId AND a.ShaleClientId=@t;
 IF @i IS NULL OR @deadline<=SYSUTCDATETIME() THROW 58702,'Exchange authority unavailable.',1;
 SELECT @revision=Revision FROM dbo.AiProductCapability WHERE Id=1 AND Enabled=1;
 DECLARE @accessExpiry datetime2(7)=CASE WHEN @deadline<DATEADD(minute,5,@now) THEN @deadline ELSE DATEADD(minute,5,@now) END,
 @refreshExpiry datetime2(7)=CASE WHEN @deadline<DATEADD(hour,8,@now) THEN @deadline ELSE DATEADD(hour,8,@now) END;
 INSERT dbo.AiDelegatedSession(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,CredentialId,ScopeMask,IssuedAt,ExpiresAt,IdleExpiresAt,
 TransactionId,ClientId,Issuer,Resource,ConsentAt,CapabilityRevision)
 VALUES(@t,@i,@u,@GrantId,@s,@KeyId,@Scopes,@now,@deadline,@refreshExpiry,NULL,(SELECT ClientId FROM dbo.AiCimdAuthorizationV1 WHERE TransactionId=@TransactionId),@Issuer,@Resource,@consent,@revision);

 INSERT dbo.AiCimdFamilyV1(ShaleClientId,SessionId,IntegrationId,GrantId,UserId,TransactionId,WebSessionId,ApplicationId,ApplicationRevision,ApprovalId,ApprovalVersion,MetadataDigest,ExternalClientId,ClientId,Issuer,Resource,CredentialId,AuthMethod,RedirectUri,Challenge,CodeHash,CodeIssuedAt,CodeExpiresAt,ConsumedAt)
 SELECT ShaleClientId,@s,IntegrationId,GrantId,UserId,TransactionId,WebSessionId,ApplicationId,ApplicationRevision,ApprovalId,ApprovalVersion,MetadataDigest,ExternalClientId,ClientId,Issuer,Resource,CredentialId,AuthMethod,RedirectUri,Challenge,CodeHash,IssuedAt,CodeExpiresAt,ConsumedAt
 FROM dbo.AiCimdAuthorizationV1 WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u;
 IF @@ROWCOUNT<>1 THROW 59501,'CIMD family receipt unavailable.',1;
 INSERT dbo.AiCimdCredentialV1(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,CredentialHash,Kind,ScopeMask,Generation,IssuedAt,ExpiresAt)
 VALUES(@t,@i,@u,@GrantId,@s,@AccessHash,'ACCESS',@Scopes,0,@now,@accessExpiry),(@t,@i,@u,@GrantId,@s,@RefreshHash,'REFRESH',@Scopes,0,@now,@refreshExpiry);
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 SELECT @t,@u,'AI_DELEGATED_SESSION',Id,'CREATED',@CorrelationId,'SHALE_SERVICE',CONCAT(N'{"AI_SCOPE_MASK":',@Scopes,N'}')
 FROM dbo.AiDelegatedSession WHERE ShaleClientId=@t AND SessionId=@s;
 IF @@ROWCOUNT<>1 THROW 58703,'Credential audit unavailable.',1;
 SELECT @now AS IssuedAt,@accessExpiry AS AccessExpiresAt,@Scopes AS ScopeMask;
END;
GO
CREATE PROCEDURE dbo.AiExternalRedeemV2
@CodeHash varbinary(max),@ClientId nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),
 @RedirectUri nvarchar(max),@Challenge varchar(max),@KeyId uniqueidentifier,@JtiHash varbinary(max),
 @AssertionIssuedAt datetime2(7),@AssertionExpiresAt datetime2(7),@CorrelationId uniqueidentifier,@AccessHash varbinary(max),@RefreshHash varbinary(max)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD lease required.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@now datetime2(7)=SYSUTCDATETIME();

 IF @CodeHash IS NULL OR DATALENGTH(@CodeHash)<>32 OR @CorrelationId IS NULL
 OR DATALENGTH(@ClientId) NOT BETWEEN 2 AND 4096 OR DATALENGTH(@Issuer) NOT BETWEEN 1 AND 512
 OR DATALENGTH(@Resource) NOT BETWEEN 1 AND 512 OR DATALENGTH(@RedirectUri) NOT BETWEEN 1 AND 4096
 OR DATALENGTH(@Challenge)<>43 THROW 58602,'Invalid exchange binding.',1;
 DECLARE @WebSessionId uniqueidentifier;
 SELECT @WebSessionId=WebSessionId FROM dbo.AiCimdAuthorizationV1 WHERE ShaleClientId=@t AND UserId=@u AND CodeHash=@CodeHash;
 IF NOT EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@WebSessionId
 AND ClientType='WEB' AND RevokedAt IS NULL AND ExpiresAt>@now) THROW 58602,'WEB authority unavailable.',1;
 DECLARE @TransactionId uniqueidentifier;
 SELECT @TransactionId=TransactionId FROM dbo.AiCimdAuthorizationV1 WITH(UPDLOCK,HOLDLOCK)
 WHERE ShaleClientId=@t AND UserId=@u AND WebSessionId=@WebSessionId AND CodeHash=@CodeHash AND ConsumedAt IS NULL
 AND CodeExpiresAt>@now AND ExternalClientId=@ClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(ExternalClientId)=DATALENGTH(@ClientId)
 AND Issuer=@Issuer AND DATALENGTH(Issuer)=DATALENGTH(@Issuer) AND Resource=@Resource AND DATALENGTH(Resource)=DATALENGTH(@Resource)
 AND RedirectUri=@RedirectUri AND DATALENGTH(RedirectUri)=DATALENGTH(@RedirectUri) AND Challenge=@Challenge COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Challenge)=DATALENGTH(@Challenge);

 EXEC dbo.AiExternalValidateTransactionV2 @TransactionId,@WebSessionId;
 DECLARE @i uniqueidentifier,@scopes tinyint,@created datetime2(7),@v binary(8),@key uniqueidentifier;
 SELECT @i=IntegrationId,@scopes=RequestedScopeMask,@created=CreatedAt,@v=RegistrationVersion,@key=CredentialId
 FROM dbo.AiCimdAuthorizationV1 WITH(UPDLOCK,HOLDLOCK) WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u
 AND WebSessionId=@WebSessionId AND DeniedAt IS NULL;
 IF @i IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND Status='SUSPENDED' AND Revision=1 AND RevokedAt IS NULL AND ExpiresAt>@now AND RowVer=@v AND (ScopeMask & @scopes)=@scopes)
 OR (@key IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationClientKey WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i
 AND CredentialId=@key AND RevokedAt IS NULL AND ValidFrom<=@now AND ExpiresAt>@now))
 THROW 58502,'OAuth authority unavailable.',1;

 IF NOT EXISTS(SELECT 1 FROM dbo.AiCimdAuthorizationV1 a JOIN dbo.AiDelegationGrant g WITH(HOLDLOCK)
 ON g.ShaleClientId=a.ShaleClientId AND g.GrantId=a.GrantId AND g.IntegrationId=a.IntegrationId AND g.UserId=a.UserId
 WHERE a.TransactionId=@TransactionId AND a.ShaleClientId=@t AND g.RevokedAt IS NULL AND g.ExpiresAt>@now
 AND (g.ScopeMask & a.ApprovedScopeMask)=a.ApprovedScopeMask) THROW 58502,'Grant unavailable.',1;
 SET @now=SYSUTCDATETIME();
 DECLARE @type varchar(16),@deadline datetime2(7),@grantId uniqueidentifier,@grantRow bigint;
 SELECT @type=ClientType FROM dbo.AiIntegrationRegistration WHERE ShaleClientId=@t AND IntegrationId=@i;
 IF (@type='PUBLIC' AND (@KeyId IS NOT NULL OR @JtiHash IS NOT NULL OR @AssertionIssuedAt IS NOT NULL OR @AssertionExpiresAt IS NOT NULL))
 OR (@type='CONFIDENTIAL' AND (@KeyId IS NULL OR @key IS NULL OR @KeyId<>@key OR @JtiHash IS NULL OR DATALENGTH(@JtiHash)<>32
 OR @AssertionIssuedAt IS NULL OR @AssertionExpiresAt IS NULL OR @AssertionIssuedAt>@now OR @AssertionExpiresAt<=@now
 OR @AssertionExpiresAt<=@AssertionIssuedAt OR @AssertionExpiresAt>DATEADD(second,60,@AssertionIssuedAt)))
 THROW 58602,'Client authentication unavailable.',1;
 IF @type='CONFIDENTIAL' BEGIN
 DECLARE @ja uniqueidentifier,@jr bigint,@ji uniqueidentifier,@jk nvarchar(128); SELECT @ja=ApplicationId,@jr=ApplicationRevision,@ji=IntegrationId FROM dbo.AiCimdAuthorizationV1 WHERE ShaleClientId=@t AND TransactionId=@TransactionId; SELECT @jk=Kid FROM dbo.AiCimdKeyBindingV1 WHERE ShaleClientId=@t AND IntegrationId=@ji AND ApplicationId=@ja AND ApplicationRevision=@jr AND CredentialId=@KeyId; EXEC dbo.AiExternalReserveAssertionV2 @ja,@jr,@jk,@JtiHash,@AssertionIssuedAt,@AssertionExpiresAt;

 -- Bounded cleanup under the same exclusive tenant lock. No valid-window record can be removed.
 DELETE TOP(256) FROM dbo.AiCimdAssertionReplayV1 WHERE ShaleClientId=@t AND ExpiresAt<=@now;
 INSERT dbo.AiCimdAssertionReplayV1(ShaleClientId,IntegrationId,CredentialId,JtiHash,AcceptedAt,ExpiresAt)
 VALUES(@t,@i,@KeyId,@JtiHash,@now,@AssertionExpiresAt);
 END;
 SELECT @grantId=g.GrantId,@grantRow=g.Id,@deadline=d.Deadline FROM dbo.AiCimdAuthorizationV1 a
 JOIN dbo.AiDelegationGrant g ON g.ShaleClientId=a.ShaleClientId AND g.GrantId=a.GrantId AND g.UserId=a.UserId AND g.IntegrationId=a.IntegrationId
 JOIN dbo.AiIntegrationRegistration r ON r.ShaleClientId=a.ShaleClientId AND r.IntegrationId=a.IntegrationId
 JOIN dbo.UserSessions w ON w.ShaleClientId=a.ShaleClientId AND w.UserId=a.UserId AND w.SessionId=a.WebSessionId
 CROSS APPLY(SELECT MIN(v.Expiry) Deadline FROM(VALUES(g.ExpiresAt),(r.ExpiresAt),(w.ExpiresAt),
 (CASE WHEN @key IS NULL THEN g.ExpiresAt ELSE (SELECT ExpiresAt FROM dbo.AiIntegrationClientKey WHERE ShaleClientId=@t AND IntegrationId=@i AND CredentialId=@key) END))v(Expiry))d
 WHERE a.TransactionId=@TransactionId AND a.ShaleClientId=@t AND a.UserId=@u;
 IF @deadline IS NULL OR @deadline<=SYSUTCDATETIME() OR (@key IS NOT NULL AND @AssertionExpiresAt<=SYSUTCDATETIME()) THROW 58602,'Exchange deadline elapsed.',1;
 UPDATE dbo.AiCimdAuthorizationV1 SET ConsumedAt=@now WHERE TransactionId=@TransactionId AND ShaleClientId=@t AND UserId=@u AND ConsumedAt IS NULL AND CodeExpiresAt>SYSUTCDATETIME();
 IF @@ROWCOUNT<>1 THROW 58503,'Code unavailable.',1;
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,'AI_DELEGATION_GRANT',@grantRow,'UPDATED',@CorrelationId,'SHALE_SERVICE',CONCAT(N'{"AI_SCOPE_MASK":',
 (SELECT ApprovedScopeMask FROM dbo.AiCimdAuthorizationV1 WHERE TransactionId=@TransactionId AND ShaleClientId=@t),N'}'));
 IF @@ROWCOUNT<>1 THROW 58603,'Exchange audit unavailable.',1;
 DECLARE @scopesApproved tinyint; SELECT @scopesApproved=ApprovedScopeMask FROM dbo.AiCimdAuthorizationV1 WHERE TransactionId=@TransactionId AND ShaleClientId=@t;
 EXEC dbo.AiExternalIssueV2 @TransactionId,@ClientId,@Issuer,@Resource,@grantId,@KeyId,@scopesApproved,@AccessHash,@RefreshHash,@CorrelationId;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiExternalRefreshV2
 @Hash varbinary(max),@ClientId nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@KeyId uniqueidentifier,
 @JtiHash varbinary(max),@AssertionIssuedAt datetime2(7),@AssertionExpiresAt datetime2(7),@AccessHash varbinary(max),@RefreshHash varbinary(max),@CorrelationId uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD lease required.',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1; EXEC dbo.AiActorV1; EXEC dbo.AiEnabledV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@now datetime2(7)=SYSUTCDATETIME();
 IF @Hash IS NULL OR DATALENGTH(@Hash)<>32 OR @AccessHash IS NULL OR DATALENGTH(@AccessHash)<>32 OR @RefreshHash IS NULL OR DATALENGTH(@RefreshHash)<>32
 OR @Hash=@AccessHash OR @Hash=@RefreshHash OR @AccessHash=@RefreshHash OR @CorrelationId IS NULL
 OR @ClientId IS NULL OR DATALENGTH(@ClientId) NOT BETWEEN 2 AND 4096 OR @Issuer IS NULL OR DATALENGTH(@Issuer) NOT BETWEEN 1 AND 512
 OR @Resource IS NULL OR DATALENGTH(@Resource) NOT BETWEEN 1 AND 512 THROW 58702,'Credential unavailable.',1;
 DECLARE @s uniqueidentifier,@id bigint,@sessionRow bigint,@i uniqueidentifier,@g uniqueidentifier,@scopes tinyint,@generation int,
 @consumed datetime2(7),@credentialExpiry datetime2(7),@deadline datetime2(7),@idle datetime2(7),@revoked datetime2(7),@type varchar(16),@key uniqueidentifier,@revision bigint;
 SELECT @s=s.SessionId,@sessionRow=s.Id,@id=c.Id,@i=s.IntegrationId,@g=s.GrantId,@scopes=s.ScopeMask,@generation=c.Generation,
 @consumed=c.ConsumedAt,@credentialExpiry=c.ExpiresAt,@deadline=s.ExpiresAt,@idle=s.IdleExpiresAt,@revoked=s.RevokedAt,@key=s.CredentialId,@revision=s.CapabilityRevision
 FROM dbo.AiCimdCredentialV1 c WITH(UPDLOCK,HOLDLOCK)
 JOIN dbo.AiDelegatedSession s WITH(UPDLOCK,HOLDLOCK) ON s.ShaleClientId=c.ShaleClientId AND s.SessionId=c.SessionId AND s.GrantId=c.GrantId AND s.IntegrationId=c.IntegrationId AND s.UserId=c.UserId
 WHERE c.ShaleClientId=@t AND c.UserId=@u AND c.CredentialHash=@Hash AND c.Kind='REFRESH' AND c.ScopeMask=s.ScopeMask
 AND EXISTS(SELECT 1 FROM dbo.AiCimdFamilyV1 f WHERE f.ShaleClientId=s.ShaleClientId AND f.SessionId=s.SessionId AND f.ExternalClientId=@ClientId COLLATE Latin1_General_100_BIN2 AND DATALENGTH(f.ExternalClientId)=DATALENGTH(@ClientId))
 AND s.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.Issuer)=DATALENGTH(@Issuer)
 AND s.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.Resource)=DATALENGTH(@Resource);
 SELECT @type=ClientType FROM dbo.AiIntegrationRegistration WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i;
 IF @s IS NULL OR @type IS NULL OR (@type='PUBLIC' AND (@KeyId IS NOT NULL OR @JtiHash IS NOT NULL OR @AssertionIssuedAt IS NOT NULL OR @AssertionExpiresAt IS NOT NULL))
 OR (@type='CONFIDENTIAL' AND (@KeyId IS NULL OR @key IS NULL OR @KeyId<>@key OR @JtiHash IS NULL OR DATALENGTH(@JtiHash)<>32
 OR @AssertionIssuedAt IS NULL OR @AssertionExpiresAt IS NULL OR @AssertionIssuedAt>@now OR @AssertionExpiresAt<=@now
 OR @AssertionExpiresAt<=@AssertionIssuedAt OR @AssertionExpiresAt>DATEADD(second,60,@AssertionIssuedAt)
 OR NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationClientKey WITH(HOLDLOCK) WHERE ShaleClientId=@t AND IntegrationId=@i AND CredentialId=@key AND RevokedAt IS NULL AND ValidFrom<=@now AND ExpiresAt>@now)))
 THROW 58702,'Client unavailable.',1;

 DECLARE @fa uniqueidentifier,@fr bigint,@fp uniqueidentifier,@fv binary(8),@fd binary(32),@fe nvarchar(2048),@fw uniqueidentifier,@fm varchar(16),@currentDeadline datetime2(7);
 SELECT @fa=ApplicationId,@fr=ApplicationRevision,@fp=ApprovalId,@fv=ApprovalVersion,@fd=MetadataDigest,@fe=ExternalClientId,@fw=WebSessionId,@fm=AuthMethod
 FROM dbo.AiCimdFamilyV1 WHERE ShaleClientId=@t AND SessionId=@s AND UserId=@u;
 EXEC dbo.AiExternalOAuthAuthorityV2 @fa,@fr,@fp,@fv,@fd,@fe,@Issuer,@Resource,@i,@fw,@scopes,@fm,@key,1,@currentDeadline OUTPUT;
 IF @type='CONFIDENTIAL' BEGIN
 DECLARE @ja uniqueidentifier,@jr bigint,@ji uniqueidentifier,@jk nvarchar(128); SELECT @ja=ApplicationId,@jr=ApplicationRevision,@ji=IntegrationId FROM dbo.AiCimdFamilyV1 WHERE ShaleClientId=@t AND SessionId=@s; SELECT @jk=Kid FROM dbo.AiCimdKeyBindingV1 WHERE ShaleClientId=@t AND IntegrationId=@ji AND ApplicationId=@ja AND ApplicationRevision=@jr AND CredentialId=@KeyId; EXEC dbo.AiExternalReserveAssertionV2 @ja,@jr,@jk,@JtiHash,@AssertionIssuedAt,@AssertionExpiresAt;

 DELETE TOP(256) FROM dbo.AiCimdAssertionReplayV1 WHERE ShaleClientId=@t AND ExpiresAt<=@now;
 INSERT dbo.AiCimdAssertionReplayV1 VALUES(@t,@i,@key,@JtiHash,@now,@AssertionExpiresAt);
 END;
 -- Reuse is checked even after token expiry. Required audit commits BEFORE denial is returned.
 IF @consumed IS NOT NULL OR @revision<>(SELECT Revision FROM dbo.AiProductCapability WHERE Id=1) BEGIN
 UPDATE dbo.AiDelegatedSession SET RevokedAt=@now,RevocationReason=CASE WHEN @consumed IS NOT NULL THEN 'REUSE' ELSE 'DISABLED' END WHERE ShaleClientId=@t AND SessionId=@s AND RevokedAt IS NULL;
 IF @@ROWCOUNT=1 BEGIN
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,'AI_DELEGATED_SESSION',@sessionRow,'DEACTIVATED',@CorrelationId,'SHALE_SERVICE',N'{"ACTIVE":false}');
 IF @@ROWCOUNT<>1 THROW 58703,'Revocation audit unavailable.',1;
 END;
 UPDATE dbo.AiCimdCredentialV1 SET RevokedAt=@now WHERE ShaleClientId=@t AND SessionId=@s AND RevokedAt IS NULL;
 COMMIT TRANSACTION;
 SELECT CAST(1 AS bit) AS Denied;
 RETURN;
 END;
 DECLARE @liveDeadline datetime2(7);
 SELECT @liveDeadline=d.Deadline
 FROM dbo.AiDelegatedSession s JOIN dbo.AiCimdFamilyV1 a WITH(HOLDLOCK) ON a.ShaleClientId=s.ShaleClientId AND a.SessionId=s.SessionId AND s.TransactionId IS NULL AND a.GrantId=s.GrantId AND a.UserId=s.UserId AND a.IntegrationId=s.IntegrationId
 JOIN dbo.AiIntegrationRegistration r WITH(HOLDLOCK) ON r.ShaleClientId=s.ShaleClientId AND r.IntegrationId=s.IntegrationId
 JOIN dbo.AiDelegationGrant g WITH(HOLDLOCK) ON g.ShaleClientId=s.ShaleClientId AND g.GrantId=s.GrantId AND g.UserId=s.UserId AND g.IntegrationId=s.IntegrationId
 JOIN dbo.UserSessions w WITH(HOLDLOCK) ON w.ShaleClientId=s.ShaleClientId AND w.UserId=s.UserId AND w.SessionId=a.WebSessionId
 LEFT JOIN dbo.AiIntegrationClientKey k WITH(HOLDLOCK) ON k.ShaleClientId=s.ShaleClientId AND k.IntegrationId=s.IntegrationId AND k.CredentialId=s.CredentialId
 CROSS APPLY(SELECT MIN(v.Expiry) Deadline FROM(VALUES(s.ExpiresAt),(g.ExpiresAt),(r.ExpiresAt),(w.ExpiresAt),(COALESCE(k.ExpiresAt,s.ExpiresAt)))v(Expiry))d
 WHERE s.ShaleClientId=@t AND s.SessionId=@s AND s.RevokedAt IS NULL AND s.RefreshGeneration=@generation
 AND r.ClientId=s.ClientId AND r.Issuer=s.Issuer AND r.Resource=s.Resource AND r.Status='SUSPENDED' AND r.Revision=1 AND r.RevokedAt IS NULL
 AND (r.ScopeMask & @scopes)=@scopes AND g.RevokedAt IS NULL AND (g.ScopeMask & @scopes)=@scopes
 AND w.ClientType='WEB' AND w.RevokedAt IS NULL AND (s.CredentialId IS NULL OR (k.RevokedAt IS NULL AND k.ValidFrom<=@now AND k.ExpiresAt>@now));
 IF @currentDeadline<@liveDeadline SET @liveDeadline=@currentDeadline;
 SET @now=SYSUTCDATETIME();
 IF @revoked IS NOT NULL OR @credentialExpiry<=@now OR @idle<=@now OR @liveDeadline IS NULL OR @liveDeadline<=@now
 OR (@key IS NOT NULL AND @AssertionExpiresAt<=@now) THROW 58702,'Refresh authority unavailable.',1;
 DECLARE @accessExpiry datetime2(7)=CASE WHEN @liveDeadline<DATEADD(minute,5,@now) THEN @liveDeadline ELSE DATEADD(minute,5,@now) END,
 @refreshExpiry datetime2(7)=CASE WHEN @liveDeadline<DATEADD(hour,8,@now) THEN @liveDeadline ELSE DATEADD(hour,8,@now) END;
 UPDATE dbo.AiCimdCredentialV1 SET ConsumedAt=@now WHERE ShaleClientId=@t AND Id=@id AND ConsumedAt IS NULL AND RevokedAt IS NULL AND ExpiresAt>@now;
 IF @@ROWCOUNT<>1 THROW 58702,'Refresh unavailable.',1;
 INSERT dbo.AiCimdCredentialV1(ShaleClientId,IntegrationId,UserId,GrantId,SessionId,CredentialHash,Kind,ScopeMask,Generation,IssuedAt,ExpiresAt)
 VALUES(@t,@i,@u,@g,@s,@AccessHash,'ACCESS',@scopes,@generation+1,@now,@accessExpiry),(@t,@i,@u,@g,@s,@RefreshHash,'REFRESH',@scopes,@generation+1,@now,@refreshExpiry);
 UPDATE dbo.AiDelegatedSession SET RefreshGeneration=@generation+1,LastRefreshedAt=@now,IdleExpiresAt=@refreshExpiry WHERE ShaleClientId=@t AND SessionId=@s AND RevokedAt IS NULL AND RefreshGeneration=@generation;
 IF @@ROWCOUNT<>1 THROW 58702,'Refresh concurrency conflict.',1;
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,'AI_DELEGATED_SESSION',@sessionRow,'UPDATED',@CorrelationId,'SHALE_SERVICE',CONCAT(N'{"AI_SCOPE_MASK":',@scopes,N'}'));
 IF @@ROWCOUNT<>1 THROW 58703,'Rotation audit unavailable.',1;
 COMMIT TRANSACTION;
 SELECT CAST(0 AS bit) AS Denied,@now AS IssuedAt,@accessExpiry AS AccessExpiresAt,@scopes AS ScopeMask;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiExternalResolveCodeV2 @Hash varbinary(max),@External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max)
 WITH EXECUTE AS OWNER
 AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL OR @Hash IS NULL OR DATALENGTH(@Hash)<>32 THROW 59501,'Neutral resolver required.',1;
 SELECT x.ShaleClientId,x.UserId FROM dbo.AiCimdAuthorizationV1 x WHERE x.CodeHash=@Hash
 AND EXISTS(SELECT 1 FROM dbo.AiExternalApplication e WHERE e.ApplicationId=x.ApplicationId AND (e.Source='CIMD' OR (e.Source='DCR' AND EXISTS(SELECT 1 FROM dbo.AiDcrMetadataV2 dm WHERE dm.ApplicationId=e.ApplicationId AND dm.ApplicationRevision=e.Revision AND dm.MetadataDigest=e.MetadataDigest) AND EXISTS(SELECT 1 FROM dbo.AiExternalControlV2 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1))))
 AND x.ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.ExternalClientId)=DATALENGTH(@External)
 AND x.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.Issuer)=DATALENGTH(@Issuer)
 AND x.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.Resource)=DATALENGTH(@Resource);
 END;
GO
CREATE PROCEDURE dbo.AiExternalResolveRefreshV2 @Hash varbinary(max),@External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max)
 WITH EXECUTE AS OWNER
 AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL OR @Hash IS NULL OR DATALENGTH(@Hash)<>32 THROW 59501,'Neutral resolver required.',1;
 SELECT x.ShaleClientId,x.UserId FROM dbo.AiCimdCredentialV1 c JOIN dbo.AiCimdFamilyV1 x ON x.ShaleClientId=c.ShaleClientId AND x.SessionId=c.SessionId AND x.IntegrationId=c.IntegrationId AND x.UserId=c.UserId AND x.GrantId=c.GrantId WHERE c.CredentialHash=@Hash AND c.Kind='REFRESH'
 AND EXISTS(SELECT 1 FROM dbo.AiExternalApplication e WHERE e.ApplicationId=x.ApplicationId AND (e.Source='CIMD' OR (e.Source='DCR' AND EXISTS(SELECT 1 FROM dbo.AiDcrMetadataV2 dm WHERE dm.ApplicationId=e.ApplicationId AND dm.ApplicationRevision=e.Revision AND dm.MetadataDigest=e.MetadataDigest) AND EXISTS(SELECT 1 FROM dbo.AiExternalControlV2 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1))))
 AND x.ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.ExternalClientId)=DATALENGTH(@External)
 AND x.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.Issuer)=DATALENGTH(@Issuer)
 AND x.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.Resource)=DATALENGTH(@Resource);
 END;
GO
CREATE PROCEDURE dbo.AiExternalResolveRevocationV2 @Hash varbinary(max),@External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max)
 WITH EXECUTE AS OWNER
 AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL OR @Hash IS NULL OR DATALENGTH(@Hash)<>32 THROW 59501,'Neutral resolver required.',1;
 SELECT x.ShaleClientId,x.UserId FROM dbo.AiCimdCredentialV1 c JOIN dbo.AiCimdFamilyV1 x ON x.ShaleClientId=c.ShaleClientId AND x.SessionId=c.SessionId AND x.IntegrationId=c.IntegrationId AND x.UserId=c.UserId AND x.GrantId=c.GrantId WHERE c.CredentialHash=@Hash
 AND EXISTS(SELECT 1 FROM dbo.AiExternalApplication e WHERE e.ApplicationId=x.ApplicationId AND (e.Source='CIMD' OR (e.Source='DCR' AND EXISTS(SELECT 1 FROM dbo.AiDcrMetadataV2 dm WHERE dm.ApplicationId=e.ApplicationId AND dm.ApplicationRevision=e.Revision AND dm.MetadataDigest=e.MetadataDigest) AND EXISTS(SELECT 1 FROM dbo.AiExternalControlV2 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1))))
 AND x.ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.ExternalClientId)=DATALENGTH(@External)
 AND x.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.Issuer)=DATALENGTH(@Issuer)
 AND x.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(x.Resource)=DATALENGTH(@Resource);
 END;
GO
CREATE PROCEDURE dbo.AiExternalLoadClientV2 @External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@Web uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD browser lease required.',1;
 BEGIN TRY BEGIN TRANSACTION; EXEC dbo.AiLockV1; EXEC dbo.AiActorV1; EXEC dbo.AiEnabledV1;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@now datetime2(7)=SYSUTCDATETIME();
 SELECT i.ShaleClientId,i.IntegrationId,e.ExternalClientId AS ClientId,i.Name,i.ClientType,p.ScopeMask,
 CASE WHEN p.ExpiresAt<i.ExpiresAt THEN p.ExpiresAt ELSE i.ExpiresAt END AS ExpiresAt,i.RowVer,e.Issuer,e.Resource,
 r.value AS RedirectUri
 FROM dbo.AiExternalApplication e WITH(HOLDLOCK)
 JOIN dbo.AiTenantApplicationApproval p WITH(HOLDLOCK) ON p.ApplicationId=e.ApplicationId AND p.ShaleClientId=@t AND p.ApplicationRevision=e.Revision
 JOIN dbo.AiIntegrationRegistration i WITH(HOLDLOCK) ON i.ShaleClientId=@t AND i.IntegrationId=p.IntegrationId
 JOIN dbo.AiCimdPublicationV1 m WITH(HOLDLOCK) ON m.ApplicationId=e.ApplicationId AND m.ApplicationRevision=e.Revision AND m.FreshUntil>@now
 CROSS APPLY OPENJSON(m.Redirects) r
 WHERE e.ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.ExternalClientId)=DATALENGTH(@External)
 AND e.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.Issuer)=DATALENGTH(@Issuer)
 AND e.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.Resource)=DATALENGTH(@Resource)
 AND e.Disabled=0 AND (e.Source<>'DCR' OR EXISTS(SELECT 1 FROM dbo.AiDcrMetadataV2 ds WHERE ds.ApplicationId=e.ApplicationId AND ds.ApplicationRevision=e.Revision AND (ds.ScopeMask & p.ScopeMask)=p.ScopeMask)) AND (e.Source='CIMD' OR (e.Source='DCR' AND EXISTS(SELECT 1 FROM dbo.AiDcrMetadataV2 dm WHERE dm.ApplicationId=e.ApplicationId AND dm.ApplicationRevision=e.Revision AND dm.MetadataDigest=e.MetadataDigest) AND EXISTS(SELECT 1 FROM dbo.AiExternalControlV2 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1))) AND p.State='APPROVED' AND p.ClosedAt IS NULL AND p.ExpiresAt>@now
 AND i.Status='SUSPENDED' AND i.Revision=1 AND i.RevokedAt IS NULL AND i.ExpiresAt>@now
 AND EXISTS(SELECT 1 FROM dbo.UserSessions WITH(HOLDLOCK) WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@Web AND ClientType='WEB' AND RevokedAt IS NULL AND ExpiresAt>@now)
 AND EXISTS(SELECT 1 FROM dbo.AiCimdOAuthControlV1 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1)
 AND EXISTS(SELECT 1 FROM dbo.AiCimdProofControlV1 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1);
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiExternalLoadClientProofV2 @Hash varbinary(max),@External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@Kind varchar(max)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD issuer lease required.',1;
 BEGIN TRY BEGIN TRANSACTION; EXEC dbo.AiLockV1;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),
 @a uniqueidentifier,@r bigint,@p uniqueidentifier,@v binary(8),@d binary(32),@e nvarchar(2048),@i uniqueidentifier,@w uniqueidentifier,@sc tinyint,@m varchar(16),@k uniqueidentifier,@deadline datetime2(7);
 IF @Kind='CODE' BEGIN
 SELECT @a=ApplicationId,@r=ApplicationRevision,@p=ApprovalId,@v=ApprovalVersion,@d=MetadataDigest,@e=ExternalClientId,@i=IntegrationId,@w=WebSessionId,@sc=RequestedScopeMask,@m=AuthMethod,@k=CredentialId
 FROM dbo.AiCimdAuthorizationV1 WHERE ShaleClientId=@t AND UserId=@u AND CodeHash=@Hash;
 END ELSE IF @Kind='REFRESH' BEGIN
 SELECT @a=f.ApplicationId,@r=f.ApplicationRevision,@p=f.ApprovalId,@v=f.ApprovalVersion,@d=f.MetadataDigest,@e=f.ExternalClientId,@i=f.IntegrationId,@w=f.WebSessionId,@sc=s.ScopeMask,@m=f.AuthMethod,@k=f.CredentialId
 FROM dbo.AiCimdFamilyV1 f JOIN dbo.AiCimdCredentialV1 c ON c.ShaleClientId=f.ShaleClientId AND c.SessionId=f.SessionId
 JOIN dbo.AiDelegatedSession s ON s.ShaleClientId=f.ShaleClientId AND s.SessionId=f.SessionId
 WHERE f.ShaleClientId=@t AND f.UserId=@u AND c.CredentialHash=@Hash AND c.Kind='REFRESH';
 END;
 IF @e IS NULL OR @e<>@External COLLATE Latin1_General_100_BIN2 OR DATALENGTH(@e)<>DATALENGTH(@External) THROW 59501,'CIMD client unavailable.',1;
 EXEC dbo.AiExternalOAuthAuthorityV2 @a,@r,@p,@v,@d,@e,@Issuer,@Resource,@i,@w,@sc,@m,@k,1,@deadline OUTPUT;
 SELECT @m AS AuthMethod,@k AS CredentialId,b.Kid,k.Modulus,k.Exponent,k.ValidFrom,k.ExpiresAt AS KeyExpiresAt,@deadline AS Deadline
 FROM (VALUES(1))x(n) LEFT JOIN dbo.AiCimdKeyBindingV1 b ON b.ShaleClientId=@t AND b.IntegrationId=@i AND b.ApplicationId=@a AND b.ApplicationRevision=@r AND b.CredentialId=@k
 LEFT JOIN dbo.AiIntegrationClientKey k ON k.ShaleClientId=@t AND k.IntegrationId=@i AND k.CredentialId=@k;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiExternalResolvePublicationV2 @External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@Digest varbinary(max),@Methods tinyint
AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL THROW 59501,'Neutral publication lease required.',1;
 SELECT ApplicationId,Revision FROM dbo.AiExternalApplication e WHERE ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(ExternalClientId)=DATALENGTH(@External)
 AND Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Issuer)=DATALENGTH(@Issuer)
 AND Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Resource)=DATALENGTH(@Resource)
 AND MetadataDigest=@Digest AND DATALENGTH(@Digest)=32 AND MethodMask=@Methods AND Disabled=0 AND (e.Source='CIMD' OR (e.Source='DCR' AND EXISTS(SELECT 1 FROM dbo.AiDcrMetadataV2 dm WHERE dm.ApplicationId=e.ApplicationId AND dm.ApplicationRevision=e.Revision AND dm.MetadataDigest=e.MetadataDigest) AND EXISTS(SELECT 1 FROM dbo.AiExternalControlV2 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1)));
END;
GO
CREATE PROCEDURE dbo.AiExternalReserveAssertionV2 @Application uniqueidentifier,@Revision bigint,@Kid nvarchar(max),@Jti varbinary(max),@Issued datetime2(7),@Expires datetime2(7)
AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>1 THROW 59501,'Assertion transaction required.',1;
 DECLARE @now datetime2(7)=SYSUTCDATETIME(),@rc int,@lock nvarchar(255)=CONCAT(N'AiCimdAssertionV1:',@Application);
 IF @Application IS NULL OR @Revision IS NULL OR @Kid IS NULL OR DATALENGTH(@Kid) NOT BETWEEN 2 AND 256 OR @Jti IS NULL OR DATALENGTH(@Jti)<>32
 OR @Issued IS NULL OR @Expires IS NULL OR @Issued>@now OR @Expires<=@now OR @Expires>DATEADD(second,60,@Issued) THROW 59501,'Assertion unavailable.',1;
 EXEC @rc=sys.sp_getapplock @Resource=@lock,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
 IF @rc<0 THROW 59501,'Assertion reservation unavailable.',1;
 SET @now=SYSUTCDATETIME();
 IF @Issued>@now OR @Expires<=@now OR @Expires>DATEADD(second,60,@Issued) THROW 59501,'Assertion deadline elapsed.',1;
 DELETE TOP(256) FROM dbo.AiCimdNeutralReplayV1 WHERE ApplicationId=@Application AND ExpiresAt<=@now;
 IF (SELECT COUNT(*) FROM dbo.AiCimdNeutralReplayV1 WHERE ApplicationId=@Application AND ExpiresAt>@now)>=1024 THROW 59501,'CIMD replay capacity unavailable.',1;
 INSERT dbo.AiCimdNeutralReplayV1 VALUES(@Application,@Revision,@Kid,@Jti,@now,@Expires);
 IF @Expires<=SYSUTCDATETIME() THROW 59501,'Assertion deadline elapsed.',1;
END;
GO
CREATE PROCEDURE dbo.AiExternalAuthorizeRevocationV2 @External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@Digest varbinary(max),@Method varchar(max),
 @Kid nvarchar(max),@JtiHash varbinary(max),@Issued datetime2(7),@Expires datetime2(7),@Application uniqueidentifier OUTPUT
AS BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT<>1 THROW 59501,'Revocation authentication transaction required.',1;
 DECLARE @r bigint,@methods tinyint,@keys nvarchar(max),@now datetime2(7);
 SET @Application=NULL;
 SELECT @Application=ApplicationId,@r=Revision,@methods=MethodMask FROM dbo.AiExternalApplication e WITH(HOLDLOCK)
 WHERE ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(ExternalClientId)=DATALENGTH(@External)
 AND Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Issuer)=DATALENGTH(@Issuer)
 AND Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Resource)=DATALENGTH(@Resource)
 AND MetadataDigest=@Digest AND DATALENGTH(@Digest)=32 AND (e.Source='CIMD' OR (e.Source='DCR' AND EXISTS(SELECT 1 FROM dbo.AiDcrMetadataV2 dm WHERE dm.ApplicationId=e.ApplicationId AND dm.ApplicationRevision=e.Revision AND dm.MetadataDigest=e.MetadataDigest) AND EXISTS(SELECT 1 FROM dbo.AiExternalControlV2 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1))) AND Disabled=0;
 SET @now=SYSUTCDATETIME();
 SELECT @keys=SigningKeys FROM dbo.AiCimdPublicationV1 WITH(HOLDLOCK) WHERE ApplicationId=@Application AND ApplicationRevision=@r AND MetadataDigest=@Digest AND FreshUntil>@now;
 IF @Application IS NULL OR @keys IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.AiCimdOAuthControlV1 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1)
 OR NOT EXISTS(SELECT 1 FROM dbo.AiCimdProofControlV1 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1)
 OR (@Method COLLATE Latin1_General_100_BIN2='none' AND ((@methods & 1)<>1 OR DATALENGTH(@Method)<>4 OR @Kid IS NOT NULL OR @JtiHash IS NOT NULL OR @Issued IS NOT NULL OR @Expires IS NOT NULL))
 OR (@Method COLLATE Latin1_General_100_BIN2='private_key_jwt' AND ((@methods & 2)<>2 OR DATALENGTH(@Method)<>15 OR @Kid IS NULL OR DATALENGTH(@Kid) NOT BETWEEN 2 AND 256
 OR @JtiHash IS NULL OR DATALENGTH(@JtiHash)<>32 OR @Issued IS NULL OR @Expires IS NULL OR @Issued>@now OR @Expires<=@now OR @Expires>DATEADD(second,60,@Issued)
 OR NOT EXISTS(SELECT 1 FROM OPENJSON(@keys) WITH(kid nvarchar(128)) WHERE kid=@Kid COLLATE Latin1_General_100_BIN2 AND DATALENGTH(kid)=DATALENGTH(@Kid))))
 OR @Method IS NULL OR @Method COLLATE Latin1_General_100_BIN2 NOT IN('none','private_key_jwt') THROW 59501,'CIMD client unavailable.',1;
 IF @Method COLLATE Latin1_General_100_BIN2='private_key_jwt'
 EXEC dbo.AiExternalReserveAssertionV2 @Application,@r,@Kid,@JtiHash,@Issued,@Expires;
END;
GO
CREATE PROCEDURE dbo.AiExternalRevokeUnknownV2 @External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@Digest varbinary(max),@Method varchar(max),
 @Kid nvarchar(max),@JtiHash varbinary(max),@Issued datetime2(7),@Expires datetime2(7)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL THROW 59501,'Neutral revocation lease required.',1;
 BEGIN TRY BEGIN TRANSACTION;
 DECLARE @application uniqueidentifier;
 EXEC dbo.AiExternalAuthorizeRevocationV2 @External,@Issuer,@Resource,@Digest,@Method,@Kid,@JtiHash,@Issued,@Expires,@application OUTPUT;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiExternalRevokeCredentialV2 @Hash varbinary(max),@External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@Digest varbinary(max),@Method varchar(max),
 @Kid nvarchar(max),@JtiHash varbinary(max),@Issued datetime2(7),@Expires datetime2(7),@Correlation uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 THROW 59501,'Fresh CIMD revocation lease required.',1;
 IF @Hash IS NULL OR DATALENGTH(@Hash)<>32 OR @Correlation IS NULL THROW 59501,'CIMD credential unavailable.',1;
 BEGIN TRY BEGIN TRANSACTION; EXEC dbo.AiLockV1;
 DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@application uniqueidentifier,
 @session uniqueidentifier,@id bigint,@now datetime2(7),@changed int;
 EXEC dbo.AiExternalAuthorizeRevocationV2 @External,@Issuer,@Resource,@Digest,@Method,@Kid,@JtiHash,@Issued,@Expires,@application OUTPUT;
 SELECT @session=s.SessionId,@id=s.Id FROM dbo.AiCimdCredentialV1 c WITH(UPDLOCK,HOLDLOCK)
 JOIN dbo.AiCimdFamilyV1 f WITH(HOLDLOCK) ON f.ShaleClientId=c.ShaleClientId AND f.SessionId=c.SessionId AND f.IntegrationId=c.IntegrationId AND f.UserId=c.UserId AND f.GrantId=c.GrantId
 JOIN dbo.AiDelegatedSession s WITH(UPDLOCK,HOLDLOCK) ON s.ShaleClientId=f.ShaleClientId AND s.SessionId=f.SessionId AND s.GrantId=f.GrantId AND s.IntegrationId=f.IntegrationId AND s.UserId=f.UserId AND s.TransactionId IS NULL
 WHERE c.CredentialHash=@Hash AND c.ShaleClientId=@t AND c.UserId=@u AND f.ApplicationId=@application
 AND f.ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(f.ExternalClientId)=DATALENGTH(@External)
 AND f.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(f.Issuer)=DATALENGTH(@Issuer)
 AND f.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(f.Resource)=DATALENGTH(@Resource);
 SET @now=SYSUTCDATETIME();
 UPDATE dbo.AiDelegatedSession SET RevokedAt=@now,RevocationReason='SELF' WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@session AND RevokedAt IS NULL;
 SET @changed=@@ROWCOUNT;
 UPDATE dbo.AiCimdCredentialV1 SET RevokedAt=@now WHERE ShaleClientId=@t AND SessionId=@session AND RevokedAt IS NULL;
 SET @changed=@changed+@@ROWCOUNT;
 IF @changed>0 BEGIN
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 VALUES(@t,@u,'AI_DELEGATED_SESSION',@id,'DEACTIVATED',@Correlation,'SHALE_SERVICE',N'{"ACTIVE":false}');
 IF @@ROWCOUNT<>1 THROW 59501,'CIMD revocation audit unavailable.',1;
 END;
 IF @Method COLLATE Latin1_General_100_BIN2='private_key_jwt' AND @Expires<=SYSUTCDATETIME() THROW 59501,'CIMD assertion expired.',1;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiExternalGetOnboardingV2
 @External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@Web uniqueidentifier
 WITH EXECUTE AS OWNER
 AS BEGIN
  SET NOCOUNT ON;
  IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR @External IS NULL OR DATALENGTH(@External) NOT BETWEEN 20 AND 4096
   OR @Issuer IS NULL OR DATALENGTH(@Issuer) NOT BETWEEN 18 AND 512 OR @Resource IS NULL OR DATALENGTH(@Resource) NOT BETWEEN 18 AND 512
   OR @Web IS NULL THROW 59531,'Bounded original WEB request required.',1;
  DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),@now datetime2(7)=SYSUTCDATETIME();
  IF @t IS NULL OR @u IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.UserSessions WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@Web AND ClientType='WEB' AND RevokedAt IS NULL AND ExpiresAt>@now)
   THROW 59531,'Original WEB authority unavailable.',1;
  SELECT u.is_admin AS IsAdmin,e.ApplicationId,e.Revision,e.Disabled,e.RowVer AS ApplicationVersion,e.MetadataDigest,e.MethodMask,
   p.ApprovalId,p.ApplicationRevision,p.IntegrationId,p.State,p.ScopeMask,p.ExpiresAt,p.RowVer AS ApprovalVersion
  FROM dbo.Users u
  LEFT JOIN dbo.AiExternalApplication e ON (e.Source='CIMD' OR (e.Source='DCR' AND EXISTS(SELECT 1 FROM dbo.AiDcrMetadataV2 dm WHERE dm.ApplicationId=e.ApplicationId AND dm.ApplicationRevision=e.Revision AND dm.MetadataDigest=e.MetadataDigest) AND EXISTS(SELECT 1 FROM dbo.AiExternalControlV2 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1)))
   AND e.ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.ExternalClientId)=DATALENGTH(@External)
   AND e.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.Issuer)=DATALENGTH(@Issuer)
   AND e.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.Resource)=DATALENGTH(@Resource)
  LEFT JOIN dbo.AiTenantApplicationApproval p ON p.ApplicationId=e.ApplicationId AND p.ShaleClientId=@t AND p.ClosedAt IS NULL
  WHERE u.Id=@u AND u.ShaleClientId=@t AND u.is_deleted=0 AND COALESCE(u.IsRemoved,0)=0;
 END;
GO
CREATE TRIGGER dbo.AiDcrImmutableMetadataV2 ON dbo.AiDcrMetadataV2 AFTER UPDATE,DELETE AS
 THROW 59600,'DCR revision metadata is immutable.',1;
GO
CREATE PROCEDURE dbo.AiDcrRegisterApplicationV2
 @Issuer nvarchar(max),@Resource nvarchar(max),@Name nvarchar(max),@Redirects nvarchar(max),@Keys nvarchar(max),
 @Methods tinyint,@Scopes tinyint,@Correlation uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR ISNULL(IS_MEMBER(N'AiDcrRegistrarV2'),0)<>1
 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 59601,'Fresh neutral DCR registrar lease required.',1;
 IF @Issuer IS NULL OR DATALENGTH(@Issuer) NOT BETWEEN 18 AND 512 OR @Issuer NOT LIKE N'https://%/%'
 OR @Resource IS NULL OR DATALENGTH(@Resource) NOT BETWEEN 18 AND 512 OR @Resource NOT LIKE N'https://%/%'
 OR @Name IS NULL OR DATALENGTH(@Name) NOT BETWEEN 2 AND 400 OR @Correlation IS NULL
 OR @Methods IS NULL OR @Methods NOT IN(1,2) OR @Scopes IS NULL OR @Scopes NOT BETWEEN 1 AND 3
 OR @Redirects IS NULL OR DATALENGTH(@Redirects)>32768 OR ISJSON(@Redirects)<>1 OR LEFT(@Redirects,1)<>N'['
 OR @Keys IS NULL OR DATALENGTH(@Keys)>16384 OR ISJSON(@Keys)<>1 OR LEFT(@Keys,1)<>N'['
 THROW 59601,'Bounded validated registration metadata required.',1;
 IF (SELECT COUNT(*) FROM OPENJSON(@Redirects)) NOT BETWEEN 1 AND 5
 OR EXISTS(SELECT 1 FROM OPENJSON(@Redirects) WHERE type<>1 OR DATALENGTH(value) NOT BETWEEN 2 AND 4096
 OR value LIKE N'%[#*%]%' OR NOT(value LIKE N'https://%/%' OR @Methods=1 AND (value LIKE N'http://127.0.0.1:%/%' OR value LIKE N'http://[[]::1]:%/%')))
 OR (SELECT COUNT(*) FROM OPENJSON(@Redirects))<>(SELECT COUNT(DISTINCT value COLLATE Latin1_General_100_BIN2) FROM OPENJSON(@Redirects))
 OR (@Methods=1 AND @Keys<>N'[]') OR (@Methods=2 AND (SELECT COUNT(*) FROM OPENJSON(@Keys)) NOT BETWEEN 1 AND 2)
 OR EXISTS(SELECT 1 FROM OPENJSON(@Keys) WITH(kid nvarchar(max),modulus varchar(max),exponent int)
 WHERE kid IS NULL OR DATALENGTH(kid) NOT BETWEEN 2 AND 256 OR exponent<>65537 OR exponent IS NULL
 OR TRY_CONVERT(varbinary(max),modulus,2) IS NULL OR DATALENGTH(TRY_CONVERT(varbinary(max),modulus,2)) NOT BETWEEN 256 AND 512)
 THROW 59601,'Registration metadata profile unavailable.',1;
 BEGIN TRY BEGIN TRANSACTION;
 DECLARE @lock int;
 EXEC @lock=sys.sp_getapplock @Resource=N'AiApplicationRegistryV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
 IF @lock<0 OR NOT EXISTS(SELECT 1 FROM dbo.AiExternalControlV2 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1)
 OR (SELECT COUNT(*) FROM dbo.AiExternalApplication WITH(HOLDLOCK) WHERE Source='DCR')>=5000
 THROW 59601,'DCR registration capacity unavailable.',1;
 DECLARE @wire nvarchar(68)=N'dcr.'+LOWER(CONVERT(varchar(64),CRYPT_GEN_RANDOM(32),2)),@application uniqueidentifier=NEWID(),@now datetime2(7)=SYSUTCDATETIME();
 DECLARE @digest binary(32)=HASHBYTES('SHA2_256',CONVERT(varbinary(max),CONCAT(DATALENGTH(@Name),N':',@Name,DATALENGTH(@Redirects),N':',@Redirects,DATALENGTH(@Keys),N':',@Keys,N':',@Methods,N':',@Scopes)));
 INSERT dbo.AiExternalApplication(ApplicationId,Issuer,Resource,ExternalClientId,IdentityHash,Source,MetadataDigest,MethodMask)
 VALUES(@application,@Issuer,@Resource,@wire,HASHBYTES('SHA2_256',CONVERT(varbinary(max),@Issuer)+0x0000+CONVERT(varbinary(max),@wire)),'DCR',@digest,@Methods);
 INSERT dbo.AiExternalApplicationRevision(ApplicationId,Revision,MetadataDigest,MethodMask,Disabled) VALUES(@application,1,@digest,@Methods,0);
 INSERT dbo.AiDcrMetadataV2 VALUES(@application,1,@digest,@Name,@Redirects,@Keys,@Methods,@Scopes,@now,DATEADD(day,1,@now));
 INSERT dbo.AiCimdPublicationV1 VALUES(@application,1,@digest,@Redirects,@Keys,DATEADD(minute,4,@now));
 INSERT dbo.GlobalControlPlaneAuditLog(OperatorId,EntityType,EntityId,Action,Metadata)
 SELECT USER_NAME(),'AI_EXTERNAL_APPLICATION',Id,'UPDATED',CONCAT(N'{"CORRELATION_ID":"',CONVERT(nvarchar(36),@Correlation),N'"}') FROM dbo.AiExternalApplication WHERE ApplicationId=@application;
 IF @@ROWCOUNT<>1 THROW 59601,'Registration audit unavailable.',1;
 SELECT @wire AS ClientId,@now AS IssuedAt,ApplicationId,Revision,Disabled,RowVer FROM dbo.AiExternalApplication WHERE ApplicationId=@application;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiDcrChangeStateV2 @Application uniqueidentifier,@Disabled bit,@Expected varbinary(max),@Correlation uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR ISNULL(IS_MEMBER(N'AiDcrAdministratorV2'),0)<>1
 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 OR @Disabled IS NULL OR @Expected IS NULL OR DATALENGTH(@Expected)<>8 OR @Correlation IS NULL
 THROW 59601,'Fresh neutral DCR administrator lease required.',1;
 BEGIN TRY BEGIN TRANSACTION;
 DECLARE @lock int,@r bigint,@digest binary(32),@methods tinyint;
 EXEC @lock=sys.sp_getapplock @Resource=N'AiApplicationRegistryV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
 IF @lock<0 THROW 59601,'Registry lock unavailable.',1;
 UPDATE dbo.AiExternalApplication SET Disabled=@Disabled,Revision=Revision+1,UpdatedAt=SYSUTCDATETIME()
 WHERE ApplicationId=@Application AND Source='DCR' AND RowVer=@Expected;
 IF @@ROWCOUNT<>1 THROW 59601,'DCR application unavailable or stale.',1;
 SELECT @r=Revision,@digest=MetadataDigest,@methods=MethodMask FROM dbo.AiExternalApplication WHERE ApplicationId=@Application;
 INSERT dbo.AiExternalApplicationRevision(ApplicationId,Revision,MetadataDigest,MethodMask,Disabled) VALUES(@Application,@r,@digest,@methods,@Disabled);
 INSERT dbo.AiDcrMetadataV2 SELECT ApplicationId,@r,MetadataDigest,ClientName,Redirects,SigningKeys,MethodMask,ScopeMask,CreatedAt,PendingExpiresAt
 FROM dbo.AiDcrMetadataV2 WHERE ApplicationId=@Application AND ApplicationRevision=@r-1;
 IF @@ROWCOUNT<>1 THROW 59601,'DCR revision metadata unavailable.',1;
 INSERT dbo.AiCimdPublicationV1 SELECT ApplicationId,@r,MetadataDigest,Redirects,SigningKeys,DATEADD(minute,4,SYSUTCDATETIME())
 FROM dbo.AiDcrMetadataV2 WHERE ApplicationId=@Application AND ApplicationRevision=@r;
 INSERT dbo.GlobalControlPlaneAuditLog(OperatorId,EntityType,EntityId,Action,Metadata)
 SELECT USER_NAME(),'AI_EXTERNAL_APPLICATION',Id,'UPDATED',CONCAT(N'{"CORRELATION_ID":"',CONVERT(nvarchar(36),@Correlation),N'"}') FROM dbo.AiExternalApplication WHERE ApplicationId=@Application;
 IF @@ROWCOUNT<>1 THROW 59601,'DCR application audit unavailable.',1;
 SELECT ApplicationId,Revision,Disabled,RowVer FROM dbo.AiExternalApplication WHERE ApplicationId=@Application;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiDcrPrepareMetadataV2 @External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 OR @External IS NULL OR DATALENGTH(@External)<>136 OR LEFT(@External,4) COLLATE Latin1_General_100_BIN2<>N'dcr.'
 OR SUBSTRING(@External,5,64) COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9a-f]%'
 THROW 59601,'Neutral exact DCR identity required.',1;
 BEGIN TRY BEGIN TRANSACTION;
 DECLARE @a uniqueidentifier,@r bigint,@now datetime2(7)=SYSUTCDATETIME();
 SELECT @a=ApplicationId,@r=Revision FROM dbo.AiExternalApplication WITH(HOLDLOCK)
 WHERE ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(ExternalClientId)=DATALENGTH(@External)
 AND Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Issuer)=DATALENGTH(@Issuer)
 AND Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(Resource)=DATALENGTH(@Resource) AND Source='DCR' AND Disabled=0;
 IF NOT EXISTS(SELECT 1 FROM dbo.AiDcrMetadataV2 m JOIN dbo.AiExternalApplication e ON e.ApplicationId=m.ApplicationId AND e.Revision=m.ApplicationRevision AND e.MetadataDigest=m.MetadataDigest
 WHERE m.ApplicationId=@a AND m.ApplicationRevision=@r AND (m.PendingExpiresAt>@now OR EXISTS(SELECT 1 FROM dbo.AiTenantApplicationApproval p WHERE p.ApplicationId=@a AND p.IntegrationId IS NOT NULL)))
 THROW 59601,'Current DCR metadata unavailable.',1;
 UPDATE p SET FreshUntil=DATEADD(minute,4,@now) FROM dbo.AiCimdPublicationV1 p
 JOIN dbo.AiDcrMetadataV2 m ON m.ApplicationId=p.ApplicationId AND m.ApplicationRevision=p.ApplicationRevision AND m.MetadataDigest=p.MetadataDigest
 WHERE p.ApplicationId=@a AND p.ApplicationRevision=@r AND p.Redirects=m.Redirects COLLATE Latin1_General_100_BIN2 AND DATALENGTH(p.Redirects)=DATALENGTH(m.Redirects)
 AND p.SigningKeys=m.SigningKeys COLLATE Latin1_General_100_BIN2 AND DATALENGTH(p.SigningKeys)=DATALENGTH(m.SigningKeys);
 IF @@ROWCOUNT<>1 THROW 59601,'DCR stored publication unavailable.',1;
 SELECT e.ExternalClientId,m.*,p.FreshUntil FROM dbo.AiDcrMetadataV2 m JOIN dbo.AiExternalApplication e ON e.ApplicationId=m.ApplicationId AND e.Revision=m.ApplicationRevision
 JOIN dbo.AiCimdPublicationV1 p ON p.ApplicationId=m.ApplicationId AND p.ApplicationRevision=m.ApplicationRevision
 WHERE m.ApplicationId=@a AND m.ApplicationRevision=@r;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO
CREATE PROCEDURE dbo.AiExternalCompatibilityV2 AS THROW 59600,'Private C1 compatibility metadata.',1;
