/*
  Phase 2 (statuses): introduce SystemKey for modular tenant/global status resolution.

  Objectives:
  - Add dbo.Statuses.SystemKey (nullable) for stable built-in identity.
  - Keep LifecycleKey as lifecycle semantics source (accepted/denied/closed).
  - Backfill SystemKey for known built-in statuses on existing tenant rows.

  Notes:
  - Global/default rows are expected at ShaleClientId IS NULL.
  - Tenant rows can override global built-ins by matching SystemKey.
  - Tenant custom statuses may keep SystemKey = NULL.
*/

SET NOCOUNT ON;

IF COL_LENGTH('dbo.Statuses', 'SystemKey') IS NULL
BEGIN
    ALTER TABLE dbo.Statuses
    ADD SystemKey nvarchar(64) NULL;
END;

-- Optional integrity guardrail for known built-in status identities.
IF OBJECT_ID('dbo.CK_Statuses_SystemKey', 'C') IS NULL
BEGIN
    ALTER TABLE dbo.Statuses
    ADD CONSTRAINT CK_Statuses_SystemKey
    CHECK (
        SystemKey IS NULL
        OR SystemKey IN (
            'active',
            'potential',
            'prelitigation',
            'accepted',
            'denied',
            'closed'
        )
    );
END;

-- Backfill lifecycle-driven built-ins first when available.
UPDATE s
SET SystemKey = 'accepted'
FROM dbo.Statuses s
WHERE s.SystemKey IS NULL
  AND (
      LOWER(LTRIM(RTRIM(COALESCE(s.LifecycleKey, '')))) = 'accepted'
      OR LOWER(LTRIM(RTRIM(COALESCE(s.Name, '')))) = 'accepted'
  );

UPDATE s
SET SystemKey = 'denied'
FROM dbo.Statuses s
WHERE s.SystemKey IS NULL
  AND (
      LOWER(LTRIM(RTRIM(COALESCE(s.LifecycleKey, '')))) = 'denied'
      OR LOWER(LTRIM(RTRIM(COALESCE(s.Name, '')))) = 'denied'
  );

UPDATE s
SET SystemKey = 'closed'
FROM dbo.Statuses s
WHERE s.SystemKey IS NULL
  AND (
      LOWER(LTRIM(RTRIM(COALESCE(s.LifecycleKey, '')))) = 'closed'
      OR LOWER(LTRIM(RTRIM(COALESCE(s.Name, '')))) = 'closed'
  );

-- Backfill common non-lifecycle built-in identities by label.
UPDATE s
SET SystemKey = 'active'
FROM dbo.Statuses s
WHERE s.SystemKey IS NULL
  AND LOWER(LTRIM(RTRIM(COALESCE(s.Name, '')))) = 'active';

UPDATE s
SET SystemKey = 'potential'
FROM dbo.Statuses s
WHERE s.SystemKey IS NULL
  AND LOWER(LTRIM(RTRIM(COALESCE(s.Name, '')))) = 'potential';

UPDATE s
SET SystemKey = 'prelitigation'
FROM dbo.Statuses s
WHERE s.SystemKey IS NULL
  AND LOWER(LTRIM(RTRIM(COALESCE(s.Name, '')))) IN ('prelitigation', 'pre-litigation');

-- Optional operational query (manual follow-up):
-- identify duplicate scoped built-ins that should be merged/cleaned up before adding a unique filtered index.
--
-- SELECT ShaleClientId, SystemKey, COUNT(*) AS Cnt
-- FROM dbo.Statuses
-- WHERE SystemKey IS NOT NULL
-- GROUP BY ShaleClientId, SystemKey
-- HAVING COUNT(*) > 1;
