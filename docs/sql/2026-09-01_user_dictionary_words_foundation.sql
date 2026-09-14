/* Per-user custom spelling dictionary. Stores words only, never narrative text. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
BEGIN TRY
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL THROW 55000,'Required table dbo.ShaleClients is missing.',1;
IF OBJECT_ID(N'dbo.Users',N'U') IS NULL THROW 55001,'Required table dbo.Users is missing.',1;
IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL THROW 55002,'Required strict tenant predicate is missing.',1;
IF COL_LENGTH(N'dbo.Users',N'Id') IS NULL OR COL_LENGTH(N'dbo.Users',N'ShaleClientId') IS NULL THROW 55003,'dbo.Users must expose Id and ShaleClientId.',1;
IF EXISTS(SELECT 1 FROM sys.columns c JOIN sys.types t ON t.user_type_id=c.user_type_id WHERE c.object_id=OBJECT_ID(N'dbo.Users') AND c.name IN(N'Id',N'ShaleClientId') AND t.name<>N'int')
 OR EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.Users') AND name=N'Id' AND is_nullable=1)
 THROW 55012,'dbo.Users.Id must be a non-null int and ShaleClientId must be int (it may be nullable in the deployed schema).',1;

/* Id is the deployed Users primary key and remains independently FK-addressable. */
IF NOT EXISTS(
 SELECT 1 FROM sys.indexes i
 WHERE i.object_id=OBJECT_ID(N'dbo.Users') AND i.is_unique=1 AND i.is_disabled=0
 AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)=1
 AND EXISTS(SELECT 1 FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal=1 AND COL_NAME(ic.object_id,ic.column_id)=N'Id')
) THROW 55004,'dbo.Users.Id must be independently unique and FK-addressable.',1;

/* New tenant-owned references use the tenant-qualified alternate key. */
IF NOT EXISTS(
 SELECT 1 FROM sys.indexes i
 WHERE i.object_id=OBJECT_ID(N'dbo.Users') AND i.is_unique=1 AND i.is_disabled=0
 AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)=2
 AND EXISTS(SELECT 1 FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal=1 AND COL_NAME(ic.object_id,ic.column_id)=N'ShaleClientId')
 AND EXISTS(SELECT 1 FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal=2 AND COL_NAME(ic.object_id,ic.column_id)=N'Id')
)
BEGIN
 IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Users') AND name=N'UX_Users_ShaleClientId_Id') THROW 55005,'UX_Users_ShaleClientId_Id exists with an unexpected definition.',1;
 CREATE UNIQUE INDEX UX_Users_ShaleClientId_Id ON dbo.Users(ShaleClientId,Id);
END;

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
IF EXISTS(SELECT 1 FROM (VALUES
 (N'Id',N'bigint',8),(N'ShaleClientId',N'int',4),(N'UserId',N'int',4),(N'Word',N'nvarchar',380),(N'NormalizedWord',N'nvarchar',380),
 (N'CreatedAt',N'datetime2',7),(N'CreatedByUserId',N'int',4),(N'UpdatedAt',N'datetime2',7),(N'UpdatedByUserId',N'int',4),(N'RowVer',N'timestamp',8)
 ) expected(ColumnName,TypeName,MaxLength)
 LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.UserDictionaryWords') AND c.name=expected.ColumnName
 LEFT JOIN sys.types t ON t.user_type_id=c.user_type_id
 WHERE c.column_id IS NULL OR t.name<>expected.TypeName OR c.max_length<>expected.MaxLength OR c.is_nullable=1
) THROW 55010,'UserDictionaryWords has a missing or incompatible column.',1;
IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.UserDictionaryWords') AND name IN(N'CreatedAt',N'UpdatedAt') AND scale<>3) THROW 55013,'UserDictionaryWords timestamps must be datetime2(3).',1;
/* Upgrade an earlier unqualified draft safely; never drop a same-named unknown FK. */
IF OBJECT_ID(N'dbo.FK_UserDictionaryWords_CreatedBy',N'F') IS NOT NULL
BEGIN
 IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id
  WHERE fk.object_id=OBJECT_ID(N'dbo.FK_UserDictionaryWords_CreatedBy') AND fk.parent_object_id=OBJECT_ID(N'dbo.UserDictionaryWords') AND fk.referenced_object_id=OBJECT_ID(N'dbo.Users')
  AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id=fk.object_id)=1
  AND COL_NAME(fkc.parent_object_id,fkc.parent_column_id)=N'CreatedByUserId' AND COL_NAME(fkc.referenced_object_id,fkc.referenced_column_id)=N'Id') THROW 55014,'Legacy CreatedBy FK has an unexpected definition.',1;
 ALTER TABLE dbo.UserDictionaryWords DROP CONSTRAINT FK_UserDictionaryWords_CreatedBy;
END;
IF OBJECT_ID(N'dbo.FK_UserDictionaryWords_UpdatedBy',N'F') IS NOT NULL
BEGIN
 IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id
  WHERE fk.object_id=OBJECT_ID(N'dbo.FK_UserDictionaryWords_UpdatedBy') AND fk.parent_object_id=OBJECT_ID(N'dbo.UserDictionaryWords') AND fk.referenced_object_id=OBJECT_ID(N'dbo.Users')
  AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id=fk.object_id)=1
  AND COL_NAME(fkc.parent_object_id,fkc.parent_column_id)=N'UpdatedByUserId' AND COL_NAME(fkc.referenced_object_id,fkc.referenced_column_id)=N'Id') THROW 55015,'Legacy UpdatedBy FK has an unexpected definition.',1;
 ALTER TABLE dbo.UserDictionaryWords DROP CONSTRAINT FK_UserDictionaryWords_UpdatedBy;
END;
IF OBJECT_ID(N'dbo.FK_UserDictionaryWords_Client',N'F') IS NULL ALTER TABLE dbo.UserDictionaryWords WITH CHECK ADD CONSTRAINT FK_UserDictionaryWords_Client FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id);
IF OBJECT_ID(N'dbo.FK_UserDictionaryWords_UserTenant',N'F') IS NULL ALTER TABLE dbo.UserDictionaryWords WITH CHECK ADD CONSTRAINT FK_UserDictionaryWords_UserTenant FOREIGN KEY(ShaleClientId,UserId) REFERENCES dbo.Users(ShaleClientId,Id);
IF OBJECT_ID(N'dbo.FK_UserDictionaryWords_CreatedByTenant',N'F') IS NULL ALTER TABLE dbo.UserDictionaryWords WITH CHECK ADD CONSTRAINT FK_UserDictionaryWords_CreatedByTenant FOREIGN KEY(ShaleClientId,CreatedByUserId) REFERENCES dbo.Users(ShaleClientId,Id);
IF OBJECT_ID(N'dbo.FK_UserDictionaryWords_UpdatedByTenant',N'F') IS NULL ALTER TABLE dbo.UserDictionaryWords WITH CHECK ADD CONSTRAINT FK_UserDictionaryWords_UpdatedByTenant FOREIGN KEY(ShaleClientId,UpdatedByUserId) REFERENCES dbo.Users(ShaleClientId,Id);

/* A same-named object is not sufficient: verify every FK column mapping and trust state. */
IF EXISTS(
 SELECT 1 FROM (VALUES
  (N'FK_UserDictionaryWords_Client',1,N'ShaleClientId',N'dbo.ShaleClients',N'Id'),
  (N'FK_UserDictionaryWords_UserTenant',1,N'ShaleClientId',N'dbo.Users',N'ShaleClientId'),(N'FK_UserDictionaryWords_UserTenant',2,N'UserId',N'dbo.Users',N'Id'),
  (N'FK_UserDictionaryWords_CreatedByTenant',1,N'ShaleClientId',N'dbo.Users',N'ShaleClientId'),(N'FK_UserDictionaryWords_CreatedByTenant',2,N'CreatedByUserId',N'dbo.Users',N'Id'),
  (N'FK_UserDictionaryWords_UpdatedByTenant',1,N'ShaleClientId',N'dbo.Users',N'ShaleClientId'),(N'FK_UserDictionaryWords_UpdatedByTenant',2,N'UpdatedByUserId',N'dbo.Users',N'Id')
 ) expected(FkName,Ordinal,ParentColumn,ReferencedTable,ReferencedColumn)
 LEFT JOIN sys.foreign_keys fk ON fk.parent_object_id=OBJECT_ID(N'dbo.UserDictionaryWords') AND fk.name=expected.FkName
 LEFT JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id AND fkc.constraint_column_id=expected.Ordinal
 WHERE fk.object_id IS NULL OR fk.is_disabled=1 OR fk.is_not_trusted=1 OR fk.referenced_object_id<>OBJECT_ID(expected.ReferencedTable)
 OR COL_NAME(fkc.parent_object_id,fkc.parent_column_id)<>expected.ParentColumn OR COL_NAME(fkc.referenced_object_id,fkc.referenced_column_id)<>expected.ReferencedColumn
) THROW 55006,'UserDictionaryWords foreign-key definition or trust verification failed.',1;
IF EXISTS(SELECT 1 FROM (VALUES(N'FK_UserDictionaryWords_Client',1),(N'FK_UserDictionaryWords_UserTenant',2),(N'FK_UserDictionaryWords_CreatedByTenant',2),(N'FK_UserDictionaryWords_UpdatedByTenant',2)) expected(FkName,ColumnCount)
 JOIN sys.foreign_keys fk ON fk.parent_object_id=OBJECT_ID(N'dbo.UserDictionaryWords') AND fk.name=expected.FkName
 WHERE (SELECT COUNT(*) FROM sys.foreign_key_columns fkc WHERE fkc.constraint_object_id=fk.object_id)<>expected.ColumnCount
) THROW 55011,'UserDictionaryWords foreign key contains unexpected additional columns.',1;

IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.UserDictionaryWords') AND name=N'UX_UserDictionaryWords_ClientUserNormalized') CREATE UNIQUE INDEX UX_UserDictionaryWords_ClientUserNormalized ON dbo.UserDictionaryWords(ShaleClientId,UserId,NormalizedWord);
IF NOT EXISTS(SELECT 1 FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.UserDictionaryWords') AND i.name=N'UX_UserDictionaryWords_ClientUserNormalized' AND i.is_unique=1 AND i.is_disabled=0
 AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)=3
 AND EXISTS(SELECT 1 FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal=1 AND COL_NAME(ic.object_id,ic.column_id)=N'ShaleClientId')
 AND EXISTS(SELECT 1 FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal=2 AND COL_NAME(ic.object_id,ic.column_id)=N'UserId')
 AND EXISTS(SELECT 1 FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal=3 AND COL_NAME(ic.object_id,ic.column_id)=N'NormalizedWord')) THROW 55007,'Dictionary uniqueness index has an unexpected definition.',1;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.UserDictionaryWords') AND name=N'IX_UserDictionaryWords_ClientUser') CREATE INDEX IX_UserDictionaryWords_ClientUser ON dbo.UserDictionaryWords(ShaleClientId,UserId) INCLUDE(Word,NormalizedWord,UpdatedAt);

DECLARE @PolicyId int,@PolicyQualified nvarchar(517),@Sql nvarchar(max);
IF (SELECT COUNT(*) FROM sys.security_policies WHERE name=N'TenantFilter')<>1 THROW 55016,'TenantFilter security policy is missing or ambiguous.',1;
SELECT @PolicyId=object_id,@PolicyQualified=QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name) FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1;
IF @PolicyId IS NULL THROW 55008,'Enabled TenantFilter security policy is required.',1;
IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.UserDictionaryWords') AND predicate_type_desc=N'FILTER') BEGIN SET @Sql=N'ALTER SECURITY POLICY '+@PolicyQualified+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.UserDictionaryWords;';EXEC sys.sp_executesql @Sql;END;
IF NOT EXISTS(SELECT 1 FROM sys.security_predicates sp WHERE sp.object_id=@PolicyId AND sp.target_object_id=OBJECT_ID(N'dbo.UserDictionaryWords') AND sp.predicate_type_desc=N'FILTER' AND CHARINDEX(N'fn_FilterByTenant',sp.predicate_definition)>0 AND CHARINDEX(N'ShaleClientId',sp.predicate_definition)>0) THROW 55009,'UserDictionaryWords TenantFilter predicate has an unexpected definition.',1;
COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO
