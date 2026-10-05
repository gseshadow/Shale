SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.TaskPolicyConfigurations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.TaskPolicyConfigurations (
        Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_TaskPolicyConfigurations PRIMARY KEY,
        ShaleClientId int NOT NULL,
        DueDatePolicy varchar(16) NOT NULL CONSTRAINT DF_TaskPolicyConfigurations_DueDatePolicy DEFAULT ('WARN'),
        CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_TaskPolicyConfigurations_CreatedAt DEFAULT SYSUTCDATETIME(),
        UpdatedAt datetime2(7) NOT NULL CONSTRAINT DF_TaskPolicyConfigurations_UpdatedAt DEFAULT SYSUTCDATETIME(),
        CreatedByUserId int NULL,
        UpdatedByUserId int NULL,
        RowVer rowversion NOT NULL,
        CONSTRAINT FK_TaskPolicyConfigurations_ShaleClients FOREIGN KEY (ShaleClientId) REFERENCES dbo.ShaleClients(Id),
        CONSTRAINT CK_TaskPolicyConfigurations_DueDatePolicy CHECK (DueDatePolicy IN ('OPTIONAL','WARN','REQUIRED')),
        CONSTRAINT UQ_TaskPolicyConfigurations_Tenant UNIQUE (ShaleClientId)
    );
END;

INSERT dbo.TaskPolicyConfigurations (ShaleClientId, DueDatePolicy)
SELECT c.Id, 'WARN' FROM dbo.ShaleClients c
WHERE NOT EXISTS (SELECT 1 FROM dbo.TaskPolicyConfigurations p WHERE p.ShaleClientId=c.Id);

IF NOT EXISTS (SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.TaskPolicyConfigurations') AND predicate_type_desc=N'FILTER')
BEGIN
    ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.TaskPolicyConfigurations;
END;
IF NOT EXISTS (SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.TaskPolicyConfigurations') AND predicate_type_desc=N'BLOCK' AND operation_desc=N'AFTER INSERT')
BEGIN
    ALTER SECURITY POLICY sec.TenantFilter ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.TaskPolicyConfigurations AFTER INSERT;
END;
IF NOT EXISTS (SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.TaskPolicyConfigurations') AND predicate_type_desc=N'BLOCK' AND operation_desc=N'AFTER UPDATE')
BEGIN
    ALTER SECURITY POLICY sec.TenantFilter ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.TaskPolicyConfigurations AFTER UPDATE;
END;
COMMIT;
