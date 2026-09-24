/* Forward-only Phase 2D: move current Case Date confirmation policy ownership from New Intake fields to tenant-effective Case Date Types. */
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF OBJECT_ID(N'dbo.FieldConfirmationPolicies',N'U') IS NULL THROW 57100,'Phase 2A field confirmation schema is missing.',1;

 /* The new column must exist before SQL Server compiles any statement that references it. */
 IF COL_LENGTH(N'dbo.FieldConfirmationPolicies',N'CaseDateTypePolicyKey') IS NULL
  ALTER TABLE dbo.FieldConfirmationPolicies ADD CaseDateTypePolicyKey varchar(160) NULL;

 EXEC sys.sp_executesql N'
  /* Enabled legacy policies require a human rollout decision. They are neither discarded nor reinterpreted. */
  IF EXISTS(SELECT 1 FROM dbo.FieldConfirmationPolicies WHERE SupersededAt IS NULL AND CaseDateTypePolicyKey IS NULL AND RequiresConfirmation=1)
   THROW 57101,''Enabled New Intake field confirmation policies exist. Review the read-only Phase 2D verification output and explicitly retire or migrate each policy before applying Phase 2D.'',1;

  IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N''dbo.FieldConfirmationPolicies'') AND name IN(N''FormKey'',N''FieldKey'') GROUP BY object_id HAVING MIN(CONVERT(int,is_nullable))<>MAX(CONVERT(int,is_nullable)))
   THROW 57102,''FormKey and FieldKey nullability is inconsistent; reconcile it before Phase 2D.'',1;

  IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N''dbo.FieldConfirmationPolicies'') AND name=N''FormKey'' AND is_nullable=0) BEGIN
  IF EXISTS(
   SELECT 1
   FROM sys.stats s
   JOIN sys.stats_columns sc ON sc.object_id=s.object_id AND sc.stats_id=s.stats_id
   JOIN sys.columns c ON c.object_id=sc.object_id AND c.column_id=sc.column_id
   WHERE s.object_id=OBJECT_ID(N''dbo.FieldConfirmationPolicies'')
    AND c.name IN(N''FormKey'',N''FieldKey'')
    AND s.user_created=1
    AND s.name NOT IN(N''UX_FieldConfirmationPolicies_Current'',N''UX_FieldConfirmationPolicies_Field_Revision''))
   THROW 57103,''A user-created statistic on FormKey or FieldKey must be removed before Phase 2D.'',1;

  IF EXISTS(
   SELECT 1
   FROM sys.indexes i
   JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id
   JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
   WHERE i.object_id=OBJECT_ID(N''dbo.FieldConfirmationPolicies'')
    AND c.name IN(N''FormKey'',N''FieldKey'')
    AND i.name NOT IN(N''UX_FieldConfirmationPolicies_Current'',N''UX_FieldConfirmationPolicies_Field_Revision''))
   THROW 57104,''An unexpected index or key constraint depends on FormKey or FieldKey; reconcile it before Phase 2D.'',1;

  IF EXISTS(
   SELECT 1
   FROM sys.foreign_key_columns fkc
   JOIN sys.foreign_keys fk ON fk.object_id=fkc.constraint_object_id
   JOIN sys.columns c ON c.object_id=fkc.parent_object_id AND c.column_id=fkc.parent_column_id
   WHERE fkc.parent_object_id=OBJECT_ID(N''dbo.FieldConfirmationPolicies'')
    AND c.name IN(N''FormKey'',N''FieldKey'')
    AND fk.name<>N''FK_FieldConfirmationPolicies_FieldKey'')
   THROW 57105,''An unexpected foreign key depends on FormKey or FieldKey; reconcile it before Phase 2D.'',1;

  IF EXISTS(
   SELECT 1 FROM sys.sql_expression_dependencies d
   JOIN sys.columns c ON c.object_id=d.referenced_id AND c.column_id=d.referenced_minor_id
   WHERE d.referenced_id=OBJECT_ID(N''dbo.FieldConfirmationPolicies'')
    AND c.name IN(N''FormKey'',N''FieldKey'')
    AND d.referencing_id<>OBJECT_ID(N''dbo.FK_FieldConfirmationPolicies_FieldKey''))
   THROW 57106,''An unexpected schema-bound dependency references FormKey or FieldKey; reconcile it before Phase 2D.'',1;

  /* SQL Server requires dependent indexes and the composite field identity FK to be removed before nullability changes. */
  IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N''dbo.FieldConfirmationPolicies'') AND name=N''UX_FieldConfirmationPolicies_Current'')
   DROP INDEX UX_FieldConfirmationPolicies_Current ON dbo.FieldConfirmationPolicies;
  IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N''dbo.FieldConfirmationPolicies'') AND name=N''UX_FieldConfirmationPolicies_Field_Revision'')
   DROP INDEX UX_FieldConfirmationPolicies_Field_Revision ON dbo.FieldConfirmationPolicies;
  IF OBJECT_ID(N''dbo.FK_FieldConfirmationPolicies_FieldKey'',N''F'') IS NOT NULL
   ALTER TABLE dbo.FieldConfirmationPolicies DROP CONSTRAINT FK_FieldConfirmationPolicies_FieldKey;

  ALTER TABLE dbo.FieldConfirmationPolicies ALTER COLUMN FormKey varchar(64) NULL;
  ALTER TABLE dbo.FieldConfirmationPolicies ALTER COLUMN FieldKey varchar(128) NULL;
  END;

  IF OBJECT_ID(N''dbo.FK_FieldConfirmationPolicies_FieldKey'',N''F'') IS NULL
   ALTER TABLE dbo.FieldConfirmationPolicies WITH CHECK ADD CONSTRAINT FK_FieldConfirmationPolicies_FieldKey
    FOREIGN KEY(ShaleClientId,FormKey,FieldKey) REFERENCES dbo.FormFieldPolicyKeys(ShaleClientId,FormKey,FieldKey);

  IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N''dbo.FieldConfirmationPolicies'') AND name=N''UX_FieldConfirmationPolicies_Current'')
   CREATE UNIQUE INDEX UX_FieldConfirmationPolicies_Current ON dbo.FieldConfirmationPolicies(ShaleClientId,FormKey,FieldKey)
    WHERE SupersededAt IS NULL AND CaseDateTypePolicyKey IS NULL;
  IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N''dbo.FieldConfirmationPolicies'') AND name=N''UX_FieldConfirmationPolicies_CurrentCaseDateType'')
   CREATE UNIQUE INDEX UX_FieldConfirmationPolicies_CurrentCaseDateType ON dbo.FieldConfirmationPolicies(ShaleClientId,CaseDateTypePolicyKey)
    WHERE SupersededAt IS NULL AND CaseDateTypePolicyKey IS NOT NULL;
  IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N''dbo.FieldConfirmationPolicies'') AND name=N''UX_FieldConfirmationPolicies_Field_Revision'')
   CREATE UNIQUE INDEX UX_FieldConfirmationPolicies_Field_Revision ON dbo.FieldConfirmationPolicies(ShaleClientId,FormKey,FieldKey,PolicyRevision)
    WHERE CaseDateTypePolicyKey IS NULL;
  IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N''dbo.FieldConfirmationPolicies'') AND name=N''UX_FieldConfirmationPolicies_CaseDateType_Revision'')
   CREATE UNIQUE INDEX UX_FieldConfirmationPolicies_CaseDateType_Revision ON dbo.FieldConfirmationPolicies(ShaleClientId,CaseDateTypePolicyKey,PolicyRevision)
    WHERE CaseDateTypePolicyKey IS NOT NULL;
  IF OBJECT_ID(N''dbo.CK_FieldConfirmationPolicies_Identity'',N''C'') IS NULL
   ALTER TABLE dbo.FieldConfirmationPolicies WITH CHECK ADD CONSTRAINT CK_FieldConfirmationPolicies_Identity
    CHECK ((CaseDateTypePolicyKey IS NULL AND FormKey IS NOT NULL AND FieldKey IS NOT NULL) OR
           (CaseDateTypePolicyKey IS NOT NULL AND FormKey IS NULL AND FieldKey IS NULL));
 ';
 COMMIT;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
