/* Forward-only, idempotent append-only Material Request Updates history. */
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.MaterialRequestUpdates', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MaterialRequestUpdates (
        Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_MaterialRequestUpdates PRIMARY KEY,
        ShaleClientId int NOT NULL,
        CaseId int NOT NULL,
        MaterialRequestId bigint NOT NULL,
        UpdateType varchar(20) NOT NULL,
        FieldKey varchar(40) NULL,
        Body nvarchar(4000) NOT NULL,
        ActorUserId int NOT NULL,
        CreatedAt datetime2 NOT NULL CONSTRAINT DF_MaterialRequestUpdates_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT CK_MaterialRequestUpdates_UpdateType CHECK (UpdateType IN ('USER_NOTE','SYSTEM_CHANGE','SYSTEM_EVENT')),
        CONSTRAINT CK_MaterialRequestUpdates_Body CHECK (LEN(LTRIM(RTRIM(Body))) BETWEEN 1 AND 4000)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.MaterialRequestUpdates') AND name=N'IX_MaterialRequestUpdates_Request_Chronology')
    CREATE INDEX IX_MaterialRequestUpdates_Request_Chronology
        ON dbo.MaterialRequestUpdates (ShaleClientId, MaterialRequestId, CreatedAt DESC, Id DESC)
        INCLUDE (CaseId, UpdateType, FieldKey, ActorUserId);

IF OBJECT_ID(N'dbo.FK_MaterialRequestUpdates_Request_Tenant_Case', N'F') IS NULL
    ALTER TABLE dbo.MaterialRequestUpdates ADD CONSTRAINT FK_MaterialRequestUpdates_Request_Tenant_Case
        FOREIGN KEY (ShaleClientId, CaseId, MaterialRequestId) REFERENCES dbo.MaterialRequests(ShaleClientId, CaseId, Id);
IF OBJECT_ID(N'dbo.FK_MaterialRequestUpdates_ShaleClientId_ShaleClients', N'F') IS NULL
    ALTER TABLE dbo.MaterialRequestUpdates ADD CONSTRAINT FK_MaterialRequestUpdates_ShaleClientId_ShaleClients
        FOREIGN KEY (ShaleClientId) REFERENCES dbo.ShaleClients(Id);
IF OBJECT_ID(N'dbo.FK_MaterialRequestUpdates_ActorUserId_Users', N'F') IS NULL
    ALTER TABLE dbo.MaterialRequestUpdates ADD CONSTRAINT FK_MaterialRequestUpdates_ActorUserId_Users
        FOREIGN KEY (ActorUserId) REFERENCES dbo.Users(Id);

DECLARE @PolicyObjectId int, @PolicyQualified nvarchar(517), @Sql nvarchar(max);
SELECT TOP (1) @PolicyObjectId=sp.object_id,
       @PolicyQualified=QUOTENAME(SCHEMA_NAME(sp.schema_id))+N'.'+QUOTENAME(sp.name)
FROM sys.security_policies sp
JOIN sys.security_predicates p ON p.object_id=sp.object_id
WHERE p.target_object_id=OBJECT_ID(N'dbo.MaterialRequests') AND p.predicate_type_desc=N'FILTER';
IF @PolicyObjectId IS NULL THROW 54729, 'MaterialRequests tenant RLS policy was not found.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyObjectId AND target_object_id=OBJECT_ID(N'dbo.MaterialRequestUpdates') AND predicate_type_desc=N'FILTER')
BEGIN
    SET @Sql=N'ALTER SECURITY POLICY '+@PolicyQualified+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.MaterialRequestUpdates;';
    EXEC sys.sp_executesql @Sql;
END;

COMMIT TRANSACTION;
