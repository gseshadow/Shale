SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH(N'dbo.Cases', N'IntakeTakenByUserId') IS NULL
    ALTER TABLE dbo.Cases ADD IntakeTakenByUserId int NULL;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.Users') AND name = N'UX_Users_ShaleClientId_Id'
)
    CREATE UNIQUE INDEX UX_Users_ShaleClientId_Id ON dbo.Users (ShaleClientId, Id);

IF OBJECT_ID(N'dbo.FK_Cases_IntakeTakenByUser_Tenant', N'F') IS NULL
    ALTER TABLE dbo.Cases ADD CONSTRAINT FK_Cases_IntakeTakenByUser_Tenant
        FOREIGN KEY (ShaleClientId, IntakeTakenByUserId)
        REFERENCES dbo.Users (ShaleClientId, Id);

COMMIT TRANSACTION;
