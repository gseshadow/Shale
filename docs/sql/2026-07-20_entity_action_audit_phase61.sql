/*
 Phase 6.1 Entity-action audit foundation.
 Additive, append-only audit event table for tenant-owned entity actions that must
 not store PHI values, URLs, descriptions, notes, RowVer bytes, DTOs, SQL, or exception text.
 Apply manually after review; do not deploy application code that writes this table until verified.
*/
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.ShaleClients', N'U') IS NULL THROW 54610, 'Required table dbo.ShaleClients is missing.', 1;
IF OBJECT_ID(N'dbo.Users', N'U') IS NULL THROW 54611, 'Required table dbo.Users is missing.', 1;
IF OBJECT_ID(N'sec.fn_FilterByTenant', N'IF') IS NULL AND OBJECT_ID(N'sec.fn_FilterByTenant', N'FN') IS NULL THROW 54612, 'Required tenant RLS predicate sec.fn_FilterByTenant is missing.', 1;

IF OBJECT_ID(N'dbo.EntityActionAuditLog', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.EntityActionAuditLog (
        Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_EntityActionAuditLog PRIMARY KEY,
        ShaleClientId int NOT NULL,
        ActorUserId int NOT NULL,
        EntityType varchar(64) NOT NULL,
        EntityId bigint NOT NULL,
        Action varchar(64) NOT NULL,
        OccurredAt datetime2(7) NOT NULL CONSTRAINT DF_EntityActionAuditLog_OccurredAt DEFAULT (SYSUTCDATETIME()),
        ParentEntityType varchar(64) NULL,
        ParentEntityId bigint NULL,
        CorrelationId uniqueidentifier NULL,
        Source varchar(64) NULL,
        Metadata nvarchar(1000) NULL,
        CONSTRAINT CK_EntityActionAuditLog_PositiveIds CHECK (ShaleClientId > 0 AND ActorUserId > 0 AND EntityId > 0 AND (ParentEntityId IS NULL OR ParentEntityId > 0)),
        CONSTRAINT CK_EntityActionAuditLog_EntityType CHECK (EntityType IN ('LINK_TYPE','CASE_LINK','CASE_LINK_SHARE')),
        CONSTRAINT CK_EntityActionAuditLog_Action CHECK (Action IN ('CREATED','OVERRIDE_CREATED','UPDATED','ACTIVATED','DEACTIVATED','OVERRIDE_RESET','DELETED','PRIMARY_SET','REORDERED','ADDED','REMOVED')),
        CONSTRAINT FK_EntityActionAuditLog_ShaleClientId_ShaleClients FOREIGN KEY (ShaleClientId) REFERENCES dbo.ShaleClients(Id),
        CONSTRAINT FK_EntityActionAuditLog_ActorUserId_Users FOREIGN KEY (ActorUserId) REFERENCES dbo.Users(id)
    );
END
ELSE
BEGIN
    IF COL_LENGTH(N'dbo.EntityActionAuditLog', N'ShaleClientId') IS NULL OR COL_LENGTH(N'dbo.EntityActionAuditLog', N'ActorUserId') IS NULL OR COL_LENGTH(N'dbo.EntityActionAuditLog', N'EntityType') IS NULL OR COL_LENGTH(N'dbo.EntityActionAuditLog', N'EntityId') IS NULL OR COL_LENGTH(N'dbo.EntityActionAuditLog', N'Action') IS NULL OR COL_LENGTH(N'dbo.EntityActionAuditLog', N'OccurredAt') IS NULL
        THROW 54613, 'Existing dbo.EntityActionAuditLog is missing required columns.', 1;
END

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.EntityActionAuditLog') AND name = N'IX_EntityActionAuditLog_Tenant_OccurredAt')
    CREATE INDEX IX_EntityActionAuditLog_Tenant_OccurredAt ON dbo.EntityActionAuditLog (ShaleClientId, OccurredAt DESC);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.EntityActionAuditLog') AND name = N'IX_EntityActionAuditLog_Tenant_Entity')
    CREATE INDEX IX_EntityActionAuditLog_Tenant_Entity ON dbo.EntityActionAuditLog (ShaleClientId, EntityType, EntityId, OccurredAt DESC);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.EntityActionAuditLog') AND name = N'IX_EntityActionAuditLog_Tenant_Actor')
    CREATE INDEX IX_EntityActionAuditLog_Tenant_Actor ON dbo.EntityActionAuditLog (ShaleClientId, ActorUserId, OccurredAt DESC);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.EntityActionAuditLog') AND name = N'IX_EntityActionAuditLog_Tenant_Parent')
    CREATE INDEX IX_EntityActionAuditLog_Tenant_Parent ON dbo.EntityActionAuditLog (ShaleClientId, ParentEntityType, ParentEntityId, OccurredAt DESC);

DECLARE @PolicyName sysname;
SELECT TOP (1) @PolicyName = QUOTENAME(SCHEMA_NAME(schema_id)) + N'.' + QUOTENAME(name) FROM sys.security_policies WHERE name = N'TenantFilter';
IF @PolicyName IS NULL THROW 54614, 'Required TenantFilter security policy is missing.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.security_predicates WHERE target_object_id = OBJECT_ID(N'dbo.EntityActionAuditLog') AND predicate_type_desc = N'FILTER')
    EXEC(N'ALTER SECURITY POLICY ' + @PolicyName + N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.EntityActionAuditLog;');

COMMIT TRANSACTION;

SELECT c.name, t.name AS type_name, c.max_length, c.is_nullable FROM sys.columns c JOIN sys.types t ON t.user_type_id = c.user_type_id WHERE c.object_id = OBJECT_ID(N'dbo.EntityActionAuditLog') ORDER BY c.column_id;
SELECT name FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.EntityActionAuditLog') ORDER BY name;
SELECT predicate_definition FROM sys.security_predicates WHERE target_object_id = OBJECT_ID(N'dbo.EntityActionAuditLog');

/* Tenant 7 / tenant 8 visibility verification: insert one row per tenant in a transaction, set SESSION_CONTEXT to 7 then 8, assert each sees only its own rows, then ROLLBACK.
Rollback guidance: remove the EntityActionAuditLog predicate from TenantFilter, drop indexes/FKs as needed, then drop dbo.EntityActionAuditLog. Do not alter dbo.AuditLog. */
