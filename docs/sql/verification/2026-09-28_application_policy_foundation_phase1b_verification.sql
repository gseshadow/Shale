
    
  
/*
 Read-only Phase 1B application-policy verification. Run after the migration and
 again after rerunning it. Every FindingCount must be zero. This Phase 1B gate
 expects no policy rows because initial publication belongs to a later phase.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @Findings table(CheckName nvarchar(180) NOT NULL,FindingCount bigint NOT NULL);
INSERT @Findings VALUES
 (N'missing ApplicationPolicy table',CASE WHEN OBJECT_ID(N'dbo.ApplicationPolicy',N'U') IS NULL THEN 1 ELSE 0 END),
 (N'Phase 1B policy rows unexpectedly seeded',CASE WHEN EXISTS(SELECT 1 FROM dbo.ApplicationPolicy) THEN 1 ELSE 0 END);

DECLARE @ExpectedColumns table(ColumnName sysname,TypeName sysname,MaxLength smallint,IsNullable bit,IsIdentity bit);
INSERT @ExpectedColumns VALUES
(N'Id',N'bigint',8,0,1),(N'ReleaseChannel',N'varchar',32,0,0),(N'RevisionNumber',N'bigint',8,0,0),
(N'LatestReleaseId',N'bigint',8,1,0),(N'MinimumRecommendedReleaseId',N'bigint',8,1,0),(N'MinimumAllowedReleaseId',N'bigint',8,1,0),
(N'RequiredUpdateDeadline',N'datetime2',8,1,0),(N'AccessMode',N'varchar',32,0,0),(N'IsCurrent',N'bit',1,0,0),
(N'PublishedAt',N'datetime2',8,0,0),(N'PublishedByUserId',N'int',4,1,0),(N'SupersededAt',N'datetime2',8,1,0),(N'SupersededByUserId',N'int',4,1,0),
(N'CreatedAt',N'datetime2',8,0,0),(N'CreatedByUserId',N'int',4,1,0),(N'RowVer',N'timestamp',8,0,0);
INSERT @Findings SELECT N'missing or incompatible policy columns',COUNT_BIG(*) FROM @ExpectedColumns e
LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND c.name COLLATE DATABASE_DEFAULT=e.ColumnName COLLATE DATABASE_DEFAULT
LEFT JOIN sys.types ty ON ty.user_type_id=c.user_type_id
WHERE c.column_id IS NULL OR ty.name COLLATE DATABASE_DEFAULT<>e.TypeName COLLATE DATABASE_DEFAULT OR c.max_length<>e.MaxLength OR c.is_nullable<>e.IsNullable OR c.is_identity<>e.IsIdentity;
INSERT @Findings SELECT N'unexpected policy columns',ABS((SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.ApplicationPolicy'))-(SELECT COUNT_BIG(*) FROM @ExpectedColumns));
INSERT @Findings SELECT N'unexpected ShaleClientId policy column',COUNT_BIG(*) FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND name COLLATE DATABASE_DEFAULT=N'ShaleClientId' COLLATE DATABASE_DEFAULT;
INSERT @Findings SELECT N'unexpected ApplicationPolicy RLS security predicate',COUNT_BIG(*) FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.ApplicationPolicy');

DECLARE @ExpectedObjects table(ObjectName sysname,ObjectType char(2));
INSERT @ExpectedObjects VALUES
(N'PK_ApplicationPolicy','PK'),(N'DF_ApplicationPolicy_AccessMode','D'),(N'DF_ApplicationPolicy_CreatedAt','D'),
(N'FK_ApplicationPolicy_LatestRelease','F'),(N'FK_ApplicationPolicy_MinimumRecommendedRelease','F'),(N'FK_ApplicationPolicy_MinimumAllowedRelease','F'),
(N'FK_ApplicationPolicy_PublishedByUser','F'),(N'FK_ApplicationPolicy_SupersededByUser','F'),(N'FK_ApplicationPolicy_CreatedByUser','F'),
(N'CK_ApplicationPolicy_Channel','C'),(N'CK_ApplicationPolicy_AccessMode','C'),(N'CK_ApplicationPolicy_RevisionNumber','C'),(N'CK_ApplicationPolicy_CurrentLifecycle','C'),(N'CK_ApplicationPolicy_SupersessionTime','C');
INSERT @Findings SELECT N'missing policy keys, foreign keys, or CHECK constraints',COUNT_BIG(*) FROM @ExpectedObjects e
LEFT JOIN sys.objects o ON o.parent_object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND o.name COLLATE DATABASE_DEFAULT=e.ObjectName COLLATE DATABASE_DEFAULT AND o.type COLLATE DATABASE_DEFAULT=e.ObjectType COLLATE DATABASE_DEFAULT WHERE o.object_id IS NULL;
INSERT @Findings SELECT N'unexpected policy keys or constraints',COUNT_BIG(*) FROM sys.objects o WHERE o.parent_object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND o.type IN('PK','D','F','C') AND NOT EXISTS(SELECT 1 FROM @ExpectedObjects e WHERE e.ObjectName COLLATE DATABASE_DEFAULT=o.name COLLATE DATABASE_DEFAULT AND e.ObjectType COLLATE DATABASE_DEFAULT=o.type COLLATE DATABASE_DEFAULT);
INSERT @Findings SELECT N'disabled, untrusted, or cascading policy foreign keys',COUNT_BIG(*) FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND (is_disabled=1 OR is_not_trusted=1 OR delete_referential_action<>0);
INSERT @Findings SELECT N'disabled or untrusted policy CHECK constraints',COUNT_BIG(*) FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND (is_disabled=1 OR is_not_trusted=1);

DECLARE @ExpectedIndexes table(IndexName sysname,IsUnique bit,HasFilter bit,KeyColumns nvarchar(400));
INSERT @ExpectedIndexes VALUES
(N'UX_ApplicationPolicy_Channel_Revision',1,0,N'ReleaseChannel,RevisionNumber'),(N'UX_ApplicationPolicy_Channel_Current',1,1,N'ReleaseChannel'),
(N'IX_ApplicationPolicy_LatestRelease',0,1,N'LatestReleaseId'),(N'IX_ApplicationPolicy_MinimumRecommendedRelease',0,1,N'MinimumRecommendedReleaseId'),(N'IX_ApplicationPolicy_MinimumAllowedRelease',0,1,N'MinimumAllowedReleaseId');
INSERT @Findings SELECT N'missing, duplicated, or incompatible policy indexes',COUNT_BIG(*) FROM @ExpectedIndexes e OUTER APPLY(
 SELECT COUNT_BIG(*) Matches FROM sys.indexes i OUTER APPLY(SELECT STRING_AGG(CONVERT(nvarchar(max),c.name),N',') WITHIN GROUP(ORDER BY ic.key_ordinal) KeyColumns FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)x
 WHERE i.object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND i.name COLLATE DATABASE_DEFAULT=e.IndexName COLLATE DATABASE_DEFAULT AND i.is_unique=e.IsUnique AND i.has_filter=e.HasFilter AND i.is_disabled=0 AND i.is_hypothetical=0 AND x.KeyColumns COLLATE DATABASE_DEFAULT=e.KeyColumns COLLATE DATABASE_DEFAULT
)a WHERE a.Matches<>1;
INSERT @Findings SELECT N'unexpected policy non-primary indexes',COUNT_BIG(*) FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND i.index_id>1 AND NOT EXISTS(SELECT 1 FROM @ExpectedIndexes e WHERE e.IndexName COLLATE DATABASE_DEFAULT=i.name COLLATE DATABASE_DEFAULT);

/* Definitions verify constrained vocabularies and lifecycle test vectors without writes. */
INSERT @Findings SELECT N'missing policy release-channel vocabulary',CASE WHEN EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND name COLLATE DATABASE_DEFAULT=N'CK_ApplicationPolicy_Channel' COLLATE DATABASE_DEFAULT AND definition LIKE N'%PRODUCTION%' AND definition LIKE N'%PILOT%' AND definition LIKE N'%DEVELOPMENT%') THEN 0 ELSE 1 END;
INSERT @Findings SELECT N'missing reserved policy access-mode vocabulary',CASE WHEN EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND name COLLATE DATABASE_DEFAULT=N'CK_ApplicationPolicy_AccessMode' COLLATE DATABASE_DEFAULT AND definition LIKE N'%NORMAL%' AND definition LIKE N'%READ_ONLY%' AND definition LIKE N'%MAINTENANCE%' AND definition LIKE N'%BLOCKED%') THEN 0 ELSE 1 END;
INSERT @Findings SELECT N'missing positive revision rejection rule',CASE WHEN EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND name COLLATE DATABASE_DEFAULT=N'CK_ApplicationPolicy_RevisionNumber' COLLATE DATABASE_DEFAULT AND definition LIKE N'%RevisionNumber%>(0)%') THEN 0 ELSE 1 END;
INSERT @Findings SELECT N'missing current and superseded lifecycle rejection rules',CASE WHEN EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND name COLLATE DATABASE_DEFAULT=N'CK_ApplicationPolicy_CurrentLifecycle' COLLATE DATABASE_DEFAULT AND definition LIKE N'%IsCurrent%=(1)%' AND definition LIKE N'%SupersededAt%IS NULL%' AND definition LIKE N'%IsCurrent%=(0)%' AND definition LIKE N'%SupersededAt%IS NOT NULL%') THEN 0 ELSE 1 END;
INSERT @Findings SELECT N'missing one-current-policy-per-channel filter',CASE WHEN EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ApplicationPolicy') AND name COLLATE DATABASE_DEFAULT=N'UX_ApplicationPolicy_Channel_Current' COLLATE DATABASE_DEFAULT AND is_unique=1 AND filter_definition=N'([IsCurrent]=(1))') THEN 0 ELSE 1 END;

/* Phase 1A regression contract: its exact tables, columns, named constraints and indexes remain present. */
INSERT @Findings SELECT N'Phase 1A release tables missing',CASE WHEN OBJECT_ID(N'dbo.ApplicationReleases',N'U') IS NULL OR OBJECT_ID(N'dbo.ApplicationReleaseItems',N'U') IS NULL THEN 1 ELSE 0 END;
INSERT @Findings SELECT N'Phase 1A release columns changed',COUNT_BIG(*) FROM (VALUES
 (N'ApplicationReleases',N'Id'),(N'ApplicationReleases',N'MajorVersion'),(N'ApplicationReleases',N'MinorVersion'),(N'ApplicationReleases',N'BuildVersion'),(N'ApplicationReleases',N'ApplicationVersion'),(N'ApplicationReleases',N'ReleaseChannel'),(N'ApplicationReleases',N'PublicationStatus'),(N'ApplicationReleases',N'RowVer'),
 (N'ApplicationReleaseItems',N'Id'),(N'ApplicationReleaseItems',N'ApplicationReleaseId'),(N'ApplicationReleaseItems',N'SortOrder'),(N'ApplicationReleaseItems',N'ItemType'),(N'ApplicationReleaseItems',N'RowVer'))e(TableName,ColumnName)
LEFT JOIN sys.tables t ON t.name COLLATE DATABASE_DEFAULT=e.TableName COLLATE DATABASE_DEFAULT AND SCHEMA_NAME(t.schema_id) COLLATE DATABASE_DEFAULT=N'dbo' COLLATE DATABASE_DEFAULT
LEFT JOIN sys.columns c ON c.object_id=t.object_id AND c.name COLLATE DATABASE_DEFAULT=e.ColumnName COLLATE DATABASE_DEFAULT
WHERE c.column_id IS NULL;
INSERT @Findings SELECT N'Phase 1A named objects changed',COUNT_BIG(*) FROM (VALUES
 (N'ApplicationReleases',N'PK_ApplicationReleases'),(N'ApplicationReleases',N'CK_ApplicationReleases_Channel'),(N'ApplicationReleases',N'CK_ApplicationReleases_VersionComponents'),
 (N'ApplicationReleaseItems',N'PK_ApplicationReleaseItems'),(N'ApplicationReleaseItems',N'FK_ApplicationReleaseItems_Release'))e(TableName,ObjectName)
LEFT JOIN sys.tables t ON t.name COLLATE DATABASE_DEFAULT=e.TableName COLLATE DATABASE_DEFAULT AND SCHEMA_NAME(t.schema_id) COLLATE DATABASE_DEFAULT=N'dbo' COLLATE DATABASE_DEFAULT
LEFT JOIN sys.objects o ON o.parent_object_id=t.object_id AND o.name COLLATE DATABASE_DEFAULT=e.ObjectName COLLATE DATABASE_DEFAULT
WHERE o.object_id IS NULL;

INSERT @Findings SELECT N'Phase 1A named indexes changed',COUNT_BIG(*) FROM (VALUES
 (N'ApplicationReleases',N'UX_ApplicationReleases_Channel_Version'),
 (N'ApplicationReleaseItems',N'UX_ApplicationReleaseItems_Release_SortOrder'))e(TableName,IndexName)
LEFT JOIN sys.tables t ON t.name COLLATE DATABASE_DEFAULT=e.TableName COLLATE DATABASE_DEFAULT AND SCHEMA_NAME(t.schema_id) COLLATE DATABASE_DEFAULT=N'dbo' COLLATE DATABASE_DEFAULT
LEFT JOIN sys.indexes i ON i.object_id=t.object_id AND i.name COLLATE DATABASE_DEFAULT=e.IndexName COLLATE DATABASE_DEFAULT
WHERE i.index_id IS NULL;
INSERT @Findings SELECT N'Phase 1A unexpected RLS predicates',COUNT_BIG(*) FROM sys.security_predicates WHERE target_object_id IN(OBJECT_ID(N'dbo.ApplicationReleases'),OBJECT_ID(N'dbo.ApplicationReleaseItems'));

SELECT CheckName,FindingCount FROM @Findings ORDER BY CheckName;
IF EXISTS(SELECT 1 FROM @Findings WHERE FindingCount<>0)
 THROW 57350,'Application policy verification found one or more failures.',1;

