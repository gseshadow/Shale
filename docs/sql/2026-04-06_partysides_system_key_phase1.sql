/*
  PartySides modularization pass 1 (conservative).

  Goals:
  - Ensure dbo.PartySides exists for tenant/global side display labels.
  - Ensure SystemKey exists and is normalized.
  - Backfill built-in side SystemKey values where clear.
  - Seed global/default built-ins only when ShaleClientId is nullable.
  - Normalize legacy CaseParties.Side values to built-in system keys.

  Safety:
  - Does NOT rewrite CaseParties foreign keys (none involved for side text).
  - Tenant rows remain active.
*/

SET NOCOUNT ON;

IF OBJECT_ID('dbo.PartySides', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.PartySides (
        Id bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
        ShaleClientId int NOT NULL,
        Name nvarchar(128) NOT NULL,
        SystemKey nvarchar(64) NULL,
        CreatedAt datetime2 NULL CONSTRAINT DF_PartySides_CreatedAt DEFAULT SYSUTCDATETIME(),
        UpdatedAt datetime2 NULL CONSTRAINT DF_PartySides_UpdatedAt DEFAULT SYSUTCDATETIME()
    );
END;

IF COL_LENGTH('dbo.PartySides', 'SystemKey') IS NULL
BEGIN
    ALTER TABLE dbo.PartySides
    ADD SystemKey nvarchar(64) NULL;
END;

-- Normalize existing key formatting.
UPDATE ps
SET SystemKey = LOWER(LTRIM(RTRIM(ps.SystemKey)))
FROM dbo.PartySides ps
WHERE ps.SystemKey IS NOT NULL
  AND ps.SystemKey <> LOWER(LTRIM(RTRIM(ps.SystemKey)));

-- Backfill built-in identities where Name is clear.
UPDATE ps
SET SystemKey = 'represented'
FROM dbo.PartySides ps
WHERE ps.SystemKey IS NULL
  AND LOWER(LTRIM(RTRIM(COALESCE(ps.Name, '')))) = 'represented';

UPDATE ps
SET SystemKey = 'opposing'
FROM dbo.PartySides ps
WHERE ps.SystemKey IS NULL
  AND LOWER(LTRIM(RTRIM(COALESCE(ps.Name, '')))) = 'opposing';

UPDATE ps
SET SystemKey = 'neutral'
FROM dbo.PartySides ps
WHERE ps.SystemKey IS NULL
  AND LOWER(LTRIM(RTRIM(COALESCE(ps.Name, '')))) = 'neutral';

-- Ensure tenant-7 built-ins exist for compatibility if table was just created/empty.
DECLARE @tenantId int = 7;

IF NOT EXISTS (SELECT 1 FROM dbo.PartySides ps WHERE ps.ShaleClientId = @tenantId AND ps.SystemKey = 'represented')
BEGIN
    INSERT INTO dbo.PartySides (ShaleClientId, Name, SystemKey)
    VALUES (@tenantId, N'Represented', N'represented');
END;

IF NOT EXISTS (SELECT 1 FROM dbo.PartySides ps WHERE ps.ShaleClientId = @tenantId AND ps.SystemKey = 'opposing')
BEGIN
    INSERT INTO dbo.PartySides (ShaleClientId, Name, SystemKey)
    VALUES (@tenantId, N'Opposing', N'opposing');
END;

IF NOT EXISTS (SELECT 1 FROM dbo.PartySides ps WHERE ps.ShaleClientId = @tenantId AND ps.SystemKey = 'neutral')
BEGIN
    INSERT INTO dbo.PartySides (ShaleClientId, Name, SystemKey)
    VALUES (@tenantId, N'Neutral', N'neutral');
END;

/*
  Seed global/default built-ins only when PartySides.ShaleClientId allows NULL.
  If NOT NULL, global seeding is intentionally deferred.
*/
IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.PartySides')
      AND name = 'ShaleClientId'
      AND is_nullable = 1
)
BEGIN
    DECLARE @seed TABLE (SystemKey nvarchar(64) NOT NULL, DefaultName nvarchar(128) NOT NULL);
    INSERT INTO @seed (SystemKey, DefaultName)
    VALUES
        ('represented', 'Represented'),
        ('opposing', 'Opposing'),
        ('neutral', 'Neutral');

    INSERT INTO dbo.PartySides (ShaleClientId, Name, SystemKey)
    SELECT
        NULL AS ShaleClientId,
        COALESCE(src.Name, s.DefaultName) AS Name,
        s.SystemKey
    FROM @seed s
    OUTER APPLY (
        SELECT TOP (1) ps.Name
        FROM dbo.PartySides ps
        WHERE ps.SystemKey = s.SystemKey
          AND ps.ShaleClientId = 7
        ORDER BY ps.Id
    ) src
    WHERE NOT EXISTS (
        SELECT 1
        FROM dbo.PartySides existing
        WHERE existing.ShaleClientId IS NULL
          AND existing.SystemKey = s.SystemKey
    );
END;

-- Normalize legacy CaseParties.Side text to built-in side system keys.
UPDATE cp
SET Side = LOWER(LTRIM(RTRIM(cp.Side)))
FROM dbo.CaseParties cp
WHERE cp.Side IS NOT NULL
  AND cp.Side <> LOWER(LTRIM(RTRIM(cp.Side)));

UPDATE cp
SET Side = 'represented'
FROM dbo.CaseParties cp
WHERE LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'represented';

UPDATE cp
SET Side = 'opposing'
FROM dbo.CaseParties cp
WHERE LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'opposing';

UPDATE cp
SET Side = 'neutral'
FROM dbo.CaseParties cp
WHERE LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'neutral';
