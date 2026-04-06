/*
  PartyRoles modularization pass 1 (aligned with Statuses pattern).

  This pass is intentionally conservative:
  - Ensure dbo.PartyRoles.SystemKey exists.
  - Normalize SystemKey values to trimmed lowercase.
  - Backfill built-in identities on existing rows where intent is clear.
  - Optionally seed global/default rows (ShaleClientId IS NULL) only when
    PartyRoles.ShaleClientId is nullable.
  - Keep existing tenant rows active so CaseParties FK history remains unchanged.
*/

SET NOCOUNT ON;

IF OBJECT_ID('dbo.PartyRoles', 'U') IS NULL
BEGIN
    THROW 51100, 'dbo.PartyRoles does not exist.', 1;
END;

IF COL_LENGTH('dbo.PartyRoles', 'SystemKey') IS NULL
BEGIN
    ALTER TABLE dbo.PartyRoles
    ADD SystemKey nvarchar(64) NULL;
END;

-- Normalize existing keys if present.
UPDATE pr
SET SystemKey = LOWER(LTRIM(RTRIM(pr.SystemKey)))
FROM dbo.PartyRoles pr
WHERE pr.SystemKey IS NOT NULL
  AND pr.SystemKey <> LOWER(LTRIM(RTRIM(pr.SystemKey)));

-- Backfill stable built-in identity where existing Name indicates intent.
UPDATE pr
SET SystemKey = 'caller'
FROM dbo.PartyRoles pr
WHERE pr.SystemKey IS NULL
  AND LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = 'caller';

UPDATE pr
SET SystemKey = 'party'
FROM dbo.PartyRoles pr
WHERE pr.SystemKey IS NULL
  AND LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = 'party';

UPDATE pr
SET SystemKey = 'counsel'
FROM dbo.PartyRoles pr
WHERE pr.SystemKey IS NULL
  AND LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = 'counsel';

/*
  Seed global/default built-ins only when ShaleClientId allows NULL.
  If not nullable yet, this script defers seeding safely.
*/
IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.PartyRoles')
      AND name = 'ShaleClientId'
      AND is_nullable = 1
)
BEGIN
    DECLARE @seed TABLE (
        SystemKey nvarchar(64) NOT NULL,
        DefaultName nvarchar(128) NOT NULL
    );

    INSERT INTO @seed (SystemKey, DefaultName)
    VALUES
        ('caller',  'Caller'),
        ('party',   'Party'),
        ('counsel', 'Counsel');

    INSERT INTO dbo.PartyRoles (ShaleClientId, Name, SystemKey)
    SELECT
        NULL AS ShaleClientId,
        COALESCE(src.Name, s.DefaultName) AS Name,
        s.SystemKey
    FROM @seed s
    OUTER APPLY (
        SELECT TOP (1) pr.Name
        FROM dbo.PartyRoles pr
        WHERE pr.SystemKey = s.SystemKey
          AND pr.ShaleClientId = 7
        ORDER BY pr.Id
    ) src
    WHERE NOT EXISTS (
        SELECT 1
        FROM dbo.PartyRoles existing
        WHERE existing.ShaleClientId IS NULL
          AND existing.SystemKey = s.SystemKey
    );
END;

-- Suggested post-migration checks.
-- SELECT ShaleClientId, SystemKey, COUNT(*) AS Cnt
-- FROM dbo.PartyRoles
-- WHERE SystemKey IS NOT NULL
-- GROUP BY ShaleClientId, SystemKey
-- HAVING COUNT(*) > 1;
--
-- SELECT Id, ShaleClientId, Name, SystemKey
-- FROM dbo.PartyRoles
-- ORDER BY CASE WHEN ShaleClientId IS NULL THEN 0 ELSE 1 END, ShaleClientId, Name, Id;
