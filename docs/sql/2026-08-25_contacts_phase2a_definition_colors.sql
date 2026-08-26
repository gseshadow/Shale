/* Contacts Phase 2A definition colors. Forward-only, rerunnable, all-tenant guarded. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
    DECLARE @OperatorVerifiedAllTenantVisibility bit = 0;
    IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL
        THROW 56300, 'Contacts Phase 2A colors require SESSION_CONTEXT ShaleClientId to be NULL.', 1;
    IF USER_NAME() IN (N'shale_app',N'shale_runtime')
       OR (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)<>1 AND ISNULL(IS_MEMBER(N'db_owner'),0)<>1)
        THROW 56301, 'Use the approved all-tenant administrative database principal.', 1;
    IF @OperatorVerifiedAllTenantVisibility<>1
        THROW 56302, 'Operator-verified all-tenant visibility is required.', 1;
    BEGIN TRANSACTION;

    DECLARE @table sysname, @sql nvarchar(max), @objectId int, @defaultName sysname, @checkName sysname;
    DECLARE definitions CURSOR LOCAL FAST_FORWARD FOR
        SELECT TableName FROM (VALUES(N'ContactTypes'),(N'Specialties'),(N'CredentialDefinitions'))v(TableName);
    OPEN definitions; FETCH NEXT FROM definitions INTO @table;
    WHILE @@FETCH_STATUS=0
    BEGIN
        SET @objectId=OBJECT_ID(N'dbo.'+@table,N'U');
        IF @objectId IS NULL THROW 56303, 'A required Phase 1A Contact definition table is missing.', 1;
        IF COL_LENGTH(N'dbo.'+@table,N'Color') IS NULL
        BEGIN
            SET @sql=N'ALTER TABLE dbo.'+QUOTENAME(@table)+N' ADD Color nvarchar(20) NULL;'; EXEC sys.sp_executesql @sql;
        END;
        IF NOT EXISTS(SELECT 1 FROM sys.columns c JOIN sys.types t ON t.user_type_id=c.user_type_id
                      WHERE c.object_id=@objectId AND c.name=N'Color' AND t.name=N'nvarchar' AND c.max_length=40)
            THROW 56304, 'An existing Contact definition Color column is incompatible.', 1;

        /* The shared safe legacy default; never derive presentation from names or identifiers. */
        SET @sql=N'UPDATE dbo.'+QUOTENAME(@table)+N' SET Color=N''#6C757D'' WHERE Color IS NULL OR NULLIF(LTRIM(RTRIM(Color)),N'''') IS NULL;'; EXEC sys.sp_executesql @sql;
        SET @sql=N'IF EXISTS(SELECT 1 FROM dbo.'+QUOTENAME(@table)+N' WHERE LEN(Color)<>7 OR LEFT(Color,1)<>N''#'' OR SUBSTRING(Color,2,6) COLLATE Latin1_General_100_BIN2 LIKE N''%[^0-9A-F]%'' OR Color<>UPPER(Color)) THROW 56305, ''Existing Contact definition colors violate #RRGGBB.'', 1;'; EXEC sys.sp_executesql @sql;
        IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=@objectId AND name=N'Color' AND is_nullable=1)
        BEGIN SET @sql=N'ALTER TABLE dbo.'+QUOTENAME(@table)+N' ALTER COLUMN Color nvarchar(20) NOT NULL;'; EXEC sys.sp_executesql @sql; END;

        SET @defaultName=N'DF_'+@table+N'_Color';
        IF NOT EXISTS(SELECT 1 FROM sys.default_constraints dc JOIN sys.columns c ON c.object_id=dc.parent_object_id AND c.column_id=dc.parent_column_id WHERE dc.parent_object_id=@objectId AND c.name=N'Color')
        BEGIN SET @sql=N'ALTER TABLE dbo.'+QUOTENAME(@table)+N' ADD CONSTRAINT '+QUOTENAME(@defaultName)+N' DEFAULT(N''#6C757D'') FOR Color;'; EXEC sys.sp_executesql @sql; END;
        ELSE IF NOT EXISTS(SELECT 1 FROM sys.default_constraints dc JOIN sys.columns c ON c.object_id=dc.parent_object_id AND c.column_id=dc.parent_column_id WHERE dc.parent_object_id=@objectId AND c.name=N'Color' AND UPPER(REPLACE(REPLACE(dc.definition,N'(',N''),N')',N'')) IN (N'''#6C757D''',N'N''#6C757D'''))
            THROW 56306, 'An existing Contact definition Color default is incompatible.', 1;

        SET @checkName=N'CK_'+@table+N'_Color';
        IF OBJECT_ID(N'dbo.'+@checkName,N'C') IS NULL
        BEGIN SET @sql=N'ALTER TABLE dbo.'+QUOTENAME(@table)+N' WITH CHECK ADD CONSTRAINT '+QUOTENAME(@checkName)+N' CHECK(LEN(Color)=7 AND LEFT(Color,1)=N''#'' AND SUBSTRING(Color,2,6) COLLATE Latin1_General_100_BIN2 NOT LIKE N''%[^0-9A-F]%'' AND Color=UPPER(Color)); ALTER TABLE dbo.'+QUOTENAME(@table)+N' CHECK CONSTRAINT '+QUOTENAME(@checkName)+N';'; EXEC sys.sp_executesql @sql; END;
        ELSE IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE object_id=OBJECT_ID(N'dbo.'+@checkName,N'C') AND parent_object_id=@objectId AND is_disabled=0 AND is_not_trusted=0 AND definition LIKE N'%LEN%Color%7%' AND definition LIKE N'%SUBSTRING%Color%2%6%' AND definition LIKE N'%[^0-9A-F]%' AND definition LIKE N'%UPPER%Color%')
            THROW 56307, 'An existing Contact definition Color check is incompatible, disabled, or untrusted.', 1;
        FETCH NEXT FROM definitions INTO @table;
    END;
    CLOSE definitions; DEALLOCATE definitions;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF CURSOR_STATUS('local','definitions')>=0 CLOSE definitions;
    IF CURSOR_STATUS('local','definitions')>-3 DEALLOCATE definitions;
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
