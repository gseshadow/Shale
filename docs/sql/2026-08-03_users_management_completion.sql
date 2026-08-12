/* Paste-ready Azure SQL migration: USER audit support and irreversible tenant removal. */
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.Users', N'U') IS NULL THROW 54801, 'Required table dbo.Users is missing.', 1;
    IF OBJECT_ID(N'dbo.EntityActionAuditLog', N'U') IS NULL THROW 54802, 'Required table dbo.EntityActionAuditLog is missing.', 1;

    IF COL_LENGTH(N'dbo.Users', N'UpdatedAt') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.Users ADD UpdatedAt datetime2(7) NULL;';
    IF COL_LENGTH(N'dbo.Users', N'RowVer') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.Users ADD RowVer rowversion NOT NULL;';
    IF COL_LENGTH(N'dbo.Users', N'IsRemoved') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.Users ADD IsRemoved bit NOT NULL CONSTRAINT DF_Users_IsRemoved DEFAULT (0) WITH VALUES;';
    IF COL_LENGTH(N'dbo.Users', N'RemovedAt') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.Users ADD RemovedAt datetime2(7) NULL;';
    IF COL_LENGTH(N'dbo.Users', N'RemovedByUserId') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.Users ADD RemovedByUserId int NULL;';

    IF OBJECT_ID(N'dbo.CK_Users_RemovalMetadata', N'C') IS NOT NULL
        ALTER TABLE dbo.Users DROP CONSTRAINT CK_Users_RemovalMetadata;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.Users WITH CHECK ADD CONSTRAINT CK_Users_RemovalMetadata CHECK ((IsRemoved=0 AND RemovedAt IS NULL AND RemovedByUserId IS NULL) OR (IsRemoved=1 AND is_deleted=1 AND RemovedAt IS NOT NULL AND RemovedByUserId IS NOT NULL));';
    ALTER TABLE dbo.Users CHECK CONSTRAINT CK_Users_RemovalMetadata;

    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.Users') AND name=N'FK_Users_RemovedByUserId_Users')
        EXEC sys.sp_executesql N'ALTER TABLE dbo.Users WITH CHECK ADD CONSTRAINT FK_Users_RemovedByUserId_Users FOREIGN KEY(RemovedByUserId) REFERENCES dbo.Users(id);';

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Users') AND name=N'IX_Users_Tenant_Removed_Inactive_Name')
        EXEC sys.sp_executesql N'CREATE INDEX IX_Users_Tenant_Removed_Inactive_Name ON dbo.Users(ShaleClientId,IsRemoved,is_deleted,name_last,name_first,id);';

    DECLARE @OldDefinition nvarchar(max)=(SELECT definition FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.EntityActionAuditLog') AND name=N'CK_EntityActionAuditLog_EntityType');
    IF @OldDefinition IS NULL THROW 54803, 'CK_EntityActionAuditLog_EntityType is missing.', 1;

    DECLARE @Allowed table(Value nvarchar(100) NOT NULL PRIMARY KEY);
    INSERT @Allowed(Value) VALUES
      (N'CASE'),(N'LINK_TYPE'),(N'CASE_LINK'),(N'CASE_LINK_SHARE'),(N'MATERIAL_TYPE'),(N'MATERIAL_REQUEST'),(N'MATERIAL_REQUEST_FOLLOW_UP'),(N'MATERIAL_ITEM'),(N'USER'),(N'CASE_DATE'),(N'CALENDAR_EVENT'),(N'CASE_DATE_ROLE_MAPPING'),(N'CALENDAR_CASE_DATE_TYPE_MAPPING'),(N'FORM_CONFIGURATION');
    -- Preserve any older quoted values present in the deployed definition.
    DECLARE @work nvarchar(max)=@OldDefinition,@start int=CHARINDEX(N'''',@OldDefinition),@finish int;
    WHILE @start>0 BEGIN
      SET @finish=CHARINDEX(N'''',@work,@start+1); IF @finish=0 BREAK;
      DECLARE @value nvarchar(100)=SUBSTRING(@work,@start+1,@finish-@start-1);
      IF LEN(@value)>0 AND NOT EXISTS(SELECT 1 FROM @Allowed WHERE Value=@value) INSERT @Allowed(Value) VALUES(@value);
      SET @work=SUBSTRING(@work,@finish+1,LEN(@work)); SET @start=CHARINDEX(N'''',@work);
    END;
    DECLARE @Values nvarchar(max)=(SELECT STRING_AGG(QUOTENAME(Value,''''),N',') WITHIN GROUP(ORDER BY Value) FROM @Allowed);
    ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT CK_EntityActionAuditLog_EntityType;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.EntityActionAuditLog WITH CHECK ADD CONSTRAINT CK_EntityActionAuditLog_EntityType CHECK (EntityType IN ('+@Values+N'));';
    ALTER TABLE dbo.EntityActionAuditLog CHECK CONSTRAINT CK_EntityActionAuditLog_EntityType;

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.EntityActionAuditLog') AND name=N'CK_EntityActionAuditLog_EntityType' AND is_disabled=0 AND is_not_trusted=0 AND definition LIKE N'%USER%')
        THROW 54804, 'USER audit EntityType verification failed.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.Users') AND name=N'CK_Users_RemovalMetadata' AND is_disabled=0 AND is_not_trusted=0)
        THROW 54805, 'User removal constraint verification failed.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

SELECT name, definition, is_disabled, is_not_trusted
FROM sys.check_constraints
WHERE parent_object_id IN (OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.EntityActionAuditLog'))
  AND name IN (N'CK_Users_RemovalMetadata',N'CK_EntityActionAuditLog_EntityType');
