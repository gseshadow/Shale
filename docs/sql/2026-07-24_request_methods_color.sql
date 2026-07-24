/*
  Adds Color support to dbo.RequestMethods while preserving the existing
  MaterialRequests.RequestMethod text contract and tenant/global overlay model.
  Rerunnable deployment migration.
*/
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.RequestMethods', N'U') IS NULL
    THROW 54840, 'Required table dbo.RequestMethods is missing.', 1;

IF COL_LENGTH(N'dbo.RequestMethods', N'Color') IS NULL
BEGIN
    ALTER TABLE dbo.RequestMethods ADD Color nvarchar(20) NULL;
END;

UPDATE dbo.RequestMethods SET Color = N'#2563EB', UpdatedAt = COALESCE(UpdatedAt, SYSUTCDATETIME()) WHERE ShaleClientId IS NULL AND SystemKey = N'email' AND Color IS NULL;
UPDATE dbo.RequestMethods SET Color = N'#16A34A', UpdatedAt = COALESCE(UpdatedAt, SYSUTCDATETIME()) WHERE ShaleClientId IS NULL AND SystemKey = N'phone' AND Color IS NULL;
UPDATE dbo.RequestMethods SET Color = N'#9333EA', UpdatedAt = COALESCE(UpdatedAt, SYSUTCDATETIME()) WHERE ShaleClientId IS NULL AND SystemKey = N'fax' AND Color IS NULL;
UPDATE dbo.RequestMethods SET Color = N'#D97706', UpdatedAt = COALESCE(UpdatedAt, SYSUTCDATETIME()) WHERE ShaleClientId IS NULL AND SystemKey = N'mail' AND Color IS NULL;
UPDATE dbo.RequestMethods SET Color = N'#0891B2', UpdatedAt = COALESCE(UpdatedAt, SYSUTCDATETIME()) WHERE ShaleClientId IS NULL AND SystemKey = N'portal' AND Color IS NULL;
UPDATE dbo.RequestMethods SET Color = N'#DB2777', UpdatedAt = COALESCE(UpdatedAt, SYSUTCDATETIME()) WHERE ShaleClientId IS NULL AND SystemKey = N'in_person' AND Color IS NULL;
UPDATE dbo.RequestMethods SET Color = N'#64748B', UpdatedAt = COALESCE(UpdatedAt, SYSUTCDATETIME()) WHERE ShaleClientId IS NULL AND SystemKey = N'other' AND Color IS NULL;

IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.RequestMethods') AND name = N'IX_RequestMethods_EffectiveList')
BEGIN
    DROP INDEX IX_RequestMethods_EffectiveList ON dbo.RequestMethods;
END;

CREATE INDEX IX_RequestMethods_EffectiveList
    ON dbo.RequestMethods (ShaleClientId, IsDeleted, IsActive, SortOrder, Name)
    INCLUDE (SystemKey, Color);

COMMIT TRANSACTION;
GO

SELECT c.name, t.name AS type_name, c.max_length, c.is_nullable
FROM sys.columns c
JOIN sys.types t ON t.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID(N'dbo.RequestMethods') AND c.name = N'Color';
