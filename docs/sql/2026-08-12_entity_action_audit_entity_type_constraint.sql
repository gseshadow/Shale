/*
 * Forward-only deployment migration for the durable entity-action audit EntityType allowlist.
 * Schema-only: no audit row is updated, deleted, inserted, or backfilled.
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
        THROW 54821, 'dbo.EntityActionAuditLog.EntityType is missing or incompatible.', 1;

    DECLARE @Checks table
    (
        ObjectId int NOT NULL PRIMARY KEY,
        ConstraintName sysname NOT NULL,
        ConstraintDefinition nvarchar(max) NOT NULL,
        IsDisabled bit NOT NULL,
        IsNotTrusted bit NOT NULL,
        Normalized nvarchar(max) NULL,
        IsPositiveAllowlist bit NOT NULL DEFAULT (0)
    );

    /* Inventory all EntityType checks by authoritative table/column dependency, not by name. */
    INSERT @Checks (ObjectId, ConstraintName, ConstraintDefinition, IsDisabled, IsNotTrusted)
    SELECT DISTINCT cc.object_id, cc.name, cc.definition, cc.is_disabled, cc.is_not_trusted
    FROM sys.check_constraints AS cc
    JOIN sys.sql_expression_dependencies AS dep
      ON dep.referencing_id = cc.object_id
     AND dep.referenced_id = @AuditObjectId
     AND dep.referenced_minor_id = @EntityTypeColumnId
    WHERE cc.parent_object_id = @AuditObjectId;
    IF NOT EXISTS (SELECT 1 FROM @Checks)
        THROW 54822, 'No CHECK constraint references dbo.EntityActionAuditLog.EntityType.', 1;

    /* Mixed-column checks are unrelated invariants and are never replacement candidates. */
    UPDATE c
    SET Normalized = UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        c.ConstraintDefinition, N' ', N''), NCHAR(9), N''), NCHAR(10), N''), NCHAR(13), N''), N'[', N''), N']', N''))
    FROM @Checks AS c
    WHERE NOT EXISTS
    (
        SELECT 1
        FROM sys.sql_expression_dependencies AS dep
        WHERE dep.referencing_id = c.ObjectId
          AND dep.referenced_id = @AuditObjectId
          AND dep.referenced_minor_id > 0
          AND dep.referenced_minor_id <> @EntityTypeColumnId
    );

    DECLARE @Extracted table
    (
        ConstraintObjectId int NOT NULL,
        Value varchar(64) NOT NULL,
        PRIMARY KEY (ConstraintObjectId, Value)
    );

    /*
     * Recognize only these positive grammars after whitespace/bracket/parenthesis normalization:
     *   EntityType IN ('A','B')
     *   EntityType = 'A' OR EntityType = 'B'
     * Every token and delimiter is consumed. NOT, <>, functions, extra comparisons, malformed
     * literals, and mixed expressions therefore cannot contribute values.
     */
    DECLARE @CheckId int, @Expression nvarchar(max), @Work nvarchar(max), @Token nvarchar(4000);
    DECLARE @QuoteEnd int, @Valid bit;
    DECLARE check_parser CURSOR LOCAL FAST_FORWARD FOR
        SELECT ObjectId, REPLACE(REPLACE(Normalized, N'(', N''), N')', N'')
        FROM @Checks WHERE Normalized IS NOT NULL ORDER BY ConstraintName;
    OPEN check_parser;
    FETCH NEXT FROM check_parser INTO @CheckId, @Expression;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @Valid = 1;
        IF LEFT(@Expression, 12) = N'ENTITYTYPEIN'
        BEGIN
            SET @Work = SUBSTRING(@Expression, 13, LEN(@Expression));
            WHILE LEN(@Work) > 0 AND @Valid = 1
            BEGIN
                IF LEFT(@Work, 1) <> N'''' SET @Valid = 0;
                ELSE
                BEGIN
                    SET @QuoteEnd = CHARINDEX(N'''', @Work, 2);
                    IF @QuoteEnd = 0 SET @Valid = 0;
                    ELSE
                    BEGIN
                        SET @Token = SUBSTRING(@Work, 2, @QuoteEnd - 2);
                        IF LEN(@Token) = 0 OR LEN(@Token) > 64
                           OR @Token COLLATE Latin1_General_100_BIN2 LIKE N'%[^A-Z_]%'
                            SET @Valid = 0;
                        ELSE
                            INSERT @Extracted (ConstraintObjectId, Value)
                            VALUES (@CheckId, CONVERT(varchar(64), @Token));
                        SET @Work = SUBSTRING(@Work, @QuoteEnd + 1, LEN(@Work));
                        IF LEN(@Work) > 0
                        BEGIN
                            IF LEFT(@Work, 1) <> N',' SET @Valid = 0;
                            ELSE SET @Work = SUBSTRING(@Work, 2, LEN(@Work));
                        END;
                    END;
                END;
            END;
        END
        ELSE IF LEFT(@Expression, 11) = N'ENTITYTYPE='
        BEGIN
            SET @Work = @Expression;
            WHILE LEN(@Work) > 0 AND @Valid = 1
            BEGIN
                IF LEFT(@Work, 12) <> N'ENTITYTYPE=''' SET @Valid = 0;
                ELSE
                BEGIN
                    SET @Work = SUBSTRING(@Work, 13, LEN(@Work));
                    SET @QuoteEnd = CHARINDEX(N'''', @Work);
                    IF @QuoteEnd = 0 SET @Valid = 0;
                    ELSE
                    BEGIN
                        SET @Token = LEFT(@Work, @QuoteEnd - 1);
                        IF LEN(@Token) = 0 OR LEN(@Token) > 64
                           OR @Token COLLATE Latin1_General_100_BIN2 LIKE N'%[^A-Z_]%'
                            SET @Valid = 0;
                        ELSE
                            INSERT @Extracted (ConstraintObjectId, Value)
                            VALUES (@CheckId, CONVERT(varchar(64), @Token));
                        SET @Work = SUBSTRING(@Work, @QuoteEnd + 1, LEN(@Work));
                        IF LEN(@Work) > 0
                        BEGIN
                            IF LEFT(@Work, 2) <> N'OR' SET @Valid = 0;
                            ELSE SET @Work = SUBSTRING(@Work, 3, LEN(@Work));
                        END;
                    END;
                END;
            END;
        END
        ELSE SET @Valid = 0;

        IF @Valid = 1
           /* The Phase 6.1 deployed allowlist necessarily contained all three foundation values. */
           AND EXISTS (SELECT 1 FROM @Extracted WHERE ConstraintObjectId = @CheckId AND Value = 'LINK_TYPE')
           AND EXISTS (SELECT 1 FROM @Extracted WHERE ConstraintObjectId = @CheckId AND Value = 'CASE_LINK')
           AND EXISTS (SELECT 1 FROM @Extracted WHERE ConstraintObjectId = @CheckId AND Value = 'CASE_LINK_SHARE')
            UPDATE @Checks SET IsPositiveAllowlist = 1 WHERE ObjectId = @CheckId;
        ELSE
            DELETE FROM @Extracted WHERE ConstraintObjectId = @CheckId;

        FETCH NEXT FROM check_parser INTO @CheckId, @Expression;
    END;
    CLOSE check_parser;
    DEALLOCATE check_parser;

    /* Exactly one positive allowlist must be identifiable. Other EntityType checks remain untouched. */
    IF (SELECT COUNT(*) FROM @Checks WHERE IsPositiveAllowlist = 1) <> 1
        THROW 54823, 'EntityType allowlist discovery is missing or ambiguous; no constraints were changed.', 1;

    DECLARE @AllowlistObjectId int, @AllowlistName sysname;
    SELECT @AllowlistObjectId = ObjectId, @AllowlistName = ConstraintName
    FROM @Checks WHERE IsPositiveAllowlist = 1;

    DECLARE @Allowed table (Value varchar(64) NOT NULL PRIMARY KEY);
    /* Complete current vocabulary and every value known to have been allowed by deployed migrations. */
    INSERT @Allowed (Value) VALUES
        ('CASE'), ('LINK_TYPE'), ('CASE_LINK'), ('CASE_LINK_SHARE'), ('CASE_DATE'),
        ('CALENDAR_EVENT'), ('CASE_DATE_ROLE_MAPPING'), ('CALENDAR_CASE_DATE_TYPE_MAPPING'),
        ('FORM_CONFIGURATION'), ('MATERIAL_TYPE'), ('MATERIAL_REQUEST'),
        ('MATERIAL_REQUEST_FOLLOW_UP'), ('MATERIAL_ITEM'), ('USER');

    /* Preserve extras only from the single expression whose entire positive allowlist grammar was validated. */
    INSERT @Allowed (Value)
    SELECT e.Value FROM @Extracted AS e
    WHERE e.ConstraintObjectId = @AllowlistObjectId
      AND NOT EXISTS (SELECT 1 FROM @Allowed AS a WHERE a.Value = e.Value);

    /* Preflight existing data before any DDL. Data is never changed by this migration. */
    IF EXISTS
    (
        SELECT 1 FROM dbo.EntityActionAuditLog AS audit_row
        WHERE NOT EXISTS (SELECT 1 FROM @Allowed AS a WHERE a.Value = audit_row.EntityType)
    )
        THROW 54824, 'Existing EntityActionAuditLog rows contain an EntityType outside the resulting allowlist.', 1;

    DECLARE @Values nvarchar(max) =
    (
        SELECT STRING_AGG(CONVERT(nvarchar(max), QUOTENAME(Value, '''')), N',')
               WITHIN GROUP (ORDER BY Value) FROM @Allowed
    );
    DECLARE @Sql nvarchar(max) = N'ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT '
        + QUOTENAME(@AllowlistName) + N';';
    EXEC sys.sp_executesql @Sql;
    SET @Sql = N'ALTER TABLE dbo.EntityActionAuditLog WITH CHECK ADD CONSTRAINT '
        + QUOTENAME(N'CK_EntityActionAuditLog_EntityType')
        + N' CHECK (EntityType IN (' + @Values + N'));';
    EXEC sys.sp_executesql @Sql;
    ALTER TABLE dbo.EntityActionAuditLog CHECK CONSTRAINT CK_EntityActionAuditLog_EntityType;

    /* One canonical trusted allowlist must exist; every unrelated pre-existing check must be unchanged. */
    IF (SELECT COUNT(*) FROM sys.check_constraints
        WHERE parent_object_id = @AuditObjectId
          AND name = N'CK_EntityActionAuditLog_EntityType'
          AND is_disabled = 0 AND is_not_trusted = 0) <> 1
        THROW 54825, 'The canonical EntityType allowlist is not uniquely enabled and trusted.', 1;
    IF EXISTS
    (
        SELECT 1 FROM @Checks AS before_check
        WHERE before_check.ObjectId <> @AllowlistObjectId
          AND NOT EXISTS
          (
              SELECT 1 FROM sys.check_constraints AS after_check
              WHERE after_check.object_id = before_check.ObjectId
                AND after_check.parent_object_id = @AuditObjectId
                AND after_check.name = before_check.ConstraintName
                AND after_check.definition = before_check.ConstraintDefinition
                AND after_check.is_disabled = before_check.IsDisabled
                AND after_check.is_not_trusted = before_check.IsNotTrusted
          )
    )
        THROW 54826, 'An unrelated EntityType CHECK changed unexpectedly.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
