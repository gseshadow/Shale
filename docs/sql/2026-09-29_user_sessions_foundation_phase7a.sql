/* Phase 7A: durable user-session foundation. Forward-only, additive, rerunnable, and not runtime-wired. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
BEGIN TRY
 BEGIN TRANSACTION;
 IF OBJECT_ID(N'dbo.Users',N'U') IS NULL OR OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL OR OBJECT_ID(N'dbo.ApplicationInstances',N'U') IS NULL
 BEGIN
  THROW 57700, 'Phase 7A prerequisites are missing.', 1;
 END;
 IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL
 BEGIN
  THROW 57701, 'Required strict tenant predicate is missing.', 1;
 END;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Users') AND name=N'UX_Users_ShaleClientId_Id' AND is_unique=1)
 BEGIN
  THROW 57702, 'Trusted tenant-qualified Users key is missing.', 1;
 END;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ApplicationInstances') AND name=N'UX_ApplicationInstances_ShaleClientId_Id' AND is_unique=1)
  CREATE UNIQUE INDEX UX_ApplicationInstances_ShaleClientId_Id ON dbo.ApplicationInstances(ShaleClientId,Id);
 IF OBJECT_ID(N'dbo.UserSessions',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.UserSessions(
   Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_UserSessions PRIMARY KEY,
   ShaleClientId int NOT NULL,
   UserId int NOT NULL,
   SessionId uniqueidentifier NOT NULL,
   ApplicationInstanceId bigint NULL,
   ClientType varchar(16) NOT NULL,
   CurrentAccessJti uniqueidentifier NOT NULL,
   IssuedAt datetime2(7) NOT NULL CONSTRAINT DF_UserSessions_IssuedAt DEFAULT(SYSUTCDATETIME()),
   ExpiresAt datetime2(7) NOT NULL,
   LastRefreshedAt datetime2(7) NULL,
   RevokedAt datetime2(7) NULL,
   RevocationReason varchar(32) NULL,
   CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_UserSessions_CreatedAt DEFAULT(SYSUTCDATETIME()),
   UpdatedAt datetime2(7) NOT NULL CONSTRAINT DF_UserSessions_UpdatedAt DEFAULT(SYSUTCDATETIME()),
   RowVer rowversion NOT NULL,
   CONSTRAINT UQ_UserSessions_SessionId UNIQUE(SessionId),
   CONSTRAINT UQ_UserSessions_CurrentAccessJti UNIQUE(CurrentAccessJti),
   CONSTRAINT CK_UserSessions_ClientType CHECK(ClientType IN('DESKTOP','WEB','MOBILE')),
   CONSTRAINT CK_UserSessions_InstanceClient CHECK(ApplicationInstanceId IS NULL OR ClientType='DESKTOP'),
   CONSTRAINT CK_UserSessions_Expiry CHECK(ExpiresAt>=IssuedAt),
   CONSTRAINT CK_UserSessions_Refresh CHECK(LastRefreshedAt IS NULL OR LastRefreshedAt>=IssuedAt),
   CONSTRAINT CK_UserSessions_Revocation CHECK((RevokedAt IS NULL AND RevocationReason IS NULL) OR (RevokedAt>=IssuedAt AND RevocationReason IN('USER_LOGOUT','ADMIN_REVOKED','SECURITY','CREDENTIAL_ROTATED','EXPIRED_REPLACEMENT'))),
   CONSTRAINT FK_UserSessions_Client FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id) ON DELETE NO ACTION,
   CONSTRAINT FK_UserSessions_UserTenant FOREIGN KEY(ShaleClientId,UserId) REFERENCES dbo.Users(ShaleClientId,Id) ON DELETE NO ACTION,
   CONSTRAINT FK_UserSessions_InstanceTenant FOREIGN KEY(ShaleClientId,ApplicationInstanceId) REFERENCES dbo.ApplicationInstances(ShaleClientId,Id) ON DELETE NO ACTION
  );
 END;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.UserSessions') AND name=N'IX_UserSessions_TenantUserIssued')
  CREATE INDEX IX_UserSessions_TenantUserIssued ON dbo.UserSessions(ShaleClientId,UserId,IssuedAt DESC);
 DECLARE @PolicyId int,@Policy nvarchar(517),@Sql nvarchar(max);
 IF (SELECT COUNT(*) FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1)<>1
 BEGIN
  THROW 57703, 'Enabled TenantFilter is missing or ambiguous.', 1;
 END;
 SELECT @PolicyId=object_id,@Policy=QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.UserSessions') AND predicate_type_desc=N'FILTER') BEGIN SET @Sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.UserSessions;';EXEC sys.sp_executesql @Sql;END;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.UserSessions') AND predicate_type_desc=N'BLOCK' AND operation_desc=N'AFTER INSERT') BEGIN SET @Sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.UserSessions AFTER INSERT;';EXEC sys.sp_executesql @Sql;END;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.UserSessions') AND predicate_type_desc=N'BLOCK' AND operation_desc=N'AFTER UPDATE') BEGIN SET @Sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.UserSessions AFTER UPDATE;';EXEC sys.sp_executesql @Sql;END;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO
/* No seed rows. No login, refresh, logout, JWT validation, endpoint, or client runtime reads this table in Phase 7A. */
