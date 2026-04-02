/*
  Phase 1 follow-up: stabilize case lifecycle semantics independent of status display name.

  Adds Statuses.LifecycleKey and backfills canonical keys for existing lifecycle statuses.
  UI/workflow logic should key off LifecycleKey instead of Name for accepted/denied/closed behavior.
*/

SET NOCOUNT ON;

IF COL_LENGTH('dbo.Statuses', 'LifecycleKey') IS NULL
BEGIN
    ALTER TABLE dbo.Statuses
    ADD LifecycleKey nvarchar(32) NULL;
END;

-- Backfill lifecycle semantics for existing rows.
-- Prefer explicit IDs from legacy seeded defaults, but also support name-based rows.
UPDATE s
SET LifecycleKey = 'accepted'
FROM dbo.Statuses s
WHERE s.LifecycleKey IS NULL
  AND (s.Id = 14 OR LOWER(LTRIM(RTRIM(COALESCE(s.Name, '')))) = 'accepted');

UPDATE s
SET LifecycleKey = 'denied'
FROM dbo.Statuses s
WHERE s.LifecycleKey IS NULL
  AND (s.Id = 11 OR LOWER(LTRIM(RTRIM(COALESCE(s.Name, '')))) = 'denied');

UPDATE s
SET LifecycleKey = 'closed'
FROM dbo.Statuses s
WHERE s.LifecycleKey IS NULL
  AND (s.Id = 12 OR LOWER(LTRIM(RTRIM(COALESCE(s.Name, '')))) = 'closed');

-- Optional integrity guardrail for known lifecycle keys.
IF OBJECT_ID('dbo.CK_Statuses_LifecycleKey', 'C') IS NULL
BEGIN
    ALTER TABLE dbo.Statuses
    ADD CONSTRAINT CK_Statuses_LifecycleKey
    CHECK (LifecycleKey IS NULL OR LifecycleKey IN ('accepted', 'denied', 'closed'));
END;
