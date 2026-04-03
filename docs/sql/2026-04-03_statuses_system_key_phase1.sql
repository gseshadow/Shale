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

/*
  Seed global/default built-ins (ShaleClientId IS NULL) using existing tenant/source
  rows when possible, with safe literals as fallback.
*/

DECLARE @seed TABLE (
    SystemKey nvarchar(64) NOT NULL,
    DefaultName nvarchar(128) NOT NULL,
    DefaultIsClosed bit NOT NULL,
    DefaultSortOrder int NOT NULL,
    DefaultColor nvarchar(32) NULL,
    DefaultLifecycleKey nvarchar(32) NULL
);

INSERT INTO @seed (SystemKey, DefaultName, DefaultIsClosed, DefaultSortOrder, DefaultColor, DefaultLifecycleKey)
VALUES
    ('intake',   'Prelitigation', 0, 10, '#5B8DEF', NULL),
    ('accepted', 'Accepted',      0, 40, '#22A06B', 'accepted'),
    ('denied',   'Denied',        1, 50, '#C9372C', 'denied'),
    ('closed',   'Closed',        1, 60, '#6B778C', 'closed');

INSERT INTO dbo.Statuses (
    ShaleClientId,
    Name,
    IsClosed,
    SortOrder,
    Color,
    LifecycleKey,
    SystemKey
)
SELECT
    NULL AS ShaleClientId,
    COALESCE(src.Name, s.DefaultName) AS Name,
    COALESCE(src.IsClosed, s.DefaultIsClosed) AS IsClosed,
    COALESCE(src.SortOrder, s.DefaultSortOrder) AS SortOrder,
    COALESCE(src.Color, s.DefaultColor) AS Color,
    COALESCE(src.LifecycleKey, s.DefaultLifecycleKey) AS LifecycleKey,
    s.SystemKey
FROM @seed s
OUTER APPLY (
    SELECT TOP (1)
        st.Name,
        st.IsClosed,
        st.SortOrder,
        st.Color,
        st.LifecycleKey
    FROM dbo.Statuses st
    WHERE st.SystemKey = s.SystemKey
      AND st.ShaleClientId = 7
    ORDER BY st.Id
) src
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.Statuses existing
    WHERE existing.ShaleClientId IS NULL
      AND existing.SystemKey = s.SystemKey
);

-- Optional diagnostics and verification queries.
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
--
-- Effective status rows for tenant 7 (tenant override by SystemKey + tenant customs):
-- ;WITH statuses AS (
--   SELECT
--     s.Id,
--     s.ShaleClientId,
--     s.Name,
--     s.SystemKey,
--     s.LifecycleKey,
--     s.IsClosed,
--     s.SortOrder,
--     s.Color
--   FROM dbo.Statuses s
--   WHERE s.ShaleClientId = 7 OR s.ShaleClientId IS NULL
-- ),
-- ranked AS (
--   SELECT
--     st.*,
--     CASE WHEN st.ShaleClientId = 7 THEN 1 ELSE 0 END AS TenantRank,
--     ROW_NUMBER() OVER (
--       PARTITION BY st.SystemKey
--       ORDER BY CASE WHEN st.ShaleClientId = 7 THEN 1 ELSE 0 END DESC, st.Id DESC
--     ) AS rn
--   FROM statuses st
-- )
-- SELECT Id, ShaleClientId, Name, SystemKey, LifecycleKey, IsClosed, SortOrder, Color
-- FROM ranked
-- WHERE (SystemKey IS NOT NULL AND rn = 1) OR SystemKey IS NULL
-- ORDER BY SortOrder, Name, Id;
