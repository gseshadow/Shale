/* Additive OFF candidate: one bounded public-application/current-tenant projection.
   No historical modules, permissions, receipts, binding or audit schema are changed.
   No login/user/membership is provisioned. Separately reviewed deployment only. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
IF @@TRANCOUNT<>0 OR USER_NAME()<>'dbo' OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL
 OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL THROW 59530,'Fresh unscoped owner required.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @lock int;
 EXEC @lock=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
 IF @lock<0 OR NOT EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Id=1 AND Enabled=0)
  THROW 59530,'Schema lock and capability OFF required.',1;
 IF OBJECT_ID(N'dbo.AiCimdGetOnboardingV1') IS NOT NULL OR DATABASE_PRINCIPAL_ID(N'AiCimdOnboardingRuntimeV1') IS NOT NULL
  THROW 59530,'Onboarding projection already exists; no automatic repair.',1;
 EXEC(N'CREATE PROCEDURE dbo.AiCimdGetOnboardingV1
 @External nvarchar(max),@Issuer nvarchar(max),@Resource nvarchar(max),@Web uniqueidentifier
 WITH EXECUTE AS OWNER
 AS BEGIN
  SET NOCOUNT ON;
  IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR @External IS NULL OR DATALENGTH(@External) NOT BETWEEN 20 AND 4096
   OR @Issuer IS NULL OR DATALENGTH(@Issuer) NOT BETWEEN 18 AND 512 OR @Resource IS NULL OR DATALENGTH(@Resource) NOT BETWEEN 18 AND 512
   OR @Web IS NULL THROW 59531,''Bounded original WEB request required.'',1;
  DECLARE @t int=TRY_CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=TRY_CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),@now datetime2(7)=SYSUTCDATETIME();
  IF @t IS NULL OR @u IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.UserSessions WHERE ShaleClientId=@t AND UserId=@u AND SessionId=@Web AND ClientType=''WEB'' AND RevokedAt IS NULL AND ExpiresAt>@now)
   THROW 59531,''Original WEB authority unavailable.'',1;
  SELECT u.is_admin AS IsAdmin,e.ApplicationId,e.Revision,e.Disabled,e.RowVer AS ApplicationVersion,e.MetadataDigest,e.MethodMask,
   p.ApprovalId,p.ApplicationRevision,p.IntegrationId,p.State,p.ScopeMask,p.ExpiresAt,p.RowVer AS ApprovalVersion
  FROM dbo.Users u
  LEFT JOIN dbo.AiExternalApplication e ON e.Source=''CIMD''
   AND e.ExternalClientId=@External COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.ExternalClientId)=DATALENGTH(@External)
   AND e.Issuer=@Issuer COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.Issuer)=DATALENGTH(@Issuer)
   AND e.Resource=@Resource COLLATE Latin1_General_100_BIN2 AND DATALENGTH(e.Resource)=DATALENGTH(@Resource)
  LEFT JOIN dbo.AiTenantApplicationApproval p ON p.ApplicationId=e.ApplicationId AND p.ShaleClientId=@t AND p.ClosedAt IS NULL
  WHERE u.Id=@u AND u.ShaleClientId=@t AND u.is_deleted=0 AND COALESCE(u.IsRemoved,0)=0;
 END;');
 CREATE ROLE AiCimdOnboardingRuntimeV1 AUTHORIZATION dbo;
 GRANT EXECUTE ON OBJECT::dbo.AiCimdGetOnboardingV1 TO AiCimdOnboardingRuntimeV1;
 IF (SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id=DATABASE_PRINCIPAL_ID(N'AiCimdOnboardingRuntimeV1'))<>1
  THROW 59530,'Onboarding role authority mismatch.',1;
 COMMIT TRANSACTION;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
