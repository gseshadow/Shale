/*
  Phase 1 database prerequisite for Request Method colors.

  Adds nullable nvarchar(20) because Shale's authoritative color contract
  (architecture/customizable-lookup-types.md and dbo.MaterialTypes) defines
  Color as an optional nvarchar(20) presentation value. This accommodates the
  application's validated CSS/hex color tokens without adopting the unrelated
  varchar(32) legacy shape of dbo.RequestStatuses.

  This migration deliberately leaves application contracts and the legacy
  dbo.MaterialRequests.RequestMethod text column unchanged.
*/
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.RequestMethods', N'U') IS NULL
    THROW 54840, 'Required table dbo.RequestMethods is missing.', 1;

IF COL_LENGTH(N'dbo.RequestMethods', N'Color') IS NULL
BEGIN
    ALTER TABLE dbo.RequestMethods ADD Color nvarchar(20) NULL;
END;

-- Seed only global built-ins with stable semantic keys. Color IS NULL makes
-- reruns fill missing defaults without replacing deployed customizations.
UPDATE dbo.RequestMethods SET Color = N'#2563EB' WHERE ShaleClientId IS NULL AND SystemKey = N'email'     AND Color IS NULL;
UPDATE dbo.RequestMethods SET Color = N'#16A34A' WHERE ShaleClientId IS NULL AND SystemKey = N'phone'     AND Color IS NULL;
UPDATE dbo.RequestMethods SET Color = N'#9333EA' WHERE ShaleClientId IS NULL AND SystemKey = N'fax'       AND Color IS NULL;
UPDATE dbo.RequestMethods SET Color = N'#D97706' WHERE ShaleClientId IS NULL AND SystemKey = N'mail'      AND Color IS NULL;
UPDATE dbo.RequestMethods SET Color = N'#0891B2' WHERE ShaleClientId IS NULL AND SystemKey = N'portal'    AND Color IS NULL;
UPDATE dbo.RequestMethods SET Color = N'#DB2777' WHERE ShaleClientId IS NULL AND SystemKey = N'in_person' AND Color IS NULL;
UPDATE dbo.RequestMethods SET Color = N'#64748B' WHERE ShaleClientId IS NULL AND SystemKey = N'other'     AND Color IS NULL;

COMMIT TRANSACTION;
GO

-- Deployment verification result: nvarchar reports max_length 40 bytes.
SELECT c.name, t.name AS type_name, c.max_length, c.is_nullable
FROM sys.columns c
JOIN sys.types t ON t.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID(N'dbo.RequestMethods') AND c.name = N'Color';
