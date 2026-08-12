/*
 * Forward-only deployment migration for the durable entity-action audit EntityType allowlist.
 * This migration changes schema only: it neither updates nor backfills audit rows.
 */
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @AuditObjectId int = OBJECT_ID(N'dbo.EntityActionAuditLog', N'U');
    IF @AuditObjectId IS NULL
        THROW 54820, 'Required table dbo.EntityActionAuditLog is missing.', 1;

    DECLARE @EntityTypeColumnId int;
    SELECT @EntityTypeColumnId = c.column_id
    FROM sys.columns AS c
    JOIN sys.types AS t ON t.user_type_id = c.user_type_id
    WHERE c.object_id = @AuditObjectId
      AND c.name = N'EntityType'
      AND t.name = N'varchar'
      AND c.max_length = 64
      AND c.is_nullable = 0;

    IF @EntityTypeColumnId IS NULL
        THROW 54821, 'dbo.EntityActionAuditLog.EntityType is missing or has an incompatible definition.', 1;

    DECLARE @ExistingConstraints table
    (
        ObjectId int NOT NULL PRIMARY KEY,
        ConstraintName sysname NOT NULL,
        ConstraintDefinition nvarchar(max) NOT NULL
    );

    /* Discover every CHECK that authoritatively depends on this table and column, regardless of name. */
    INSERT @ExistingConstraints (ObjectId, ConstraintName, ConstraintDefinition)
    SELECT DISTINCT cc.object_id, cc.name, cc.definition
    FROM sys.check_constraints AS cc
    JOIN sys.sql_expression_dependencies AS dep
      ON dep.referencing_id = cc.object_id
     AND dep.referenced_id = @AuditObjectId
     AND dep.referenced_minor_id = @EntityTypeColumnId
    WHERE cc.parent_object_id = @AuditObjectId;

    IF NOT EXISTS (SELECT 1 FROM @ExistingConstraints)
        THROW 54822, 'No deployed CHECK constraint was found for dbo.EntityActionAuditLog.EntityType.', 1;

    /* A mixed-column expression cannot safely be replaced by an EntityType-only constraint. */
    IF EXISTS
    (
        SELECT 1
        FROM @ExistingConstraints AS ec
        JOIN sys.sql_expression_dependencies AS dep ON dep.referencing_id = ec.ObjectId
        WHERE dep.referenced_id = @AuditObjectId
          AND dep.referenced_minor_id > 0
          AND dep.referenced_minor_id <> @EntityTypeColumnId
    )
        THROW 54823, 'An EntityType CHECK also depends on another column; migration stopped without changes.', 1;

    DECLARE @Allowed table (Value varchar(64) NOT NULL PRIMARY KEY);
    /* Complete EntityActionAuditEvent.EntityType vocabulary at this migration's release. */
    INSERT @Allowed (Value) VALUES
        ('CASE'),
        ('LINK_TYPE'),
        ('CASE_LINK'),
        ('CASE_LINK_SHARE'),
        ('CASE_DATE'),
        ('CALENDAR_EVENT'),
        ('CASE_DATE_ROLE_MAPPING'),
        ('CALENDAR_CASE_DATE_TYPE_MAPPING'),
        ('FORM_CONFIGURATION'),
        ('MATERIAL_TYPE'),
        ('MATERIAL_REQUEST'),
        ('MATERIAL_REQUEST_FOLLOW_UP'),
        ('MATERIAL_ITEM'),
        ('USER');

    /* Preserve every quoted value allowed by every deployed historical EntityType CHECK. */
    DECLARE @Definition nvarchar(max), @QuoteStart int, @QuoteEnd int, @Value nvarchar(4000);
    DECLARE constraint_definitions CURSOR LOCAL FAST_FORWARD FOR
        SELECT ConstraintDefinition FROM @ExistingConstraints ORDER BY ConstraintName;
    OPEN constraint_definitions;
    FETCH NEXT FROM constraint_definitions INTO @Definition;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @QuoteStart = CHARINDEX(N'''', @Definition);
        WHILE @QuoteStart > 0
        BEGIN
            SET @QuoteEnd = CHARINDEX(N'''', @Definition, @QuoteStart + 1);
            IF @QuoteEnd = 0
                THROW 54824, 'An EntityType CHECK definition contains an unmatched quote.', 1;
            SET @Value = SUBSTRING(@Definition, @QuoteStart + 1, @QuoteEnd - @QuoteStart - 1);
            IF LEN(@Value) = 0 OR LEN(@Value) > 64 OR @Value COLLATE Latin1_General_100_BIN2 LIKE N'%[^A-Z_]%'
                THROW 54825, 'An EntityType CHECK contains an incompatible allowed value.', 1;
            IF NOT EXISTS (SELECT 1 FROM @Allowed WHERE Value = CONVERT(varchar(64), @Value))
                INSERT @Allowed (Value) VALUES (CONVERT(varchar(64), @Value));
            SET @Definition = SUBSTRING(@Definition, @QuoteEnd + 1, LEN(@Definition));
            SET @QuoteStart = CHARINDEX(N'''', @Definition);
        END;
        FETCH NEXT FROM constraint_definitions INTO @Definition;
    END;
    CLOSE constraint_definitions;
    DEALLOCATE constraint_definitions;

    DECLARE @ConstraintName sysname, @Sql nvarchar(max);
    DECLARE constraints_to_drop CURSOR LOCAL FAST_FORWARD FOR
        SELECT ConstraintName FROM @ExistingConstraints ORDER BY ConstraintName;
    OPEN constraints_to_drop;
    FETCH NEXT FROM constraints_to_drop INTO @ConstraintName;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @Sql = N'ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT ' + QUOTENAME(@ConstraintName) + N';';
        EXEC sys.sp_executesql @Sql;
        FETCH NEXT FROM constraints_to_drop INTO @ConstraintName;
    END;
    CLOSE constraints_to_drop;
    DEALLOCATE constraints_to_drop;

    DECLARE @Values nvarchar(max) =
    (
        SELECT STRING_AGG(CONVERT(nvarchar(max), QUOTENAME(Value, '''')), N',')
               WITHIN GROUP (ORDER BY Value)
        FROM @Allowed
    );
    SET @Sql = N'ALTER TABLE dbo.EntityActionAuditLog WITH CHECK ADD CONSTRAINT '
        + QUOTENAME(N'CK_EntityActionAuditLog_EntityType')
        + N' CHECK (EntityType IN (' + @Values + N'));';
    EXEC sys.sp_executesql @Sql;
    ALTER TABLE dbo.EntityActionAuditLog CHECK CONSTRAINT CK_EntityActionAuditLog_EntityType;

    IF NOT EXISTS
    (
        SELECT 1
        FROM sys.check_constraints AS cc
        JOIN sys.sql_expression_dependencies AS dep
          ON dep.referencing_id = cc.object_id
         AND dep.referenced_id = @AuditObjectId
         AND dep.referenced_minor_id = @EntityTypeColumnId
        WHERE cc.parent_object_id = @AuditObjectId
          AND cc.name = N'CK_EntityActionAuditLog_EntityType'
          AND cc.is_disabled = 0
          AND cc.is_not_trusted = 0
    )
        THROW 54826, 'The rebuilt EntityType CHECK is not enabled and trusted.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
