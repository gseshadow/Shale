/*
 Read-only Phase 1A release-catalog verification. Run after the migration and
 again after rerunning it. Every FindingCount must be zero. The catalog remains
 empty unless a later, separately approved publication phase writes it.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @Findings table(CheckName nvarchar(160) NOT NULL,FindingCount bigint NOT NULL);

INSERT @Findings VALUES
 (N'missing ApplicationReleases table',CASE WHEN OBJECT_ID(N'dbo.ApplicationReleases',N'U') IS NULL THEN 1 ELSE 0 END),
 (N'missing ApplicationReleaseItems table',CASE WHEN OBJECT_ID(N'dbo.ApplicationReleaseItems',N'U') IS NULL THEN 1 ELSE 0 END);

DECLARE @ExpectedColumns table(TableName sysname,ColumnName sysname,TypeName sysname,MaxLength smallint,IsNullable bit,IsIdentity bit,IsComputed bit);
INSERT @ExpectedColumns VALUES
(N'ApplicationReleases',N'Id',N'bigint',8,0,1,0),(N'ApplicationReleases',N'MajorVersion',N'int',4,0,0,0),(N'ApplicationReleases',N'MinorVersion',N'int',4,0,0,0),(N'ApplicationReleases',N'BuildVersion',N'int',4,0,0,0),(N'ApplicationReleases',N'ApplicationVersion',N'varchar',32,1,0,1),
(N'ApplicationReleases',N'ReleaseChannel',N'varchar',32,0,0,0),(N'ApplicationReleases',N'PublicationStatus',N'varchar',16,0,0,0),(N'ApplicationReleases',N'PublishedAt',N'datetime2',8,1,0,0),(N'ApplicationReleases',N'PublishedByUserId',N'int',4,1,0,0),(N'ApplicationReleases',N'Summary',N'nvarchar',1020,0,0,0),
(N'ApplicationReleases',N'CreatedAt',N'datetime2',8,0,0,0),(N'ApplicationReleases',N'CreatedByUserId',N'int',4,1,0,0),(N'ApplicationReleases',N'UpdatedAt',N'datetime2',8,1,0,0),(N'ApplicationReleases',N'UpdatedByUserId',N'int',4,1,0,0),(N'ApplicationReleases',N'RowVer',N'timestamp',8,0,0,0),
(N'ApplicationReleaseItems',N'Id',N'bigint',8,0,1,0),(N'ApplicationReleaseItems',N'ApplicationReleaseId',N'bigint',8,0,0,0),(N'ApplicationReleaseItems',N'SortOrder',N'int',4,0,0,0),(N'ApplicationReleaseItems',N'ItemType',N'varchar',32,0,0,0),(N'ApplicationReleaseItems',N'Title',N'nvarchar',400,0,0,0),(N'ApplicationReleaseItems',N'Body',N'nvarchar',4000,0,0,0),(N'ApplicationReleaseItems',N'ResourceUrl',N'nvarchar',4096,1,0,0),(N'ApplicationReleaseItems',N'IsActive',N'bit',1,0,0,0),
(N'ApplicationReleaseItems',N'CreatedAt',N'datetime2',8,0,0,0),(N'ApplicationReleaseItems',N'CreatedByUserId',N'int',4,1,0,0),(N'ApplicationReleaseItems',N'UpdatedAt',N'datetime2',8,1,0,0),(N'ApplicationReleaseItems',N'UpdatedByUserId',N'int',4,1,0,0),(N'ApplicationReleaseItems',N'RowVer',N'timestamp',8,0,0,0);
INSERT @Findings SELECT N'missing or incompatible required columns',COUNT_BIG(*) FROM @ExpectedColumns e
LEFT JOIN sys.tables t ON t.name COLLATE DATABASE_DEFAULT=e.TableName AND SCHEMA_NAME(t.schema_id)=N'dbo'
LEFT JOIN sys.columns c ON c.object_id=t.object_id AND c.name COLLATE DATABASE_DEFAULT=e.ColumnName
LEFT JOIN sys.types ty ON ty.user_type_id=c.user_type_id
WHERE c.column_id IS NULL OR ty.name COLLATE DATABASE_DEFAULT<>e.TypeName OR c.max_length<>e.MaxLength OR c.is_nullable<>e.IsNullable OR c.is_identity<>e.IsIdentity OR c.is_computed<>e.IsComputed;

INSERT @Findings SELECT N'unexpected ShaleClientId columns',COUNT_BIG(*) FROM sys.tables t JOIN sys.columns c ON c.object_id=t.object_id
WHERE t.object_id IN(OBJECT_ID(N'dbo.ApplicationReleases'),OBJECT_ID(N'dbo.ApplicationReleaseItems')) AND c.name=N'ShaleClientId';
INSERT @Findings SELECT N'unexpected RLS security predicates',COUNT_BIG(*) FROM sys.security_predicates
WHERE target_object_id IN(OBJECT_ID(N'dbo.ApplicationReleases'),OBJECT_ID(N'dbo.ApplicationReleaseItems'));

DECLARE @ExpectedObjects table(TableName sysname,ObjectName sysname,ObjectType char(2));
INSERT @ExpectedObjects VALUES
(N'ApplicationReleases',N'PK_ApplicationReleases','PK'),(N'ApplicationReleases',N'FK_ApplicationReleases_PublishedByUser','F'),(N'ApplicationReleases',N'FK_ApplicationReleases_CreatedByUser','F'),(N'ApplicationReleases',N'FK_ApplicationReleases_UpdatedByUser','F'),
(N'ApplicationReleases',N'CK_ApplicationReleases_VersionComponents','C'),(N'ApplicationReleases',N'CK_ApplicationReleases_Channel','C'),(N'ApplicationReleases',N'CK_ApplicationReleases_PublicationStatus','C'),(N'ApplicationReleases',N'CK_ApplicationReleases_PublicationLifecycle','C'),(N'ApplicationReleases',N'CK_ApplicationReleases_Summary','C'),
(N'ApplicationReleaseItems',N'PK_ApplicationReleaseItems','PK'),(N'ApplicationReleaseItems',N'FK_ApplicationReleaseItems_Release','F'),(N'ApplicationReleaseItems',N'FK_ApplicationReleaseItems_CreatedByUser','F'),(N'ApplicationReleaseItems',N'FK_ApplicationReleaseItems_UpdatedByUser','F'),(N'ApplicationReleaseItems',N'CK_ApplicationReleaseItems_SortOrder','C'),(N'ApplicationReleaseItems',N'CK_ApplicationReleaseItems_ItemType','C'),(N'ApplicationReleaseItems',N'CK_ApplicationReleaseItems_Title','C'),(N'ApplicationReleaseItems',N'CK_ApplicationReleaseItems_Body','C');
INSERT @Findings SELECT N'missing required keys, foreign keys, or CHECK constraints',COUNT_BIG(*) FROM @ExpectedObjects e
LEFT JOIN sys.tables t ON t.name COLLATE DATABASE_DEFAULT=e.TableName AND SCHEMA_NAME(t.schema_id)=N'dbo'
LEFT JOIN sys.objects o ON o.parent_object_id=t.object_id AND o.name COLLATE DATABASE_DEFAULT=e.ObjectName AND o.type=e.ObjectType WHERE o.object_id IS NULL;
INSERT @Findings SELECT N'disabled or untrusted foreign keys',COUNT_BIG(*) FROM sys.foreign_keys WHERE parent_object_id IN(OBJECT_ID(N'dbo.ApplicationReleases'),OBJECT_ID(N'dbo.ApplicationReleaseItems')) AND (is_disabled=1 OR is_not_trusted=1);
INSERT @Findings SELECT N'disabled or untrusted CHECK constraints',COUNT_BIG(*) FROM sys.check_constraints WHERE parent_object_id IN(OBJECT_ID(N'dbo.ApplicationReleases'),OBJECT_ID(N'dbo.ApplicationReleaseItems')) AND (is_disabled=1 OR is_not_trusted=1);

DECLARE @ExpectedIndexes table(TableName sysname,IndexName sysname,IsUnique bit,KeyColumns nvarchar(400));
INSERT @ExpectedIndexes VALUES
(N'ApplicationReleases',N'UX_ApplicationReleases_Channel_Version',1,N'ReleaseChannel,MajorVersion,MinorVersion,BuildVersion'),
(N'ApplicationReleases',N'IX_ApplicationReleases_Channel_Publication_Version',0,N'ReleaseChannel,PublicationStatus,MajorVersion,MinorVersion,BuildVersion'),
(N'ApplicationReleaseItems',N'UX_ApplicationReleaseItems_Release_SortOrder',1,N'ApplicationReleaseId,SortOrder');
INSERT @Findings SELECT N'missing, duplicated, or incompatible required indexes',COUNT_BIG(*) FROM @ExpectedIndexes e OUTER APPLY(
 SELECT COUNT_BIG(*) Matches FROM sys.tables t JOIN sys.indexes i ON i.object_id=t.object_id
 OUTER APPLY(SELECT STRING_AGG(CONVERT(nvarchar(max),c.name),N',') WITHIN GROUP(ORDER BY ic.key_ordinal) KeyColumns FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0)x
 WHERE t.name=e.TableName AND SCHEMA_NAME(t.schema_id)=N'dbo' AND i.name=e.IndexName AND i.is_unique=e.IsUnique AND i.is_disabled=0 AND i.is_hypothetical=0 AND x.KeyColumns=e.KeyColumns
)a WHERE a.Matches<>1;

/* Check definitions prove the constrained vocabulary and rejection rules without writes. */
INSERT @Findings SELECT N'missing numeric nonnegative version enforcement',CASE WHEN EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.ApplicationReleases') AND name=N'CK_ApplicationReleases_VersionComponents' AND definition LIKE N'%MajorVersion%>=(0)%' AND definition LIKE N'%MinorVersion%>=(0)%' AND definition LIKE N'%BuildVersion%>=(0)%') THEN 0 ELSE 1 END;
INSERT @Findings SELECT N'missing release channel vocabulary',CASE WHEN EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.ApplicationReleases') AND name=N'CK_ApplicationReleases_Channel' AND definition LIKE N'%PRODUCTION%' AND definition LIKE N'%PILOT%' AND definition LIKE N'%DEVELOPMENT%') THEN 0 ELSE 1 END;
INSERT @Findings SELECT N'missing publication lifecycle enforcement',CASE WHEN EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.ApplicationReleases') AND name=N'CK_ApplicationReleases_PublicationLifecycle' AND definition LIKE N'%DRAFT%' AND definition LIKE N'%PUBLISHED%' AND definition LIKE N'%PublishedAt%') THEN 0 ELSE 1 END;
INSERT @Findings SELECT N'missing release item type vocabulary',CASE WHEN EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.ApplicationReleaseItems') AND name=N'CK_ApplicationReleaseItems_ItemType' AND definition LIKE N'%FEATURE%' AND definition LIKE N'%FIX%' AND definition LIKE N'%IMPROVEMENT%') THEN 0 ELSE 1 END;
INSERT @Findings SELECT N'incompatible release-item parent foreign key',CASE WHEN EXISTS(SELECT 1 FROM sys.foreign_keys f JOIN sys.foreign_key_columns fc ON fc.constraint_object_id=f.object_id JOIN sys.columns child ON child.object_id=f.parent_object_id AND child.column_id=fc.parent_column_id JOIN sys.columns parent ON parent.object_id=f.referenced_object_id AND parent.column_id=fc.referenced_column_id WHERE f.name=N'FK_ApplicationReleaseItems_Release' AND f.parent_object_id=OBJECT_ID(N'dbo.ApplicationReleaseItems') AND f.referenced_object_id=OBJECT_ID(N'dbo.ApplicationReleases') AND child.name=N'ApplicationReleaseId' AND parent.name=N'Id' AND f.delete_referential_action=0) THEN 0 ELSE 1 END;

SELECT CheckName,FindingCount FROM @Findings ORDER BY CheckName;
IF EXISTS(SELECT 1 FROM @Findings WHERE FindingCount<>0)
 THROW 57250,'Application release catalog verification found one or more failures.',1;
