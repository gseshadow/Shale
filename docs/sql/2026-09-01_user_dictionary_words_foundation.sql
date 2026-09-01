/* Per-user custom spelling dictionary. Stores words only, never narrative text. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
BEGIN TRY
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL THROW 55000,'Required table dbo.ShaleClients is missing.',1;
IF OBJECT_ID(N'dbo.Users',N'U') IS NULL THROW 55001,'Required table dbo.Users is missing.',1;
IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL THROW 55002,'Required strict tenant predicate is missing.',1;

IF OBJECT_ID(N'dbo.UserDictionaryWords',N'U') IS NULL
BEGIN
 CREATE TABLE dbo.UserDictionaryWords(
  Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_UserDictionaryWords PRIMARY KEY,
  ShaleClientId int NOT NULL,
  UserId int NOT NULL,
  Word nvarchar(190) NOT NULL,
  NormalizedWord nvarchar(190) NOT NULL,
  CreatedAt datetime2(3) NOT NULL CONSTRAINT DF_UserDictionaryWords_CreatedAt DEFAULT SYSUTCDATETIME(),
  CreatedByUserId int NOT NULL,
  UpdatedAt datetime2(3) NOT NULL CONSTRAINT DF_UserDictionaryWords_UpdatedAt DEFAULT SYSUTCDATETIME(),
  UpdatedByUserId int NOT NULL,
  RowVer rowversion NOT NULL,
  CONSTRAINT CK_UserDictionaryWords_Word_NotBlank CHECK(LEN(LTRIM(RTRIM(Word)))>0),
  CONSTRAINT CK_UserDictionaryWords_NormalizedWord_NotBlank CHECK(LEN(LTRIM(RTRIM(NormalizedWord)))>0)
 );
END;
IF OBJECT_ID(N'dbo.FK_UserDictionaryWords_Client',N'F') IS NULL ALTER TABLE dbo.UserDictionaryWords ADD CONSTRAINT FK_UserDictionaryWords_Client FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id);
IF OBJECT_ID(N'dbo.FK_UserDictionaryWords_UserTenant',N'F') IS NULL ALTER TABLE dbo.UserDictionaryWords ADD CONSTRAINT FK_UserDictionaryWords_UserTenant FOREIGN KEY(ShaleClientId,UserId) REFERENCES dbo.Users(ShaleClientId,Id);
IF OBJECT_ID(N'dbo.FK_UserDictionaryWords_CreatedBy',N'F') IS NULL ALTER TABLE dbo.UserDictionaryWords ADD CONSTRAINT FK_UserDictionaryWords_CreatedBy FOREIGN KEY(CreatedByUserId) REFERENCES dbo.Users(Id);
IF OBJECT_ID(N'dbo.FK_UserDictionaryWords_UpdatedBy',N'F') IS NULL ALTER TABLE dbo.UserDictionaryWords ADD CONSTRAINT FK_UserDictionaryWords_UpdatedBy FOREIGN KEY(UpdatedByUserId) REFERENCES dbo.Users(Id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.UserDictionaryWords') AND name=N'UX_UserDictionaryWords_ClientUserNormalized') CREATE UNIQUE INDEX UX_UserDictionaryWords_ClientUserNormalized ON dbo.UserDictionaryWords(ShaleClientId,UserId,NormalizedWord);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.UserDictionaryWords') AND name=N'IX_UserDictionaryWords_ClientUser') CREATE INDEX IX_UserDictionaryWords_ClientUser ON dbo.UserDictionaryWords(ShaleClientId,UserId) INCLUDE(Word,NormalizedWord,UpdatedAt);

DECLARE @PolicyId int,@PolicyQualified nvarchar(517),@Sql nvarchar(max);
SELECT @PolicyId=object_id,@PolicyQualified=QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1;
IF @PolicyId IS NULL THROW 55003,'Enabled TenantFilter security policy is required.',1;
IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.UserDictionaryWords') AND predicate_type_desc=N'FILTER') BEGIN SET @Sql=N'ALTER SECURITY POLICY '+@PolicyQualified+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.UserDictionaryWords;';EXEC sys.sp_executesql @Sql;END;
COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO
