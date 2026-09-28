/*
 Phase 1B global application-policy foundation.
 REVIEW/APPLY MANUALLY IN SSMS OR SQLCMD. Forward-only, additive, transactional,
 and rerunnable. Creates empty storage only; it does not read or synchronize the
 static updater manifest and adds no runtime policy behavior.

 ApplicationPolicy is global product-control data. It intentionally has no
 ShaleClientId and no RLS predicate. Actor foreign keys provide provenance only;
 they do not grant tenant users or tenant administrators mutation authority.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
 BEGIN TRANSACTION;

 IF OBJECT_ID(N'dbo.Users',N'U') IS NULL
  THROW 57300,'Required Users table is missing.',1;
 IF OBJECT_ID(N'dbo.ApplicationReleases',N'U') IS NULL
  THROW 57301,'Required Phase 1A ApplicationReleases table is missing.',1;

 IF OBJECT_ID(N'dbo.ApplicationPolicy',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.ApplicationPolicy(
   Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_ApplicationPolicy PRIMARY KEY,
   ReleaseChannel varchar(32) NOT NULL,
   RevisionNumber bigint NOT NULL,
   LatestReleaseId bigint NULL,
   MinimumRecommendedReleaseId bigint NULL,
   MinimumAllowedReleaseId bigint NULL,
   RequiredUpdateDeadline datetime2(7) NULL,
   AccessMode varchar(32) NOT NULL CONSTRAINT DF_ApplicationPolicy_AccessMode DEFAULT('NORMAL'),
   IsCurrent bit NOT NULL,
   PublishedAt datetime2(7) NOT NULL,
   PublishedByUserId int NULL,
   SupersededAt datetime2(7) NULL,
   SupersededByUserId int NULL,
   CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_ApplicationPolicy_CreatedAt DEFAULT(SYSUTCDATETIME()),
   CreatedByUserId int NULL,
   RowVer rowversion NOT NULL,
   CONSTRAINT FK_ApplicationPolicy_LatestRelease FOREIGN KEY(LatestReleaseId) REFERENCES dbo.ApplicationReleases(Id) ON DELETE NO ACTION,
   CONSTRAINT FK_ApplicationPolicy_MinimumRecommendedRelease FOREIGN KEY(MinimumRecommendedReleaseId) REFERENCES dbo.ApplicationReleases(Id) ON DELETE NO ACTION,
   CONSTRAINT FK_ApplicationPolicy_MinimumAllowedRelease FOREIGN KEY(MinimumAllowedReleaseId) REFERENCES dbo.ApplicationReleases(Id) ON DELETE NO ACTION,
   CONSTRAINT FK_ApplicationPolicy_PublishedByUser FOREIGN KEY(PublishedByUserId) REFERENCES dbo.Users(id),
   CONSTRAINT FK_ApplicationPolicy_SupersededByUser FOREIGN KEY(SupersededByUserId) REFERENCES dbo.Users(id),
   CONSTRAINT FK_ApplicationPolicy_CreatedByUser FOREIGN KEY(CreatedByUserId) REFERENCES dbo.Users(id),
   CONSTRAINT CK_ApplicationPolicy_Channel CHECK(ReleaseChannel IN('PRODUCTION','PILOT','DEVELOPMENT')),
   CONSTRAINT CK_ApplicationPolicy_AccessMode CHECK(AccessMode IN('NORMAL','READ_ONLY','MAINTENANCE','BLOCKED')),
   CONSTRAINT CK_ApplicationPolicy_RevisionNumber CHECK(RevisionNumber>0),
   CONSTRAINT CK_ApplicationPolicy_CurrentLifecycle CHECK((IsCurrent=1 AND SupersededAt IS NULL AND SupersededByUserId IS NULL) OR (IsCurrent=0 AND SupersededAt IS NOT NULL)),
   CONSTRAINT CK_ApplicationPolicy_SupersessionTime CHECK(SupersededAt IS NULL OR SupersededAt>=PublishedAt)
  );
 END;

 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND name=N'UX_ApplicationPolicy_Channel_Revision')
  CREATE UNIQUE INDEX UX_ApplicationPolicy_Channel_Revision
   ON dbo.ApplicationPolicy(ReleaseChannel,RevisionNumber);
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND name=N'UX_ApplicationPolicy_Channel_Current')
  CREATE UNIQUE INDEX UX_ApplicationPolicy_Channel_Current
   ON dbo.ApplicationPolicy(ReleaseChannel) WHERE IsCurrent=1;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND name=N'IX_ApplicationPolicy_LatestRelease')
  CREATE INDEX IX_ApplicationPolicy_LatestRelease ON dbo.ApplicationPolicy(LatestReleaseId) WHERE LatestReleaseId IS NOT NULL;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND name=N'IX_ApplicationPolicy_MinimumRecommendedRelease')
  CREATE INDEX IX_ApplicationPolicy_MinimumRecommendedRelease ON dbo.ApplicationPolicy(MinimumRecommendedReleaseId) WHERE MinimumRecommendedReleaseId IS NOT NULL;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND name=N'IX_ApplicationPolicy_MinimumAllowedRelease')
  CREATE INDEX IX_ApplicationPolicy_MinimumAllowedRelease ON dbo.ApplicationPolicy(MinimumAllowedReleaseId) WHERE MinimumAllowedReleaseId IS NOT NULL;

 /* Closed rerun contract: never accept a familiar name with an incompatible shape. */
 DECLARE @ExpectedColumns table(ColumnName sysname,TypeName sysname,MaxLength smallint,IsNullable bit,IsIdentity bit);
 INSERT @ExpectedColumns VALUES
 (N'Id',N'bigint',8,0,1),(N'ReleaseChannel',N'varchar',32,0,0),(N'RevisionNumber',N'bigint',8,0,0),
 (N'LatestReleaseId',N'bigint',8,1,0),(N'MinimumRecommendedReleaseId',N'bigint',8,1,0),(N'MinimumAllowedReleaseId',N'bigint',8,1,0),
 (N'RequiredUpdateDeadline',N'datetime2',8,1,0),(N'AccessMode',N'varchar',32,0,0),(N'IsCurrent',N'bit',1,0,0),
 (N'PublishedAt',N'datetime2',8,0,0),(N'PublishedByUserId',N'int',4,1,0),(N'SupersededAt',N'datetime2',8,1,0),(N'SupersededByUserId',N'int',4,1,0),
 (N'CreatedAt',N'datetime2',8,0,0),(N'CreatedByUserId',N'int',4,1,0),(N'RowVer',N'timestamp',8,0,0);
 IF EXISTS(SELECT 1 FROM @ExpectedColumns e LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND c.name COLLATE DATABASE_DEFAULT=e.ColumnName
  LEFT JOIN sys.types ty ON ty.user_type_id=c.user_type_id WHERE c.column_id IS NULL OR ty.name COLLATE DATABASE_DEFAULT<>e.TypeName OR c.max_length<>e.MaxLength OR c.is_nullable<>e.IsNullable OR c.is_identity<>e.IsIdentity)
  THROW 57302,'A required application-policy column is missing or incompatible.',1;
 IF (SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.ApplicationPolicy'))<>(SELECT COUNT_BIG(*) FROM @ExpectedColumns)
  THROW 57303,'ApplicationPolicy contains unexpected columns.',1;
 IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND name=N'ShaleClientId')
  THROW 57304,'Global ApplicationPolicy must not contain ShaleClientId.',1;
 IF EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.ApplicationPolicy'))
  THROW 57305,'Global ApplicationPolicy must not have an RLS security predicate.',1;

 DECLARE @RequiredObjects table(ObjectName sysname,ObjectType char(2));
 INSERT @RequiredObjects VALUES
 (N'PK_ApplicationPolicy','PK'),(N'DF_ApplicationPolicy_AccessMode','D'),(N'DF_ApplicationPolicy_CreatedAt','D'),
 (N'FK_ApplicationPolicy_LatestRelease','F'),(N'FK_ApplicationPolicy_MinimumRecommendedRelease','F'),(N'FK_ApplicationPolicy_MinimumAllowedRelease','F'),
 (N'FK_ApplicationPolicy_PublishedByUser','F'),(N'FK_ApplicationPolicy_SupersededByUser','F'),(N'FK_ApplicationPolicy_CreatedByUser','F'),
 (N'CK_ApplicationPolicy_Channel','C'),(N'CK_ApplicationPolicy_AccessMode','C'),(N'CK_ApplicationPolicy_RevisionNumber','C'),(N'CK_ApplicationPolicy_CurrentLifecycle','C'),(N'CK_ApplicationPolicy_SupersessionTime','C');
 IF EXISTS(SELECT 1 FROM @RequiredObjects e LEFT JOIN sys.objects o ON o.parent_object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND o.name COLLATE DATABASE_DEFAULT=e.ObjectName AND o.type COLLATE DATABASE_DEFAULT=e.ObjectType WHERE o.object_id IS NULL)
  THROW 57306,'A required application-policy key or CHECK constraint is missing.',1;
 IF EXISTS(SELECT 1 FROM sys.objects o WHERE o.parent_object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND o.type IN('PK','D','F','C') AND NOT EXISTS(SELECT 1 FROM @RequiredObjects e WHERE e.ObjectName COLLATE DATABASE_DEFAULT=o.name AND e.ObjectType COLLATE DATABASE_DEFAULT=o.type))
  THROW 57310,'ApplicationPolicy contains an unexpected key or constraint.',1;
 IF EXISTS(SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND (is_disabled=1 OR is_not_trusted=1 OR delete_referential_action<>0))
  THROW 57307,'Application-policy foreign keys must be trusted and non-cascading.',1;
 IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND (is_disabled=1 OR is_not_trusted=1))
  THROW 57308,'Application-policy CHECK constraints must be enabled and trusted.',1;

 DECLARE @RequiredIndexes table(IndexName sysname,IsUnique bit,HasFilter bit,FilterDefinition nvarchar(100),KeyColumns nvarchar(400));
 INSERT @RequiredIndexes VALUES
 (N'UX_ApplicationPolicy_Channel_Revision',1,0,NULL,N'ReleaseChannel,RevisionNumber'),
 (N'UX_ApplicationPolicy_Channel_Current',1,1,N'([IsCurrent]=(1))',N'ReleaseChannel'),
 (N'IX_ApplicationPolicy_LatestRelease',0,1,N'([LatestReleaseId] IS NOT NULL)',N'LatestReleaseId'),
 (N'IX_ApplicationPolicy_MinimumRecommendedRelease',0,1,N'([MinimumRecommendedReleaseId] IS NOT NULL)',N'MinimumRecommendedReleaseId'),
 (N'IX_ApplicationPolicy_MinimumAllowedRelease',0,1,N'([MinimumAllowedReleaseId] IS NOT NULL)',N'MinimumAllowedReleaseId');
 IF EXISTS(SELECT 1 FROM @RequiredIndexes e LEFT JOIN sys.indexes i ON i.object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND i.name COLLATE DATABASE_DEFAULT=e.IndexName
  OUTER APPLY(SELECT STRING_AGG(CONVERT(nvarchar(max),c.name),N',') WITHIN GROUP(ORDER BY ic.key_ordinal) KeyColumns FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)x
  WHERE i.index_id IS NULL OR i.is_unique<>e.IsUnique OR i.has_filter<>e.HasFilter OR ISNULL(i.filter_definition,N'') COLLATE DATABASE_DEFAULT<>ISNULL(e.FilterDefinition,N'') COLLATE DATABASE_DEFAULT OR ISNULL(x.KeyColumns,N'') COLLATE DATABASE_DEFAULT<>e.KeyColumns COLLATE DATABASE_DEFAULT OR i.is_disabled=1 OR i.is_hypothetical=1)
  THROW 57309,'A required application-policy index is missing or incompatible.',1;
 IF EXISTS(SELECT 1 FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND i.index_id>1 AND NOT EXISTS(SELECT 1 FROM @RequiredIndexes e WHERE e.IndexName COLLATE DATABASE_DEFAULT=i.name))
  THROW 57311,'ApplicationPolicy contains an unexpected non-primary index.',1;

 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO
/* No rollback script is supplied. Rollback leaves this unused additive schema in place. */
