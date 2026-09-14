/* Contacts Phase 2C-B read-only effective EntityType allowlist verification.
   One guarded batch; it changes no schema, rows, permissions, principals, or SESSION_CONTEXT. */
SET XACT_ABORT ON;

BEGIN TRY
    /* One guarded executable batch: a failed preflight cannot continue into DDL. */
    DECLARE @OperatorVerifiedAllTenantVisibility bit = 0;
    IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL
        THROW 56200, 'Contacts Phase 2C-B requires SESSION_CONTEXT ShaleClientId to be NULL.', 1;
    IF USER_NAME() IN (N'shale_app', N'shale_runtime')
       OR (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'), 0) <> 1 AND ISNULL(IS_MEMBER(N'db_owner'), 0) <> 1)
        THROW 56201, 'Use the approved all-tenant administrative database principal.', 1;
    IF @OperatorVerifiedAllTenantVisibility <> 1
        THROW 56202, 'Operator-verified all-tenant visibility is required.', 1;

    BEGIN TRANSACTION;

    DECLARE @AuditObjectId int = OBJECT_ID(N'dbo.EntityActionAuditLog', N'U');
    IF @AuditObjectId IS NULL
        THROW 56203, 'Required table dbo.EntityActionAuditLog is missing.', 1;

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
        THROW 56204, 'dbo.EntityActionAuditLog.EntityType is missing or incompatible.', 1;
    IF COL_LENGTH(N'dbo.EntityActionAuditLog', N'ShaleClientId') IS NULL
       OR COL_LENGTH(N'dbo.EntityActionAuditLog', N'ActorUserId') IS NULL
       OR COL_LENGTH(N'dbo.EntityActionAuditLog', N'EntityId') IS NULL
       OR COL_LENGTH(N'dbo.EntityActionAuditLog', N'Action') IS NULL
        THROW 56211, 'dbo.EntityActionAuditLog required audit columns are missing.', 1;

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
        THROW 56205, 'No CHECK constraint references dbo.EntityActionAuditLog.EntityType.', 1;

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
        THROW 56206, 'EntityType allowlist discovery is missing or ambiguous; no constraints were changed.', 1;

    DECLARE @AllowlistObjectId int, @AllowlistName sysname;
    SELECT @AllowlistObjectId = ObjectId, @AllowlistName = ConstraintName
    FROM @Checks WHERE IsPositiveAllowlist = 1;

    DECLARE @Expected table (Value varchar(64) NOT NULL PRIMARY KEY);
    INSERT @Expected (Value) VALUES
        ('CASE'), ('CASE_STATUS'), ('LINK_TYPE'), ('CASE_LINK'), ('CASE_LINK_SHARE'), ('CASE_DATE'), ('CASE_DATE_TYPE'),
        ('CALENDAR_EVENT'), ('CASE_DATE_ROLE_MAPPING'), ('CALENDAR_CASE_DATE_TYPE_MAPPING'),
        ('FORM_CONFIGURATION'), ('MATERIAL_TYPE'), ('MATERIAL_REQUEST'),
        ('MATERIAL_REQUEST_FOLLOW_UP'), ('MATERIAL_ITEM'), ('USER'),
        ('CONTACT_TYPE'), ('SPECIALTY'), ('CREDENTIAL_DEFINITION'),
        ('CONTACT_CONTACT_TYPE'), ('CONTACT_SPECIALTY'), ('CONTACT_CREDENTIAL'), ('CONTACT'),
        ('CONTACT_PHONE_NUMBER'), ('CONTACT_EMAIL_ADDRESS'), ('CONTACT_ADDRESS');

    SELECT N'enabled trusted positive EntityType allowlists (expect 1)' CheckName, COUNT_BIG(*) FindingCount
    FROM @Checks WHERE IsPositiveAllowlist=1 AND IsDisabled=0 AND IsNotTrusted=0;
    SELECT N'missing current or historical EntityTypes (expect 0)' CheckName, COUNT_BIG(*) FindingCount
    FROM @Expected e WHERE NOT EXISTS(SELECT 1 FROM @Extracted x WHERE x.ConstraintObjectId=@AllowlistObjectId AND x.Value=e.Value);
    SELECT N'unexpected EntityTypes accepted (expect 0)' CheckName, COUNT_BIG(*) FindingCount
    FROM @Extracted x WHERE x.ConstraintObjectId=@AllowlistObjectId AND NOT EXISTS(SELECT 1 FROM @Expected e WHERE e.Value=x.Value);
    SELECT N'Phase 2C-B contact-point EntityTypes accepted (expect 3)' CheckName, COUNT_BIG(*) FindingCount
    FROM @Extracted x WHERE x.ConstraintObjectId=@AllowlistObjectId AND x.Value IN('CONTACT_PHONE_NUMBER','CONTACT_EMAIL_ADDRESS','CONTACT_ADDRESS');
    SELECT N'existing audit rows outside effective allowlist (expect 0)' CheckName, COUNT_BIG(*) FindingCount
    FROM dbo.EntityActionAuditLog a WHERE NOT EXISTS(SELECT 1 FROM @Expected e WHERE e.Value=a.EntityType);
    SELECT N'unrelated EntityType CHECK constraints retained (inventory)' CheckName, ConstraintName, ConstraintDefinition, IsDisabled, IsNotTrusted
    FROM @Checks WHERE ObjectId<>@AllowlistObjectId ORDER BY ConstraintName;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
