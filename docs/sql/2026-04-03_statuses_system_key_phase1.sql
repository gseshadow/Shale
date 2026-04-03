/*
  Status modularization prep pass (tenant rows remain active).

  This script is intentionally conservative:
  - Ensure dbo.Statuses.SystemKey exists.
  - Normalize/backfill known built-in keys on existing tenant rows.
  - Do NOT create global rows (ShaleClientId IS NULL) yet.
  - Do NOT migrate case-history StatusId references.
*/

SET NOCOUNT ON;

IF COL_LENGTH('dbo.Statuses', 'SystemKey') IS NULL
BEGIN
    ALTER TABLE dbo.Statuses
    ADD SystemKey nvarchar(64) NULL;
END;

-- Normalize existing key formatting where present.
UPDATE s
SET SystemKey = LOWER(LTRIM(RTRIM(s.SystemKey)))
FROM dbo.Statuses s
WHERE s.SystemKey IS NOT NULL
  AND s.SystemKey <> LOWER(LTRIM(RTRIM(s.SystemKey)));

-- Backfill lifecycle-driven built-ins first.
UPDATE s
SET SystemKey = 'accepted'
FROM dbo.Statuses s
WHERE s.SystemKey IS NULL
  AND LOWER(LTRIM(RTRIM(COALESCE(s.LifecycleKey, '')))) = 'accepted';

UPDATE s
SET SystemKey = 'denied'
FROM dbo.Statuses s
WHERE s.SystemKey IS NULL
  AND LOWER(LTRIM(RTRIM(COALESCE(s.LifecycleKey, '')))) = 'denied';

UPDATE s
SET SystemKey = 'closed'
FROM dbo.Statuses s
WHERE s.SystemKey IS NULL
  AND LOWER(LTRIM(RTRIM(COALESCE(s.LifecycleKey, '')))) = 'closed';

-- Backfill intake/open pipeline identity for legacy prelitigation labels.
UPDATE s
SET SystemKey = 'intake'
FROM dbo.Statuses s
WHERE s.SystemKey IS NULL
  AND LOWER(LTRIM(RTRIM(COALESCE(s.Name, '')))) IN ('prelitigation', 'pre-litigation', 'intake');

-- Optional diagnostics for manual cleanup before a later true global-layer pass.
--
-- Duplicate built-in identities inside a scope (tenant/global):
-- SELECT ShaleClientId, SystemKey, COUNT(*) AS Cnt
-- FROM dbo.Statuses
-- WHERE SystemKey IS NOT NULL
-- GROUP BY ShaleClientId, SystemKey
-- HAVING COUNT(*) > 1;
--
-- Global/default status rows currently configured:
-- SELECT Id, Name, SystemKey, LifecycleKey, SortOrder
-- FROM dbo.Statuses
-- WHERE ShaleClientId IS NULL
-- ORDER BY SortOrder, Name, Id;
