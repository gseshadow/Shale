/* Forward-only validation foundation. No cleanup, backfill or syntax constraints.
   Run before deploying validation-capable desktop/server clients; see validation rollout document. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
 DECLARE @ExpectedDatabase sysname=N'REPLACE_WITH_APPROVED_DATABASE';
 DECLARE @OperatorVerifiedAllTenantVisibility bit=0;
 IF @ExpectedDatabase=N'REPLACE_WITH_APPROVED_DATABASE' OR DB_NAME()<>@ExpectedDatabase
  THROW 57800,'Select and acknowledge the approved database.',1;
 IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
  THROW 57801,'Use null tenant and actor context for administrative deployment.',1;
 IF USER_NAME() IN(N'shale_app',N'shale_runtime') OR
  (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)<>1 AND ISNULL(IS_MEMBER(N'db_owner'),0)<>1)
  THROW 57802,'An approved administrative deployment principal is required.',1;
 IF @OperatorVerifiedAllTenantVisibility<>1 THROW 57803,'Independently verify all-tenant visibility first.',1;
 IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL THROW 57804,'Strict tenant predicate is required.',1;
 DECLARE @Policy nvarchar(517),@PolicyId int,@sql nvarchar(max);
 IF (SELECT COUNT(*) FROM sys.security_policies WHERE name=N'TenantFilter')<>1 THROW 57805,'Exactly one TenantFilter is required.',1;
 SELECT @PolicyId=object_id,@Policy=QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name)
 FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1;
 IF @PolicyId IS NULL THROW 57806,'Enabled TenantFilter is required.',1;
 IF OBJECT_ID(N'dbo.Cases',N'U') IS NULL OR OBJECT_ID(N'dbo.Contacts',N'U') IS NULL OR OBJECT_ID(N'dbo.Users',N'U') IS NULL
  THROW 57807,'Intake parent tables are required.',1;
 BEGIN TRANSACTION;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Cases') AND name=N'UX_Cases_ShaleClientId_Id')
  CREATE UNIQUE INDEX UX_Cases_ShaleClientId_Id ON dbo.Cases(ShaleClientId,Id);
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Contacts') AND name=N'UX_Contacts_ShaleClientId_Id')
  CREATE UNIQUE INDEX UX_Contacts_ShaleClientId_Id ON dbo.Contacts(ShaleClientId,Id);
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Users') AND name=N'UX_Users_ShaleClientId_Id')
  CREATE UNIQUE INDEX UX_Users_ShaleClientId_Id ON dbo.Users(ShaleClientId,Id);
 IF OBJECT_ID(N'dbo.IntakePhoneAvailability',N'U') IS NULL
 CREATE TABLE dbo.IntakePhoneAvailability(
  Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_IntakePhoneAvailability PRIMARY KEY,
  ShaleClientId int NOT NULL,CaseId int NOT NULL,ContactId int NOT NULL,
  IntakeRole nvarchar(6) NOT NULL,UnavailableReason nvarchar(12) NULL,
  CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_IntakePhoneAvailability_CreatedAt DEFAULT(SYSUTCDATETIME()),
  CreatedByUserId int NOT NULL,RowVer rowversion NOT NULL,
  CONSTRAINT CK_IntakePhoneAvailability_Role CHECK(IntakeRole IN(N'CLIENT',N'CALLER')),
  CONSTRAINT CK_IntakePhoneAvailability_Reason CHECK(UnavailableReason IS NULL OR UnavailableReason IN(N'UNKNOWN',N'NOT_PROVIDED',N'NO_PHONE')),
  CONSTRAINT FK_IntakePhoneAvailability_Case FOREIGN KEY(ShaleClientId,CaseId) REFERENCES dbo.Cases(ShaleClientId,Id),
  CONSTRAINT FK_IntakePhoneAvailability_Contact FOREIGN KEY(ShaleClientId,ContactId) REFERENCES dbo.Contacts(ShaleClientId,Id),
  CONSTRAINT FK_IntakePhoneAvailability_Actor FOREIGN KEY(ShaleClientId,CreatedByUserId) REFERENCES dbo.Users(ShaleClientId,Id));
 IF EXISTS(SELECT 1 FROM (VALUES
  (N'Id',N'bigint',8,0),(N'ShaleClientId',N'int',4,0),(N'CaseId',N'int',4,0),(N'ContactId',N'int',4,0),
  (N'IntakeRole',N'nvarchar',12,0),(N'UnavailableReason',N'nvarchar',24,1),(N'CreatedAt',N'datetime2',8,0),
  (N'CreatedByUserId',N'int',4,0),(N'RowVer',N'timestamp',8,0)) expected(ColumnName,TypeName,Bytes,Nullable)
  WHERE NOT EXISTS(SELECT 1 FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.IntakePhoneAvailability')
   AND c.name=expected.ColumnName AND TYPE_NAME(c.user_type_id)=expected.TypeName AND c.max_length=expected.Bytes AND c.is_nullable=expected.Nullable))
  THROW 57808,'Existing intake availability schema differs from the reviewed contract.',1;
 IF (SELECT COUNT(*) FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.IntakePhoneAvailability')
  AND name IN(N'FK_IntakePhoneAvailability_Case',N'FK_IntakePhoneAvailability_Contact',N'FK_IntakePhoneAvailability_Actor') AND is_disabled=0 AND is_not_trusted=0)<>3
  THROW 57812,'Trusted availability ownership foreign keys are required.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.IntakePhoneAvailability') AND name=N'IX_IntakePhoneAvailability_CaseRole')
  CREATE INDEX IX_IntakePhoneAvailability_CaseRole ON dbo.IntakePhoneAvailability(ShaleClientId,CaseId,IntakeRole,Id DESC);
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.IntakePhoneAvailability') AND predicate_type_desc=N'FILTER')
 BEGIN SET @sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.IntakePhoneAvailability;';EXEC sys.sp_executesql @sql;END;
 IF EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.IntakePhoneAvailability') AND predicate_type_desc=N'FILTER' AND predicate_definition NOT LIKE N'%fn_FilterByTenant%')
  THROW 57813,'Unexpected availability tenant predicate; review before deployment.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.IntakePhoneAvailability') AND operation_desc=N'AFTER INSERT')
 BEGIN SET @sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.IntakePhoneAvailability AFTER INSERT;';EXEC sys.sp_executesql @sql;END;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.IntakePhoneAvailability') AND operation_desc=N'AFTER UPDATE')
 BEGIN SET @sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.IntakePhoneAvailability AFTER UPDATE;';EXEC sys.sp_executesql @sql;END;
 IF EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.IntakePhoneAvailability') AND predicate_type_desc=N'BLOCK' AND operation_desc IN(N'AFTER INSERT',N'AFTER UPDATE') AND predicate_definition NOT LIKE N'%fn_FilterByTenant%')
  THROW 57814,'Unexpected availability write predicate; review before deployment.',1;
 /* Mirror widths must accommodate every structured value; never truncate values. */
 IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.Organizations') AND name IN(N'Phone',N'Fax',N'Email') AND (TYPE_NAME(user_type_id)<>N'nvarchar' OR is_nullable<>1))
  THROW 57809,'Organization mirror type/nullability differs from the expected contract.',1;
 IF COL_LENGTH(N'dbo.Organizations',N'Phone') IS NULL OR COL_LENGTH(N'dbo.Organizations',N'Fax') IS NULL OR COL_LENGTH(N'dbo.Organizations',N'Email') IS NULL
  THROW 57810,'Organization mirrors are missing.',1;
 IF COL_LENGTH(N'dbo.Organizations',N'Phone') BETWEEN 1 AND 509 ALTER TABLE dbo.Organizations ALTER COLUMN Phone nvarchar(255) NULL;
 IF COL_LENGTH(N'dbo.Organizations',N'Fax') BETWEEN 1 AND 509 ALTER TABLE dbo.Organizations ALTER COLUMN Fax nvarchar(255) NULL;
 IF COL_LENGTH(N'dbo.Organizations',N'Email') BETWEEN 1 AND 639 ALTER TABLE dbo.Organizations ALTER COLUMN Email nvarchar(320) NULL;
 IF OBJECT_ID(N'dbo.OrganizationPhoneNumbers',N'U') IS NULL THROW 57811,'Structured Organization phones are required.',1;
 IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OrganizationPhoneNumbers') AND name=N'UX_OrganizationPhoneNumbers_ActiveValue')
  DROP INDEX UX_OrganizationPhoneNumbers_ActiveValue ON dbo.OrganizationPhoneNumbers;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OrganizationPhoneNumbers') AND name=N'UX_OrganizationPhoneNumbers_ActiveValueExtension')
  CREATE UNIQUE INDEX UX_OrganizationPhoneNumbers_ActiveValueExtension ON dbo.OrganizationPhoneNumbers(ShaleClientId,OrganizationId,Kind,DisplayNumber,Extension) WHERE IsDeleted=0;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
