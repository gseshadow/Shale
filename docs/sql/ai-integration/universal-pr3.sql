/* Universal MCP PR 3 CANDIDATE. Manual disposable use only. No activation.
   Historical confinement receipts and immutable deployment binding are never refreshed.
   The production compatibility prerequisite below is deliberate; see pr3-review.md. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;
IF USER_NAME()<>N'dbo' OR (SELECT COUNT(*) FROM sys.schemas WHERE name IN(N'dbo',N'sec') AND principal_id=DATABASE_PRINCIPAL_ID(N'dbo'))<>2 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 59300,'Fresh unscoped dbo session required.',1;
IF OBJECT_ID(N'dbo.AiCaseDataDeployment') IS NOT NULL
 THROW 59300,'Confinement successor review required before installing PR 3 alongside Case confinement.',1;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @rc int;
EXEC @rc=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
IF @rc<0 THROW 59300,'Schema lock unavailable.',1;
IF OBJECT_ID(N'dbo.AiActorV1',N'P') IS NULL OR OBJECT_ID(N'dbo.AiLockV1',N'P') IS NULL
 OR OBJECT_ID(N'dbo.AiIntegrationRegistration',N'U') IS NULL
 OR NOT EXISTS(SELECT 1 FROM sys.security_policies WHERE object_id=OBJECT_ID(N'sec.TenantFilter') AND is_enabled=1 AND is_schema_bound=1)
 OR OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL
 THROW 59300,'Reviewed AI foundation and strict RLS required.',1;
IF OBJECT_ID(N'dbo.AiExternalApplication') IS NOT NULL OR OBJECT_ID(N'dbo.AiTenantApplicationApproval') IS NOT NULL
 OR DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher') IS NOT NULL OR DATABASE_PRINCIPAL_ID(N'AiApplicationTenant') IS NOT NULL
 THROW 59300,'Already installed or unreceipted PR 3 objects; verify, never overwrite.',1;
CREATE TABLE dbo.AiExternalApplication(
 Id bigint IDENTITY CONSTRAINT PK_AiExternalApplication PRIMARY KEY,
 ApplicationId uniqueidentifier NOT NULL CONSTRAINT UQ_AiExternalApplication_Id UNIQUE DEFAULT NEWID(),
 Issuer nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 Resource nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 ExternalClientId nvarchar(2048) COLLATE Latin1_General_100_BIN2 NOT NULL,
 IdentityHash binary(32) NOT NULL CONSTRAINT UQ_AiExternalApplication_Hash UNIQUE,
 Source varchar(8) NOT NULL CONSTRAINT CK_AiExternalApplication_Source CHECK(Source='CIMD'),
 MetadataDigest binary(32) NOT NULL, MethodMask tinyint NOT NULL CONSTRAINT CK_AiExternalApplication_Method CHECK(MethodMask BETWEEN 1 AND 3),
 Revision bigint NOT NULL DEFAULT 1, Disabled bit NOT NULL DEFAULT 0,
 CreatedAt datetime2(7) NOT NULL DEFAULT SYSUTCDATETIME(), UpdatedAt datetime2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
 RowVer rowversion NOT NULL,
 CONSTRAINT CK_AiExternalApplication_Revision CHECK(Revision>0)
);
CREATE TABLE dbo.AiExternalApplicationRevision(
 ApplicationId uniqueidentifier NOT NULL REFERENCES dbo.AiExternalApplication(ApplicationId),
 Revision bigint NOT NULL, MetadataDigest binary(32) NOT NULL, MethodMask tinyint NOT NULL CHECK(MethodMask BETWEEN 1 AND 3),
 Disabled bit NOT NULL, RecordedAt datetime2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT PK_AiExternalApplicationRevision PRIMARY KEY(ApplicationId,Revision)
);
CREATE TABLE dbo.AiTenantApplicationApproval(
 Id bigint IDENTITY CONSTRAINT PK_AiTenantApplicationApproval PRIMARY KEY,
 ApprovalId uniqueidentifier NOT NULL CONSTRAINT UQ_AiTenantApplicationApproval_Id UNIQUE DEFAULT NEWID(),
 ShaleClientId int NOT NULL REFERENCES dbo.ShaleClients(Id),
 ApplicationId uniqueidentifier NOT NULL REFERENCES dbo.AiExternalApplication(ApplicationId),
 ApplicationRevision bigint NOT NULL,
 IntegrationId uniqueidentifier NULL,
 State varchar(8) NOT NULL CONSTRAINT CK_AiTenantApplicationApproval_State CHECK(State IN('PENDING','APPROVED','DENIED','REVOKED')),
 RequestedScopeMask tinyint NOT NULL CHECK(RequestedScopeMask BETWEEN 1 AND 3),
 ScopeMask tinyint NOT NULL DEFAULT 0 CHECK(ScopeMask BETWEEN 0 AND 3),
 RequestedByUserId int NOT NULL, UpdatedByUserId int NOT NULL,
 CreatedAt datetime2(7) NOT NULL DEFAULT SYSUTCDATETIME(), UpdatedAt datetime2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
 ExpiresAt datetime2(7) NOT NULL, ClosedAt datetime2(7) NULL, RowVer rowversion NOT NULL,
 CONSTRAINT FK_AiTenantApplicationApproval_Revision FOREIGN KEY(ApplicationId,ApplicationRevision) REFERENCES dbo.AiExternalApplicationRevision(ApplicationId,Revision),
 CONSTRAINT UQ_AiTenantApplicationApproval_Owner UNIQUE(ShaleClientId,ApprovalId),
 CONSTRAINT FK_AiTenantApplicationApproval_Requester FOREIGN KEY(ShaleClientId,RequestedByUserId) REFERENCES dbo.Users(ShaleClientId,id),
 CONSTRAINT FK_AiTenantApplicationApproval_Actor FOREIGN KEY(ShaleClientId,UpdatedByUserId) REFERENCES dbo.Users(ShaleClientId,id),
 CONSTRAINT FK_AiTenantApplicationApproval_Integration FOREIGN KEY(ShaleClientId,IntegrationId) REFERENCES dbo.AiIntegrationRegistration(ShaleClientId,IntegrationId),
 CONSTRAINT CK_AiTenantApplicationApproval_Closed CHECK((State IN('PENDING','APPROVED') AND ClosedAt IS NULL) OR (State IN('DENIED','REVOKED') AND ClosedAt IS NOT NULL)),
 CONSTRAINT CK_AiTenantApplicationApproval_Scopes CHECK((ScopeMask & RequestedScopeMask)=ScopeMask),
 CONSTRAINT CK_AiTenantApplicationApproval_Binding CHECK((State IN('APPROVED','REVOKED') AND IntegrationId IS NOT NULL) OR (State IN('PENDING','DENIED') AND IntegrationId IS NULL))
);
CREATE UNIQUE INDEX UX_AiTenantApplicationApproval_Current ON dbo.AiTenantApplicationApproval(ShaleClientId,ApplicationId) WHERE ClosedAt IS NULL;
CREATE UNIQUE INDEX UX_AiTenantApplicationApproval_Integration ON dbo.AiTenantApplicationApproval(ShaleClientId,IntegrationId) WHERE IntegrationId IS NOT NULL;
EXEC(N'ALTER SECURITY POLICY sec.TenantFilter
 ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiTenantApplicationApproval,
 ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiTenantApplicationApproval AFTER INSERT,
 ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AiTenantApplicationApproval AFTER UPDATE;');
CREATE ROLE AiApplicationPublisher AUTHORIZATION dbo;
CREATE ROLE AiApplicationTenant AUTHORIZATION dbo;

DECLARE @AuditObjectId int=OBJECT_ID(N'dbo.EntityActionAuditLog'),@EntityTypeColumnId int=COLUMNPROPERTY(OBJECT_ID(N'dbo.EntityActionAuditLog'),N'EntityType',N'ColumnId');
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
    INSERT @Allowed (Value) VALUES ('AI_APPLICATION_APPROVAL');

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

    DECLARE @auditValues nvarchar(max) =
    (
        SELECT STRING_AGG(CONVERT(nvarchar(max), QUOTENAME(Value, '''')), N',')
               WITHIN GROUP (ORDER BY Value) FROM @Allowed
    );
    DECLARE @auditSql nvarchar(max) = N'ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT '
        + QUOTENAME(@AllowlistName) + N';';
    EXEC sys.sp_executesql @auditSql;
    SET @auditSql = N'ALTER TABLE dbo.EntityActionAuditLog WITH CHECK ADD CONSTRAINT '
        + QUOTENAME(N'CK_EntityActionAuditLog_EntityType')
        + N' CHECK (EntityType IN (' + @auditValues + N'));';
    EXEC sys.sp_executesql @auditSql;
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

EXEC(N'CREATE PROCEDURE dbo.AiPublishApplicationV1
@Issuer nvarchar(max),@Resource nvarchar(max),@ExternalClientId nvarchar(max),@MetadataDigest varbinary(max),@MethodMask tinyint,
 @Disabled bit,@ExpectedRowVer varbinary(max),@CorrelationId uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 THROW 59300,''Fresh autocommit lease required.'',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 IF ISNULL(IS_MEMBER(N''AiApplicationPublisher''),0)<>1 OR SESSION_CONTEXT(N''ShaleClientId'') IS NOT NULL OR SESSION_CONTEXT(N''PrincipalUserId'') IS NOT NULL
 THROW 59301,''Registry operator required.'',1;
 IF @Issuer IS NULL OR DATALENGTH(@Issuer) NOT BETWEEN 18 AND 512 OR @Issuer NOT LIKE N''https://%/%''
 OR @Resource IS NULL OR DATALENGTH(@Resource) NOT BETWEEN 18 AND 512 OR @Resource NOT LIKE N''https://%/%''
 OR @ExternalClientId IS NULL OR DATALENGTH(@ExternalClientId) NOT BETWEEN 20 AND 4096
 OR @ExternalClientId NOT LIKE N''https://%/%'' OR RIGHT(@ExternalClientId,1)=N'' ''
 OR DATALENGTH(@MetadataDigest)<>32 OR @MetadataDigest IS NULL OR @MethodMask IS NULL OR @MethodMask NOT BETWEEN 1 AND 3
 OR @Disabled IS NULL OR @CorrelationId IS NULL THROW 59302,''Invalid publication.'',1;
 DECLARE @lock int;
 EXEC @lock=sys.sp_getapplock @Resource=N''AiApplicationRegistryV1'',@LockMode=''Exclusive'',@LockOwner=''Transaction'',@LockTimeout=3000;
 IF @lock<0 THROW 59300,''Registry lock unavailable.'',1;
 DECLARE @hash binary(32)=HASHBYTES(''SHA2_256'',CONVERT(varbinary(max),@Issuer)+0x0000+CONVERT(varbinary(max),@ExternalClientId)),@id bigint;
 SELECT @id=Id FROM dbo.AiExternalApplication WHERE IdentityHash=@hash;
 IF @id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.AiExternalApplication WHERE Id=@id
 AND DATALENGTH(Issuer)=DATALENGTH(@Issuer) AND DATALENGTH(ExternalClientId)=DATALENGTH(@ExternalClientId) AND DATALENGTH(Resource)=DATALENGTH(@Resource)
 AND CONVERT(varbinary(max),Issuer)=CONVERT(varbinary(max),@Issuer) AND CONVERT(varbinary(max),ExternalClientId)=CONVERT(varbinary(max),@ExternalClientId) AND CONVERT(varbinary(max),Resource)=CONVERT(varbinary(max),@Resource))
 THROW 59302,''Application identity collision.'',1;
 IF @id IS NULL BEGIN
  IF @ExpectedRowVer IS NOT NULL THROW 59303,''Publication version conflict.'',1;
  INSERT dbo.AiExternalApplication(Issuer,Resource,ExternalClientId,IdentityHash,Source,MetadataDigest,MethodMask,Disabled)
  VALUES(@Issuer,@Resource,@ExternalClientId,@hash,''CIMD'',@MetadataDigest,@MethodMask,@Disabled);
  SET @id=SCOPE_IDENTITY();
 END ELSE BEGIN
  IF DATALENGTH(@ExpectedRowVer)<>8 OR @ExpectedRowVer IS NULL THROW 59303,''Publication version conflict.'',1;
  UPDATE dbo.AiExternalApplication SET MetadataDigest=@MetadataDigest,MethodMask=@MethodMask,Disabled=@Disabled,Revision=Revision+1,UpdatedAt=SYSUTCDATETIME()
  WHERE Id=@id AND RowVer=@ExpectedRowVer;
  IF @@ROWCOUNT<>1 THROW 59303,''Publication version conflict.'',1;
 END;
 INSERT dbo.AiExternalApplicationRevision(ApplicationId,Revision,MetadataDigest,MethodMask,Disabled)
 SELECT ApplicationId,Revision,MetadataDigest,MethodMask,Disabled FROM dbo.AiExternalApplication WHERE Id=@id;
 INSERT dbo.GlobalControlPlaneAuditLog(OperatorId,EntityType,EntityId,Action,Metadata)
 VALUES(USER_NAME(),''AI_EXTERNAL_APPLICATION'',@id,''UPDATED'',CONCAT(N''{"CORRELATION_ID":"'',CONVERT(nvarchar(36),@CorrelationId),N''"}''));
 IF @@ROWCOUNT<>1 THROW 59304,''Application audit unavailable.'',1;
 SELECT ApplicationId,Revision,Disabled,RowVer FROM dbo.AiExternalApplication WHERE Id=@id;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');

EXEC(N'CREATE PROCEDURE dbo.AiRequestApplicationV1
@ApplicationId uniqueidentifier,@ExpectedApplicationRowVer varbinary(max),@ScopeMask tinyint,@CorrelationId uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 THROW 59300,''Fresh autocommit lease required.'',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),@now datetime2(7)=SYSUTCDATETIME();
 IF DATALENGTH(@ExpectedApplicationRowVer)<>8 OR @ExpectedApplicationRowVer IS NULL OR @CorrelationId IS NULL OR @ScopeMask IS NULL OR @ScopeMask NOT BETWEEN 1 AND 3 THROW 59302,''Invalid request.'',1;
 DECLARE @rev bigint,@id bigint;
 SELECT @rev=Revision FROM dbo.AiExternalApplication WITH(HOLDLOCK) WHERE ApplicationId=@ApplicationId AND Disabled=0 AND RowVer=@ExpectedApplicationRowVer;
 IF @rev IS NULL THROW 59303,''Application unavailable or stale.'',1;
 IF EXISTS(SELECT 1 FROM dbo.AiTenantApplicationApproval WHERE ShaleClientId=@t AND ApplicationId=@ApplicationId AND ClosedAt IS NULL)
 THROW 59303,''Current approval exists; reload or close it.'',1;
 INSERT dbo.AiTenantApplicationApproval(ShaleClientId,ApplicationId,ApplicationRevision,State,RequestedScopeMask,RequestedByUserId,UpdatedByUserId,ExpiresAt)
 VALUES(@t,@ApplicationId,@rev,''PENDING'',@ScopeMask,@u,@u,DATEADD(day,1,@now));
 SET @id=SCOPE_IDENTITY();
 DECLARE @action varchar(16)=''CREATED'',@scope tinyint=0;
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 SELECT @t,@u,''AI_APPLICATION_APPROVAL'',@id,@action,@CorrelationId,''SHALE_SERVICE'',CONCAT(N''{"AI_SCOPE_MASK":'',@scope,N'',"AI_APPROVAL_STATE":"'',State,N''"}'') FROM dbo.AiTenantApplicationApproval WHERE Id=@id AND ShaleClientId=@t;
 IF @@ROWCOUNT<>1 THROW 59304,''Approval audit unavailable.'',1;
SELECT ApprovalId,ApplicationId,ApplicationRevision,IntegrationId,State,ScopeMask,ExpiresAt,RowVer FROM dbo.AiTenantApplicationApproval WHERE Id=@id AND ShaleClientId=@t;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');

EXEC(N'CREATE PROCEDURE dbo.AiDecideApplicationV1
@ApprovalId uniqueidentifier,@ExpectedRowVer varbinary(max),@Decision varchar(16),@ScopeMask tinyint,@Days int,@CorrelationId uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 THROW 59300,''Fresh autocommit lease required.'',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.AiLockV1;
 EXEC dbo.AiActorV1;
 DECLARE @t int=CONVERT(int,SESSION_CONTEXT(N''ShaleClientId'')),@u int=CONVERT(int,SESSION_CONTEXT(N''PrincipalUserId'')),@now datetime2(7)=SYSUTCDATETIME();
 EXEC dbo.AiActorV1 @Admin=1;
 IF @CorrelationId IS NULL OR @Decision IS NULL OR @Decision NOT IN(''APPROVE'',''DENY'',''REVOKE'',''REDUCE'') OR @ScopeMask IS NULL OR @ScopeMask>3
 THROW 59302,''Invalid decision.'',1;
 DECLARE @id bigint,@application uniqueidentifier,@rev bigint,@state varchar(8),@oldScope tinyint,@requested tinyint,@expires datetime2(7),@integration uniqueidentifier;
 SELECT @id=Id,@application=ApplicationId,@rev=ApplicationRevision,@state=State,@oldScope=ScopeMask,@requested=RequestedScopeMask,@expires=ExpiresAt,@integration=IntegrationId
 FROM dbo.AiTenantApplicationApproval WHERE ShaleClientId=@t AND ApprovalId=@ApprovalId AND RowVer=@ExpectedRowVer;
 IF @id IS NULL THROW 59303,''Approval unavailable or stale.'',1;
 IF @Decision IN(''APPROVE'',''DENY'') AND @state<>''PENDING'' OR @Decision IN(''REVOKE'',''REDUCE'') AND @state<>''APPROVED''
 THROW 59303,''Invalid approval transition.'',1;
 IF @Decision=''APPROVE'' BEGIN
  EXEC dbo.AiEnabledV1;
  IF @expires<=SYSUTCDATETIME() OR @Days IS NULL OR @Days NOT BETWEEN 1 AND 90 OR @ScopeMask=0 OR (@ScopeMask & @requested)<>@ScopeMask
  OR NOT EXISTS(SELECT 1 FROM dbo.AiExternalApplication WITH(HOLDLOCK) WHERE ApplicationId=@application AND Revision=@rev AND Disabled=0)
  THROW 59303,''Expired or stale approval.'',1;
  SET @integration=NEWID(); SET @expires=DATEADD(day,@Days,SYSUTCDATETIME());
  /* Deliberately SUSPENDED, no callbacks/keys: PR 4 owns activation and wire binding. */
  INSERT dbo.AiIntegrationRegistration(ShaleClientId,IntegrationId,ClientId,Issuer,Resource,Name,ClientType,Status,ScopeMask,CreatedByUserId,UpdatedByUserId,ExpiresAt)
  SELECT @t,@integration,CONVERT(varchar(36),NEWID()),Issuer,Resource,N''External application'',CASE WHEN MethodMask=2 THEN ''CONFIDENTIAL'' ELSE ''PUBLIC'' END,''SUSPENDED'',@ScopeMask,@u,@u,@expires
  FROM dbo.AiExternalApplication WHERE ApplicationId=@application;
  INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source)
  VALUES(@t,@u,''AI_INTEGRATION'',SCOPE_IDENTITY(),''CREATED'',@CorrelationId,''SHALE_SERVICE'');
  IF @@ROWCOUNT<>1 THROW 59304,''Integration audit unavailable.'',1;
 END;
 IF @Decision=''REDUCE'' AND ((@ScopeMask & @oldScope)<>@ScopeMask OR @expires<=SYSUTCDATETIME()
 OR NOT EXISTS(SELECT 1 FROM dbo.AiIntegrationRegistration WHERE ShaleClientId=@t AND IntegrationId=@integration AND (ScopeMask & @ScopeMask)=@ScopeMask)) THROW 59303,''Ceiling may only be reduced.'',1;
 IF @Decision IN(''REVOKE'',''REDUCE'') BEGIN
  IF @Decision=''REVOKE'' SET @ScopeMask=0;
  UPDATE dbo.AiIntegrationRegistration SET ScopeMask=@ScopeMask,Status=CASE WHEN @Decision=''REVOKE'' THEN ''REVOKED'' ELSE Status END,
   RevokedAt=CASE WHEN @Decision=''REVOKE'' THEN COALESCE(RevokedAt,@now) ELSE RevokedAt END,RevokedByUserId=CASE WHEN @Decision=''REVOKE'' THEN COALESCE(RevokedByUserId,@u) ELSE RevokedByUserId END,
   RevocationReason=CASE WHEN @Decision=''REVOKE'' THEN COALESCE(RevocationReason,''ADMIN'') ELSE RevocationReason END,Revision=Revision+1,UpdatedAt=@now,UpdatedByUserId=@u
  WHERE ShaleClientId=@t AND IntegrationId=@integration;
  UPDATE dbo.AiDelegationGrant SET RevokedAt=@now,RevokedByUserId=@u,RevocationReason=''ADMIN'',Revision=Revision+1
  WHERE ShaleClientId=@t AND IntegrationId=@integration AND RevokedAt IS NULL AND (@Decision=''REVOKE'' OR (ScopeMask & @ScopeMask)<>ScopeMask);
  UPDATE s SET RevokedAt=@now,RevocationReason=''ADMIN''
  FROM dbo.AiDelegatedSession s JOIN dbo.AiDelegationGrant g ON g.ShaleClientId=s.ShaleClientId AND g.GrantId=s.GrantId
  WHERE s.ShaleClientId=@t AND s.IntegrationId=@integration AND s.RevokedAt IS NULL AND g.RevokedAt IS NOT NULL;
  UPDATE c SET RevokedAt=@now FROM dbo.AiOAuthCredential c JOIN dbo.AiDelegationGrant g ON g.ShaleClientId=c.ShaleClientId AND g.GrantId=c.GrantId
  WHERE c.ShaleClientId=@t AND c.IntegrationId=@integration AND c.RevokedAt IS NULL AND g.RevokedAt IS NOT NULL;
  UPDATE dbo.AiConsentIntent SET ConsumedAt=@now WHERE ShaleClientId=@t AND IntegrationId=@integration AND ConsumedAt IS NULL;
  INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
  SELECT @t,@u,''AI_INTEGRATION'',Id,''UPDATED'',@CorrelationId,''SHALE_SERVICE'',CONCAT(N''{"AI_SCOPE_MASK":'',@ScopeMask,N''}'')
  FROM dbo.AiIntegrationRegistration WHERE ShaleClientId=@t AND IntegrationId=@integration;
  IF @@ROWCOUNT<>1 THROW 59304,''Integration audit unavailable.'',1;
 END;
 IF @Decision=''DENY'' SET @ScopeMask=0;
 UPDATE dbo.AiTenantApplicationApproval SET State=CASE @Decision WHEN ''APPROVE'' THEN ''APPROVED'' WHEN ''DENY'' THEN ''DENIED'' WHEN ''REVOKE'' THEN ''REVOKED'' ELSE State END,
 ScopeMask=@ScopeMask,IntegrationId=@integration,ExpiresAt=@expires,UpdatedByUserId=@u,UpdatedAt=@now,
 ClosedAt=CASE WHEN @Decision IN(''DENY'',''REVOKE'') THEN @now ELSE NULL END WHERE Id=@id AND ShaleClientId=@t;
 DECLARE @action varchar(16)=''UPDATED'',@scope tinyint=@ScopeMask;
 INSERT dbo.EntityActionAuditLog(ShaleClientId,ActorUserId,EntityType,EntityId,Action,CorrelationId,Source,Metadata)
 SELECT @t,@u,''AI_APPLICATION_APPROVAL'',@id,@action,@CorrelationId,''SHALE_SERVICE'',CONCAT(N''{"AI_SCOPE_MASK":'',@scope,N'',"AI_APPROVAL_STATE":"'',State,N''"}'') FROM dbo.AiTenantApplicationApproval WHERE Id=@id AND ShaleClientId=@t;
 IF @@ROWCOUNT<>1 THROW 59304,''Approval audit unavailable.'',1;
SELECT ApprovalId,ApplicationId,ApplicationRevision,IntegrationId,State,ScopeMask,ExpiresAt,RowVer FROM dbo.AiTenantApplicationApproval WHERE Id=@id AND ShaleClientId=@t;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;');

EXEC(N'CREATE TRIGGER dbo.AiImmutable_ApplicationRevision ON dbo.AiExternalApplicationRevision AFTER UPDATE,DELETE AS THROW 59305,''Publication history is append only.'',1;');
EXEC(N'CREATE TRIGGER dbo.AiImmutable_ExternalApplication ON dbo.AiExternalApplication AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON;
 IF NOT EXISTS(SELECT 1 FROM deleted) RETURN;
 IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON i.Id=d.Id WHERE i.Revision<>d.Revision+1) THROW 59305,''Application revision must advance once.'',1;
 IF NOT EXISTS(SELECT 1 FROM inserted) OR UPDATE(ApplicationId) OR UPDATE(Issuer) OR UPDATE(Resource) OR UPDATE(ExternalClientId) OR UPDATE(IdentityHash) OR UPDATE(Source) OR UPDATE(CreatedAt)
 THROW 59305,''Application identity/history is immutable.'',1;
END;');
EXEC(N'CREATE TRIGGER dbo.AiImmutable_ApplicationApproval ON dbo.AiTenantApplicationApproval AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON;
 IF NOT EXISTS(SELECT 1 FROM deleted) RETURN;
 IF NOT EXISTS(SELECT 1 FROM inserted) OR UPDATE(ApprovalId) OR UPDATE(ShaleClientId) OR UPDATE(ApplicationId) OR UPDATE(ApplicationRevision) OR UPDATE(RequestedByUserId) OR UPDATE(RequestedScopeMask) OR UPDATE(CreatedAt)
 THROW 59305,''Approval ownership/history is immutable.'',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON i.Id=d.Id WHERE d.ClosedAt IS NOT NULL OR d.IntegrationId IS NOT NULL AND (i.IntegrationId IS NULL OR i.IntegrationId<>d.IntegrationId))
 THROW 59305,''Closed approval or integration binding is immutable.'',1;
END;');
GRANT EXECUTE ON dbo.AiPublishApplicationV1 TO AiApplicationPublisher;
GRANT EXECUTE ON dbo.AiRequestApplicationV1 TO AiApplicationTenant;
GRANT EXECUTE ON dbo.AiDecideApplicationV1 TO AiApplicationTenant;

DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON dbo.AiExternalApplication TO AiApplicationPublisher;

DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON dbo.AiExternalApplicationRevision TO AiApplicationPublisher;

DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON dbo.AiTenantApplicationApproval TO AiApplicationPublisher;

DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON dbo.AiExternalApplication TO AiApplicationTenant;

DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON dbo.AiExternalApplicationRevision TO AiApplicationTenant;

DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON dbo.AiTenantApplicationApproval TO AiApplicationTenant;

DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON dbo.AiExternalApplication TO AiIntegrationRuntime;

DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON dbo.AiExternalApplicationRevision TO AiIntegrationRuntime;

DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON dbo.AiTenantApplicationApproval TO AiIntegrationRuntime;

DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON dbo.AiExternalApplication TO AiIntegrationBootstrap;

DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON dbo.AiExternalApplicationRevision TO AiIntegrationBootstrap;

DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON dbo.AiTenantApplicationApproval TO AiIntegrationBootstrap;

DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON dbo.AiExternalApplication TO AiCapabilityOperator;

DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON dbo.AiExternalApplicationRevision TO AiCapabilityOperator;

DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON dbo.AiTenantApplicationApproval TO AiCapabilityOperator;

DECLARE @Owned table(Name sysname PRIMARY KEY); INSERT @Owned VALUES (N'AiExternalApplication'),(N'AiExternalApplicationRevision'),(N'AiTenantApplicationApproval'),(N'AiPublishApplicationV1'),(N'AiRequestApplicationV1'),(N'AiDecideApplicationV1'),(N'AiImmutable_ExternalApplication'),(N'AiImmutable_ApplicationRevision'),(N'AiImmutable_ApplicationApproval'),(N'AiLockV1'),(N'AiActorV1'),(N'AiEnabledV1');
DECLARE @catalog nvarchar(max)=N'';
SET @catalog=@catalog+(SELECT CONCAT(o.name,N':',o.type,N':',COALESCE(CONVERT(nvarchar(20),o.principal_id),N''),N':',COALESCE(m.definition,N''),N';') AS [text()] FROM sys.objects o LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.schema_id=SCHEMA_ID(N'dbo') AND o.name IN(SELECT Name FROM @Owned) ORDER BY o.name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(c.object_id),N':',c.name,N':',c.column_id,N':',c.system_type_id,N':',c.max_length,N':',c.precision,N':',c.scale,N':',c.is_nullable,N':',c.is_identity,N':',c.collation_name,N':',cc.definition,N';') AS [text()] FROM sys.columns c LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id WHERE OBJECT_NAME(c.object_id) IN(SELECT Name FROM @Owned) ORDER BY c.object_id,c.column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(o.parent_object_id),N':',o.name,N':',o.type,N':',ch.definition,N':',ch.is_disabled,N':',ch.is_not_trusted,N':',dc.definition,N':',fk.referenced_object_id,N':',fk.delete_referential_action,N':',fk.update_referential_action,N':',fk.is_disabled,N':',fk.is_not_trusted,N';') AS [text()] FROM sys.objects o LEFT JOIN sys.check_constraints ch ON ch.object_id=o.object_id LEFT JOIN sys.default_constraints dc ON dc.object_id=o.object_id LEFT JOIN sys.foreign_keys fk ON fk.object_id=o.object_id WHERE OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY o.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(fkc.constraint_object_id,N':',fkc.parent_column_id,N':',fkc.referenced_column_id,N';') AS [text()] FROM sys.foreign_key_columns fkc WHERE OBJECT_NAME(fkc.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY fkc.constraint_object_id,fkc.constraint_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(i.object_id),N':',i.name,N':',i.is_unique,N':',i.is_disabled,N':',i.filter_definition,N':',ic.column_id,N':',ic.key_ordinal,N':',ic.is_included_column,N':',ic.is_descending_key,N';') AS [text()] FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id WHERE OBJECT_NAME(i.object_id) IN(SELECT Name FROM @Owned) ORDER BY i.object_id,i.index_id,ic.index_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(p.target_object_id,N':',p.predicate_definition,N':',p.predicate_type,N':',p.operation,N':',sp.is_enabled,N':',sp.is_schema_bound,N';') AS [text()] FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id WHERE OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @Owned) ORDER BY p.target_object_id,p.security_predicate_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(p.major_id,N':',p.minor_id,N':',p.grantor_principal_id,N':',p.grantee_principal_id,N':',p.permission_name,N':',p.state,N';') AS [text()] FROM sys.database_permissions p WHERE p.class=1 AND OBJECT_NAME(p.major_id) IN(SELECT Name FROM @Owned) ORDER BY p.major_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(t.object_id,N':',t.is_disabled,N';') AS [text()] FROM sys.triggers t WHERE t.name IN(SELECT Name FROM @Owned) ORDER BY t.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N':',owning_principal_id,N';') AS [text()] FROM sys.database_principals WHERE name IN(N'AiApplicationPublisher',N'AiApplicationTenant') ORDER BY name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)'),N'');
SET @catalog=@catalog+COALESCE(OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenant')),N'MISSING');

SET @catalog=@catalog+COALESCE((SELECT CONCAT(p.class,N':',p.major_id,N':',p.minor_id,N':',USER_NAME(p.grantee_principal_id),N':',p.permission_name,N':',p.state,N';') AS [text()] FROM sys.database_permissions p WHERE p.grantee_principal_id IN(DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher'),DATABASE_PRINCIPAL_ID(N'AiApplicationTenant')) ORDER BY p.class,p.major_id,p.minor_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)'),N'');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N':',definition,N':',is_disabled,N':',is_not_trusted,N';') AS [text()] FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.EntityActionAuditLog') ORDER BY name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)'),N'');

DECLARE @receipt varbinary(32)=HASHBYTES('SHA2_256',@catalog); EXEC sys.sp_addextendedproperty @name=N'Shale.UniversalMcpPR3',@value=@receipt,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'AiExternalApplication';

COMMIT TRANSACTION;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
