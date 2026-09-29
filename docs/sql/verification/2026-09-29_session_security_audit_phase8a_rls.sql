/* Run with a migration/admin connection. Enforcement itself executes as a disposable non-dbo user. */
SET NOCOUNT ON;

DECLARE @SameTenant int = 7;
DECLARE @OtherTenant int = 8;
DECLARE @SameTenantActor int = (SELECT TOP (1) Id FROM dbo.Users WHERE ShaleClientId = 7 ORDER BY Id);
DECLARE @OtherTenantActor int = (SELECT TOP (1) Id FROM dbo.Users WHERE ShaleClientId = 8 ORDER BY Id);
DECLARE @FixtureSessionId uniqueidentifier = '8A000000-0000-4000-8000-000000000007';
DECLARE @DeniedSessionId uniqueidentifier = '8A000000-0000-4000-8000-000000000008';
DECLARE @FixtureId bigint;
DECLARE @InsertBlocked bit = 0;
DECLARE @UpdateBlocked bit = 0;

IF @SameTenantActor IS NULL OR @OtherTenantActor IS NULL
BEGIN
    THROW 57800, 'Phase 8A RLS verification requires users in tenants 7 and 8.', 1;
END;

IF USER_ID(N'Phase8ARlsVerifier') IS NOT NULL
BEGIN
    THROW 57800, 'Phase8ARlsVerifier already exists; run the Phase 8A RLS cleanup script first.', 1;
END;

CREATE USER [Phase8ARlsVerifier] WITHOUT LOGIN;
GRANT SELECT, INSERT, UPDATE ON dbo.SessionSecurityAuditLog TO [Phase8ARlsVerifier];

EXECUTE AS USER = N'Phase8ARlsVerifier';

EXEC sys.sp_set_session_context
    @key = N'ShaleClientId',
    @value = 7;

INSERT dbo.SessionSecurityAuditLog
    (ShaleClientId, ActorUserId, EventType, TargetSessionId, AffectedCount, ReasonCode)
VALUES
    (@SameTenant, @SameTenantActor, 'SELF_REVOKE', @FixtureSessionId, 1, 'USER_REVOKED');

SET @FixtureId = SCOPE_IDENTITY();

SELECT
    'SameTenantInsertAndVisibility' AS CheckName,
    CASE WHEN (SELECT COUNT(*) FROM dbo.SessionSecurityAuditLog WHERE Id = @FixtureId AND TargetSessionId = @FixtureSessionId) = 1 THEN 0 ELSE 1 END AS FindingCount;

SELECT
    'CrossTenantVisibilityFiltered' AS CheckName,
    COUNT(*) AS FindingCount
FROM dbo.SessionSecurityAuditLog
WHERE ShaleClientId = @OtherTenant;

BEGIN TRY
    INSERT dbo.SessionSecurityAuditLog
        (ShaleClientId, ActorUserId, EventType, TargetSessionId, AffectedCount, ReasonCode)
    VALUES
        (@OtherTenant, @OtherTenantActor, 'SELF_REVOKE', @DeniedSessionId, 1, 'USER_REVOKED');
END TRY
BEGIN CATCH
    IF ERROR_NUMBER() = 33504
    BEGIN
        SET @InsertBlocked = 1;
    END
    ELSE
    BEGIN
        THROW;
    END;
END CATCH;

BEGIN TRY
    UPDATE dbo.SessionSecurityAuditLog
    SET ShaleClientId = @OtherTenant,
        ActorUserId = @OtherTenantActor
    WHERE Id = @FixtureId;
END TRY
BEGIN CATCH
    IF ERROR_NUMBER() = 33504
    BEGIN
        SET @UpdateBlocked = 1;
    END
    ELSE
    BEGIN
        THROW;
    END;
END CATCH;

SELECT
    'CrossTenantInsertBlocked33504' AS CheckName,
    CASE WHEN @InsertBlocked = 1 THEN 0 ELSE 1 END AS FindingCount;

SELECT
    'CrossTenantUpdateBlocked33504' AS CheckName,
    CASE WHEN @UpdateBlocked = 1 THEN 0 ELSE 1 END AS FindingCount;

REVERT;

DELETE dbo.SessionSecurityAuditLog
WHERE TargetSessionId IN (@FixtureSessionId, @DeniedSessionId)
  AND EventType = 'SELF_REVOKE'
  AND ReasonCode = 'USER_REVOKED';

DROP USER [Phase8ARlsVerifier];
