
    
  
/*
 Phase 1A global application-release catalog foundation.
 REVIEW/APPLY MANUALLY IN SSMS OR SQLCMD. Forward-only, additive, transactional,
 and rerunnable. Creates storage only; it does not seed releases or change the
 static updater manifest, update discovery, publication, policy, or enforcement.

 These are global control-plane tables. They intentionally have no ShaleClientId
 and no RLS predicate. A later phase must define operator mutation authority and
 append release changes/publication to EntityActionAuditLog transactionally.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
 BEGIN TRANSACTION;

 IF OBJECT_ID(N'dbo.Users',N'U') IS NULL
  THROW 57200,'Required Users table is missing.',1;

 IF OBJECT_ID(N'dbo.ApplicationReleases',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.ApplicationReleases(
   Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_ApplicationReleases PRIMARY KEY,
   MajorVersion int NOT NULL,
   MinorVersion int NOT NULL,
   BuildVersion int NOT NULL,
   ApplicationVersion AS (CONVERT(varchar(10),MajorVersion)+'.'+CONVERT(varchar(10),MinorVersion)+'.'+CONVERT(varchar(10),BuildVersion)) PERSISTED,
   ReleaseChannel varchar(32) NOT NULL CONSTRAINT DF_ApplicationReleases_ReleaseChannel DEFAULT('PRODUCTION'),
   PublicationStatus varchar(16) NOT NULL CONSTRAINT DF_ApplicationReleases_PublicationStatus DEFAULT('DRAFT'),
   PublishedAt datetime2(7) NULL,
   PublishedByUserId int NULL,
   Summary nvarchar(510) NOT NULL,
   CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_ApplicationReleases_CreatedAt DEFAULT(SYSUTCDATETIME()),
   CreatedByUserId int NULL,
   UpdatedAt datetime2(7) NULL,
   UpdatedByUserId int NULL,
   RowVer rowversion NOT NULL,
   CONSTRAINT FK_ApplicationReleases_PublishedByUser FOREIGN KEY(PublishedByUserId) REFERENCES dbo.Users(id),
   CONSTRAINT FK_ApplicationReleases_CreatedByUser FOREIGN KEY(CreatedByUserId) REFERENCES dbo.Users(id),
   CONSTRAINT FK_ApplicationReleases_UpdatedByUser FOREIGN KEY(UpdatedByUserId) REFERENCES dbo.Users(id),
   CONSTRAINT CK_ApplicationReleases_VersionComponents CHECK(MajorVersion>=0 AND MinorVersion>=0 AND BuildVersion>=0),
   CONSTRAINT CK_ApplicationReleases_Channel CHECK(ReleaseChannel IN('PRODUCTION','PILOT','DEVELOPMENT')),
   CONSTRAINT CK_ApplicationReleases_PublicationStatus CHECK(PublicationStatus IN('DRAFT','PUBLISHED')),
   CONSTRAINT CK_ApplicationReleases_PublicationLifecycle CHECK((PublicationStatus='DRAFT' AND PublishedAt IS NULL AND PublishedByUserId IS NULL) OR (PublicationStatus='PUBLISHED' AND PublishedAt IS NOT NULL)),
   CONSTRAINT CK_ApplicationReleases_Summary CHECK(LEN(LTRIM(RTRIM(Summary)))>0)
  );
 END;

 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ApplicationReleases') AND name=N'UX_ApplicationReleases_Channel_Version')
  CREATE UNIQUE INDEX UX_ApplicationReleases_Channel_Version
   ON dbo.ApplicationReleases(ReleaseChannel,MajorVersion,MinorVersion,BuildVersion);
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ApplicationReleases') AND name=N'IX_ApplicationReleases_Channel_Publication_Version')
  CREATE INDEX IX_ApplicationReleases_Channel_Publication_Version
   ON dbo.ApplicationReleases(ReleaseChannel,PublicationStatus,MajorVersion DESC,MinorVersion DESC,BuildVersion DESC);

 IF OBJECT_ID(N'dbo.ApplicationReleaseItems',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.ApplicationReleaseItems(
   Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_ApplicationReleaseItems PRIMARY KEY,
   ApplicationReleaseId bigint NOT NULL,
   SortOrder int NOT NULL,
   ItemType varchar(32) NOT NULL,
   Title nvarchar(200) NOT NULL,
   Body nvarchar(2000) NOT NULL,
   ResourceUrl nvarchar(2048) NULL,
   IsActive bit NOT NULL CONSTRAINT DF_ApplicationReleaseItems_IsActive DEFAULT(1),
   CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_ApplicationReleaseItems_CreatedAt DEFAULT(SYSUTCDATETIME()),
   CreatedByUserId int NULL,
   UpdatedAt datetime2(7) NULL,
   UpdatedByUserId int NULL,
   RowVer rowversion NOT NULL,
   CONSTRAINT FK_ApplicationReleaseItems_Release FOREIGN KEY(ApplicationReleaseId) REFERENCES dbo.ApplicationReleases(Id) ON DELETE NO ACTION,
   CONSTRAINT FK_ApplicationReleaseItems_CreatedByUser FOREIGN KEY(CreatedByUserId) REFERENCES dbo.Users(id),
   CONSTRAINT FK_ApplicationReleaseItems_UpdatedByUser FOREIGN KEY(UpdatedByUserId) REFERENCES dbo.Users(id),
   CONSTRAINT CK_ApplicationReleaseItems_SortOrder CHECK(SortOrder>=0),
   CONSTRAINT CK_ApplicationReleaseItems_ItemType CHECK(ItemType IN('FEATURE','FIX','IMPROVEMENT','IMPORTANT','LINK','VIDEO')),
   CONSTRAINT CK_ApplicationReleaseItems_Title CHECK(LEN(LTRIM(RTRIM(Title)))>0),
   CONSTRAINT CK_ApplicationReleaseItems_Body CHECK(LEN(LTRIM(RTRIM(Body)))>0)
  );
 END;

 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ApplicationReleaseItems') AND name=N'UX_ApplicationReleaseItems_Release_SortOrder')
  CREATE UNIQUE INDEX UX_ApplicationReleaseItems_Release_SortOrder
   ON dbo.ApplicationReleaseItems(ApplicationReleaseId,SortOrder);

 /* Closed rerun contract: never accept familiar names with an incompatible shape. */
 DECLARE @ExpectedColumns table(TableName sysname,ColumnName sysname,TypeName sysname,MaxLength smallint,IsNullable bit,IsIdentity bit,IsComputed bit);
 INSERT @ExpectedColumns VALUES
 (N'ApplicationReleases',N'Id',N'bigint',8,0,1,0),(N'ApplicationReleases',N'MajorVersion',N'int',4,0,0,0),(N'ApplicationReleases',N'MinorVersion',N'int',4,0,0,0),(N'ApplicationReleases',N'BuildVersion',N'int',4,0,0,0),
 (N'ApplicationReleases',N'ApplicationVersion',N'varchar',32,1,0,1),(N'ApplicationReleases',N'ReleaseChannel',N'varchar',32,0,0,0),(N'ApplicationReleases',N'PublicationStatus',N'varchar',16,0,0,0),
 (N'ApplicationReleases',N'PublishedAt',N'datetime2',8,1,0,0),(N'ApplicationReleases',N'PublishedByUserId',N'int',4,1,0,0),(N'ApplicationReleases',N'Summary',N'nvarchar',1020,0,0,0),
 (N'ApplicationReleases',N'CreatedAt',N'datetime2',8,0,0,0),(N'ApplicationReleases',N'CreatedByUserId',N'int',4,1,0,0),(N'ApplicationReleases',N'UpdatedAt',N'datetime2',8,1,0,0),(N'ApplicationReleases',N'UpdatedByUserId',N'int',4,1,0,0),(N'ApplicationReleases',N'RowVer',N'timestamp',8,0,0,0),
 (N'ApplicationReleaseItems',N'Id',N'bigint',8,0,1,0),(N'ApplicationReleaseItems',N'ApplicationReleaseId',N'bigint',8,0,0,0),(N'ApplicationReleaseItems',N'SortOrder',N'int',4,0,0,0),(N'ApplicationReleaseItems',N'ItemType',N'varchar',32,0,0,0),
 (N'ApplicationReleaseItems',N'Title',N'nvarchar',400,0,0,0),(N'ApplicationReleaseItems',N'Body',N'nvarchar',4000,0,0,0),(N'ApplicationReleaseItems',N'ResourceUrl',N'nvarchar',4096,1,0,0),(N'ApplicationReleaseItems',N'IsActive',N'bit',1,0,0,0),
 (N'ApplicationReleaseItems',N'CreatedAt',N'datetime2',8,0,0,0),(N'ApplicationReleaseItems',N'CreatedByUserId',N'int',4,1,0,0),(N'ApplicationReleaseItems',N'UpdatedAt',N'datetime2',8,1,0,0),(N'ApplicationReleaseItems',N'UpdatedByUserId',N'int',4,1,0,0),(N'ApplicationReleaseItems',N'RowVer',N'timestamp',8,0,0,0);
 IF EXISTS(SELECT 1 FROM @ExpectedColumns e LEFT JOIN sys.tables t ON t.name COLLATE DATABASE_DEFAULT=e.TableName AND SCHEMA_NAME(t.schema_id)=N'dbo'
  LEFT JOIN sys.columns c ON c.object_id=t.object_id AND c.name COLLATE DATABASE_DEFAULT=e.ColumnName LEFT JOIN sys.types ty ON ty.user_type_id=c.user_type_id
  WHERE c.column_id IS NULL OR ty.name COLLATE DATABASE_DEFAULT<>e.TypeName OR c.max_length<>e.MaxLength OR c.is_nullable<>e.IsNullable OR c.is_identity<>e.IsIdentity OR c.is_computed<>e.IsComputed)
  THROW 57201,'A required release-catalog column is missing or incompatible.',1;
 IF EXISTS(SELECT 1 FROM sys.tables t JOIN sys.columns c ON c.object_id=t.object_id WHERE t.object_id IN(OBJECT_ID(N'dbo.ApplicationReleases'),OBJECT_ID(N'dbo.ApplicationReleaseItems')) AND c.name=N'ShaleClientId')
  THROW 57202,'Global release-catalog tables must not contain ShaleClientId.',1;
 IF EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id IN(OBJECT_ID(N'dbo.ApplicationReleases'),OBJECT_ID(N'dbo.ApplicationReleaseItems')))
  THROW 57203,'Global release-catalog tables must not have an RLS security predicate.',1;
 IF EXISTS(SELECT 1 FROM sys.foreign_keys WHERE parent_object_id IN(OBJECT_ID(N'dbo.ApplicationReleases'),OBJECT_ID(N'dbo.ApplicationReleaseItems')) AND (is_disabled=1 OR is_not_trusted=1))
  THROW 57204,'Release-catalog foreign keys must be enabled and trusted.',1;

 DECLARE @RequiredObjects table(TableName sysname,ObjectName sysname,ObjectType char(2));
 INSERT @RequiredObjects VALUES
 (N'ApplicationReleases',N'PK_ApplicationReleases','PK'),(N'ApplicationReleases',N'FK_ApplicationReleases_PublishedByUser','F'),(N'ApplicationReleases',N'FK_ApplicationReleases_CreatedByUser','F'),(N'ApplicationReleases',N'FK_ApplicationReleases_UpdatedByUser','F'),
 (N'ApplicationReleases',N'CK_ApplicationReleases_VersionComponents','C'),(N'ApplicationReleases',N'CK_ApplicationReleases_Channel','C'),(N'ApplicationReleases',N'CK_ApplicationReleases_PublicationStatus','C'),(N'ApplicationReleases',N'CK_ApplicationReleases_PublicationLifecycle','C'),(N'ApplicationReleases',N'CK_ApplicationReleases_Summary','C'),
 (N'ApplicationReleaseItems',N'PK_ApplicationReleaseItems','PK'),(N'ApplicationReleaseItems',N'FK_ApplicationReleaseItems_Release','F'),(N'ApplicationReleaseItems',N'FK_ApplicationReleaseItems_CreatedByUser','F'),(N'ApplicationReleaseItems',N'FK_ApplicationReleaseItems_UpdatedByUser','F'),
 (N'ApplicationReleaseItems',N'CK_ApplicationReleaseItems_SortOrder','C'),(N'ApplicationReleaseItems',N'CK_ApplicationReleaseItems_ItemType','C'),(N'ApplicationReleaseItems',N'CK_ApplicationReleaseItems_Title','C'),(N'ApplicationReleaseItems',N'CK_ApplicationReleaseItems_Body','C');
 IF EXISTS(SELECT 1 FROM @RequiredObjects e LEFT JOIN sys.tables t ON t.name COLLATE DATABASE_DEFAULT=e.TableName AND SCHEMA_NAME(t.schema_id)=N'dbo'
  LEFT JOIN sys.objects o ON o.parent_object_id=t.object_id AND o.name COLLATE DATABASE_DEFAULT=e.ObjectName AND o.type COLLATE DATABASE_DEFAULT=e.ObjectType COLLATE DATABASE_DEFAULT WHERE o.object_id IS NULL)
  THROW 57205,'A required release-catalog key or CHECK constraint is missing.',1;
 IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id IN(OBJECT_ID(N'dbo.ApplicationReleases'),OBJECT_ID(N'dbo.ApplicationReleaseItems')) AND (is_disabled=1 OR is_not_trusted=1))
  THROW 57206,'Release-catalog CHECK constraints must be enabled and trusted.',1;

 DECLARE @RequiredIndexes table(TableName sysname,IndexName sysname,IsUnique bit,KeyColumns nvarchar(400));
 INSERT @RequiredIndexes VALUES
 (N'ApplicationReleases',N'UX_ApplicationReleases_Channel_Version',1,N'ReleaseChannel,MajorVersion,MinorVersion,BuildVersion'),
 (N'ApplicationReleases',N'IX_ApplicationReleases_Channel_Publication_Version',0,N'ReleaseChannel,PublicationStatus,MajorVersion,MinorVersion,BuildVersion'),
 (N'ApplicationReleaseItems',N'UX_ApplicationReleaseItems_Release_SortOrder',1,N'ApplicationReleaseId,SortOrder');
 IF EXISTS(SELECT 1 FROM @RequiredIndexes e LEFT JOIN sys.tables t ON t.name COLLATE DATABASE_DEFAULT=e.TableName AND SCHEMA_NAME(t.schema_id)=N'dbo'
  LEFT JOIN sys.indexes i ON i.object_id=t.object_id AND i.name COLLATE DATABASE_DEFAULT=e.IndexName
  OUTER APPLY(SELECT STRING_AGG(CONVERT(nvarchar(max),c.name),N',') WITHIN GROUP(ORDER BY ic.key_ordinal) KeyColumns FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)x
  WHERE i.index_id IS NULL OR i.is_unique<>e.IsUnique OR i.is_disabled=1 OR i.is_hypothetical=1 OR ISNULL(x.KeyColumns,N'') COLLATE DATABASE_DEFAULT<>e.KeyColumns COLLATE DATABASE_DEFAULT)
  THROW 57207,'A required release-catalog index is missing or incompatible.',1;

 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0
  ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO
/* No rollback script is supplied. Rollback means leaving this unused additive schema in place. */

