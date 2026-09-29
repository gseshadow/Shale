SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.UserSessions') AND name=N'CK_UserSessions_Revocation')
 ALTER TABLE dbo.UserSessions DROP CONSTRAINT CK_UserSessions_Revocation;
ALTER TABLE dbo.UserSessions WITH CHECK ADD CONSTRAINT CK_UserSessions_Revocation CHECK((RevokedAt IS NULL AND RevocationReason IS NULL) OR (RevokedAt>=IssuedAt AND RevocationReason IN('USER_LOGOUT','USER_REVOKED','ADMIN_REVOKED','SECURITY','CREDENTIAL_ROTATED','EXPIRED_REPLACEMENT')));
IF OBJECT_ID(N'dbo.SessionSecurityAuditLog',N'U') IS NULL
BEGIN
 CREATE TABLE dbo.SessionSecurityAuditLog(
  Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_SessionSecurityAuditLog PRIMARY KEY,
  ShaleClientId int NOT NULL, ActorUserId int NOT NULL, EventType varchar(40) NOT NULL,
  TargetSessionId uniqueidentifier NULL, TargetUserId int NULL, AffectedCount int NOT NULL,
  ReasonCode varchar(32) NOT NULL, OccurredAt datetime2(7) NOT NULL CONSTRAINT DF_SessionSecurityAuditLog_OccurredAt DEFAULT(SYSUTCDATETIME()),
  CONSTRAINT CK_SessionSecurityAuditLog_Event CHECK(EventType IN('SELF_LOGOUT','SELF_REVOKE','SELF_REVOKE_OTHERS','ADMIN_SESSION_LIST','ADMIN_REVOKE')),
  CONSTRAINT CK_SessionSecurityAuditLog_Reason CHECK(ReasonCode IN('USER_LOGOUT','USER_REVOKED','ADMIN_REVOKED','ADMIN_READ')),
  CONSTRAINT CK_SessionSecurityAuditLog_Count CHECK(AffectedCount>=0),
  CONSTRAINT FK_SessionSecurityAuditLog_Client FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id),
  CONSTRAINT FK_SessionSecurityAuditLog_Actor FOREIGN KEY(ShaleClientId,ActorUserId) REFERENCES dbo.Users(ShaleClientId,Id),
  CONSTRAINT FK_SessionSecurityAuditLog_TargetUser FOREIGN KEY(ShaleClientId,TargetUserId) REFERENCES dbo.Users(ShaleClientId,Id)
 );
 CREATE INDEX IX_SessionSecurityAuditLog_TenantOccurred ON dbo.SessionSecurityAuditLog(ShaleClientId,OccurredAt DESC,Id DESC);
END;
DECLARE @Policy nvarchar(517),@sql nvarchar(max);
SELECT @Policy=QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1;
IF @Policy IS NULL THROW 57801,'Enabled TenantFilter is required.',1;
IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.SessionSecurityAuditLog') AND operation_desc=N'FILTER') BEGIN SET @sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.SessionSecurityAuditLog';EXEC sys.sp_executesql @sql;END;
IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.SessionSecurityAuditLog') AND operation_desc=N'AFTER INSERT') BEGIN SET @sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.SessionSecurityAuditLog AFTER INSERT';EXEC sys.sp_executesql @sql;END;
IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.SessionSecurityAuditLog') AND operation_desc=N'AFTER UPDATE') BEGIN SET @sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.SessionSecurityAuditLog AFTER UPDATE';EXEC sys.sp_executesql @sql;END;
COMMIT;
